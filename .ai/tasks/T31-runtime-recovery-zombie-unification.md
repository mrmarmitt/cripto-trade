# T31 — Unificação do Recovery de Runtime: cobertura de zombies (`PENDING`)

**Complexidade:** Baixa  
**Responsável:** Claude  
**Dependências:** Nenhuma  
**Status:** Pendente

---

## Descrição

Esta task fecha **um único furo**: em runtime, uma reserva órfã (`PENDING` sem `exchangeOrderId` — "zombie") não tem nenhuma rede de segurança. Ela resolve isso **sem criar um fluxo novo** — estende o recovery de runtime que já existe para também cobrir `PENDING`, com **query-before-expire**.

> **Reescopo (dedup).** A versão anterior propunha um `RuntimeZombieCleanupUseCase` + watchdog + properties dedicados. Isso era **duplicação**: o motor por-transação (`RecoverTransactionStatusUseCase`) já faz exatamente "consulta-e-concilia" e **já aceita `PENDING`** como status elegível. O único lugar que não enxerga zombie é o **seletor de lote** (`RecoverStaleTransactionsUseCase`), que filtra só `SUBMITTED`/`PARTIAL`. A correção é estender esse seletor, não criar um motor paralelo.
>
> O verbo de cancelamento outbound (`OrderDispatchPort.cancel`), que era a "Parte 2" desta task, foi **extraído para a T33** — é mecanismo de cancelamento, não recovery.

---

## A estrutura que já existe (duas camadas)

Não há dois motores de recovery; há um, parametrizado:

**Motor (por transação) — `RecoverTransactionStatusUseCase`**
- Elegíveis: **`[PENDING, SUBMITTED, PARTIAL]`** (já inclui `PENDING`).
- Fluxo: carrega tx → checa elegibilidade → carrega runner/adapter → **`queryOrderByClientOrderId`** → se achou: valida + normaliza + concilia (`ConciliationOrderUpdateExecutor`); se **não** achou: decide pela `MissingOrderPolicy`.
- `MissingOrderPolicy.APPLY_TERMINAL_FALLBACK` → `PARTIAL`→`CANCELED`, senão→`EXPIRED`.
- `MissingOrderPolicy.REGISTER_DLQ` → cria `DeadLetterEntry` (`RECONCILIATION_CONFLICT`).

**Seletor de lote — `RecoverStaleTransactionsUseCase`**
- `findByStatusesUpdatedBefore([SUBMITTED, PARTIAL], updatedBefore, maxPerRun)` → loop chamando o motor com `forRuntimeWatchdog(id)` (= `REGISTER_DLQ`) → agrega contadores.
- É disparado pelo `RunnerTransactionRecoveryWatchdog` (`@Scheduled`).

O "recovery" é só **seletor + motor**. O zombie de runtime é o **mesmo** motor sobre `PENDING`.

---

## Premissa: "capital preso" vs "capital comprometido" (mantida)

| Caso | Situação | Capital | É problema? |
|---|---|---|---|
| **A — Reserva órfã (zombie)** | `PENDING` que reservou capital mas **nunca foi confirmado** (sem `exchangeOrderId`) | Comprometido com **nada** (se a ordem realmente não existe) | **Sim** — se confirmado órfão |
| **B — Ordem viva** | `SUBMITTED`/`PARTIAL` aberta na exchange esperando preço | **Corretamente comprometido** | **Não** — é a estratégia operando |

Cancelar ordem viva (Caso B) é decisão de alpha → **T32**. Esta task atua **só** no Caso A, e **confirma** que é o Caso A (query) antes de liberar capital.

---

## A única diferença real entre "recovery" e "zombie": dois parâmetros

| | Recovery (stale) | Zombie cleanup |
|---|---|---|
| **Filtro de status** | `[SUBMITTED, PARTIAL]` | `[PENDING]` |
| **MissingOrderPolicy** | `REGISTER_DLQ` | `APPLY_TERMINAL_FALLBACK` |

E a segunda diferença é **derivável** da primeira:

- `SUBMITTED`/`PARTIAL` not-found → a ordem **teve confirmação** (tem `exchangeOrderId`); sumir é **divergência real** → `REGISTER_DLQ`.
- `PENDING` not-found → a ordem **nunca foi confirmada**; é **órfão limpo** (nunca saiu) → `APPLY_TERMINAL_FALLBACK` (`EXPIRED`). DLQ aqui seria ruído/falso conflito.

A `MissingOrderPolicy` já é a costura que prova que o design antecipou isso: `forBoot(txId)` **já** = `APPLY_TERMINAL_FALLBACK` (exatamente o que o zombie precisa em runtime).

---

## Solução proposta

Estender o recovery de runtime para cobrir `PENDING`. Duas formas, em ordem de preferência:

### Opção 1 — Política derivada do status (preferida)

No `RecoverStaleTransactionsUseCase`:

- Selecionar `[PENDING, SUBMITTED, PARTIAL]` (um único `findByStatusesUpdatedBefore`).
- No loop, escolher a request por `candidate.getStatus()`:
  - `PENDING` → `APPLY_TERMINAL_FALLBACK` (query-before-expire);
  - `SUBMITTED`/`PARTIAL` → `REGISTER_DLQ` (comportamento atual).

Um seletor, um loop, **zero motor novo, zero watchdog novo**. As "duas flows" viram uma de verdade.

### Opção 2 — Request parametrizada (só se quiser cutoff/cadência independentes)

`RecoverStaleTransactionsRequest` passa a carregar o conjunto de status + política; **dois gatilhos** finos no watchdog (um para `[SUBMITTED,PARTIAL]`/`REGISTER_DLQ`, outro para `[PENDING]`/`APPLY_TERMINAL_FALLBACK`) chamam o **mesmo** use case com requests diferentes. Um motor, um lote, duas configs.

> **Cutoff:** o zombie merece um cutoff **generoso** (carência para ACK atrasado / reconexão do USER_DATA antes de declarar órfão). Se um cutoff único generoso servir aos dois (provável), fica a Opção 1. Se quiser afinar separado, Opção 2.

---

## Observabilidade e diagnóstico (não silenciar)

Um zombie é **impressão digital de uma falha anterior** (crash entre persist e dispatch; exceção no envio; ACK perdido). Expirar é a **resolução** contábil correta, mas não pode ser **silenciosa** — senão se perde o health-signal do caminho persist→dispatch.

- Contador por desfecho: `reconciled_live` (Caso 3 — a query achou a ordem **viva**) vs `expired_orphan` (não achou → `EXPIRED`).
- `reconciled_live` é **quase-vazamento de capital + USER_DATA não confiável** → alerta ativo (gancho com **T21**), severidade maior que um órfão de rotina.
- Taxa de zombie sustentada alimenta o **Safe Mode automático (T25)**: se o dispatch quebra repetidamente, talvez não se deva continuar abrindo ordens.
- **DLQ continua sendo o canal de *conflito*** (`SUBMITTED`/`PARTIAL` not-found), nunca do zombie órfão (`PENDING` not-found) — evita falso conflito.

---

## Configuração (application.yml)

Reusa o namespace de recovery já existente. Na Opção 2, um sub-bloco para o cutoff do zombie:

```yaml
runner:
  recovery:
    transaction:
      enabled: true
      interval-ms: 60000
      stale-threshold-ms: 120000     # cutoff para SUBMITTED/PARTIAL (já existe)
      # Opção 2 — cutoff dedicado do zombie (carência de ACK):
      zombie-cutoff-ms: 1800000      # 30 min, generoso
      max-per-run: 50
```

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../usecase/runner/RecoverStaleTransactionsUseCase.java` | Incluir `PENDING` na seleção e derivar `MissingOrderPolicy` por status (Opção 1) **ou** receber status+política via request (Opção 2) |
| `core/.../dto/runner/request/RecoverStaleTransactionsRequest.java` | (Opção 2) campos status-set + política |
| `core/.../dto/runner/request/RecoverTransactionStatusRequest.java` | Renomear/alias para deixar a intenção clara (`forBoot` já encapsula `APPLY_TERMINAL_FALLBACK`; expor algo como `withTerminalFallback`/`forOrphanCleanup`) |
| `spring-application/.../bootstrap/RunnerTransactionRecoveryWatchdog.java` | (Opção 2) segundo tick/gatilho com cutoff do zombie; (Opção 1) inalterado |
| `spring-application/.../bootstrap/RunnerTransactionRecoveryProperties.java` | (Opção 2) `zombieCutoffMs` |
| Métricas/log | Contador `reconciled_live` / `expired_orphan`; WARN estruturado |
| Testes em `core/.../runner/` | `PENDING` reconciliado (vivo) não libera capital; `PENDING` not-found → `EXPIRED` sem DLQ; `SUBMITTED` not-found → DLQ (sem regressão) |

> **Não** criar `RuntimeZombieCleanupUseCase`, `RuntimeZombieCleanupWatchdog` nem `RuntimeZombieCleanupProperties` — era a duplicação.

---

## Fora de escopo (explicitamente)

- **Cancelar ordem viva por idade/TTL global.** Anula intenção de estratégia. É da **T32** (opt-in por runner, default off).
- **Verbo de cancelamento `OrderDispatchPort.cancel`.** Extraído para a **T33**.
- **Expiração local cega de zombie em runtime.** Substituída por query-before-expire (runtime não tem o backstop do boot).
- **Boot (`step3ExpireZombies`).** Permanece como está — expira local porque tem carência de TTL + varredura única. Esta task **não** altera o boot; só cobre o runtime, mais exposto.

---

## Riscos / pontos de atenção

- **Não competir por estado:** após a extensão, o lote cobre `[PENDING, SUBMITTED, PARTIAL]` num único seletor — sem dois processos disputando a mesma transação.
- **Política por status é invariante de segurança:** `PENDING`→`APPLY_TERMINAL_FALLBACK`, `SUBMITTED`/`PARTIAL`→`REGISTER_DLQ`. Inverter geraria falso conflito (zombie nunca-enviado na DLQ) ou perda de sinal (ordem confirmada sumindo sem DLQ).
- **Corrida query × fill tardio:** a conciliação idempotente (`ConciliationOrderUpdateExecutor`) é a fonte da verdade; não decidir fora dela.
- **Custo de query:** uma chamada por candidato velho, incl. casos nunca-enviados (desperdiçada-mas-inofensiva). Mitigado por cutoff generoso + `maxPerRun`. Combina com o circuit breaker da **T24**.
- **Liberação de capital:** sempre via `ConciliationOrderUpdateExecutor`, nunca direta.

---

## Critérios de aceitação

1. Um `PENDING` (zombie) mais velho que o cutoff é **consultado por `clientOrderId`** antes de qualquer expiração, em runtime, pelo **mesmo** caminho de recovery (sem use case paralelo).
2. Se a exchange conhece a ordem (Caso 3 — viva), ela é **reconciliada** e o capital **não** é liberado.
3. Se a exchange não conhece (casos 1/2), vira **`EXPIRED`** via conciliação — **sem** entrada de DLQ.
4. `SUBMITTED`/`PARTIAL` not-found continua indo para **DLQ** (sem regressão do recovery atual).
5. Não existe `RuntimeZombieCleanupUseCase`/watchdog/properties dedicados — a cobertura de zombie é uma extensão do recovery existente.
6. Métrica distingue `reconciled_live` de `expired_orphan`; o primeiro emite alerta ativo.
7. Boot, recovery (`SUBMITTED`/`PARTIAL`) e reconciliação existentes seguem sem regressão.
