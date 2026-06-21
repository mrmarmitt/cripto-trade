# T31 — Limpeza de Reserva Órfã em Runtime (query-before-expire) + Fundação de Cancelamento

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** Nenhuma (estabelece o caminho outbound de cancelamento reusado por T32)  
**Status:** Pendente

---

## Descrição

Esta task resolve **um único caso de capital genuinamente preso** — a reserva órfã (zombie) — e entrega o **mecanismo de cancelamento** que a T32 vai reusar. Ela é deliberadamente **neutra em relação à estratégia**: nada aqui cancela uma ordem que a estratégia mandou abrir.

> **Escopo refinado em duas rodadas.** (1) Removido o scheduler global que cancelava ordens limite vivas por TTL externo — anularia estratégias de horizonte longo (ver "Premissa: preso vs comprometido"). (2) A limpeza de zombie em runtime **não** copia a expiração local cega do boot — ela faz **query-before-expire** (consulta a exchange por `clientOrderId` antes de expirar), porque em runtime não há nenhuma outra rede de segurança para o zombie.

---

## Premissa: "capital preso" vs "capital comprometido"

| Caso | Situação | Capital | É problema? |
|---|---|---|---|
| **A — Reserva órfã (zombie)** | `PENDING` que reservou capital mas **nunca foi confirmado** pela exchange (sem `exchangeOrderId`) | Comprometido com **nada** (se a ordem realmente não existe) | **Sim** — se confirmado órfão |
| **B — Ordem viva** | Ordem aberta na exchange (`SUBMITTED`/`PARTIAL`) esperando o preço | **Corretamente comprometido** com a ordem | **Não** — é a estratégia operando |

Para o Caso B, o capital reservado **não está preso, está comprometido**: a estratégia pode querer manter uma compra limite aberta por semanas, e esse capital precisa ficar reservado. Cancelar ordem viva é decisão de alpha → **T32**, nunca um TTL global aqui.

Esta task atua só no Caso A — e, crucialmente, **confirma** que é o Caso A antes de liberar capital.

---

## O furo que esta versão corrige

Por construção, `Transaction.submit(exchangeOrderId)` grava `exchangeOrderId` **e** vira `SUBMITTED` no mesmo passo. Logo, **todo `PENDING` tem `exchangeOrderId == null`** — "zombie" ≡ qualquer `PENDING`.

Um zombie pode surgir por três caminhos (entre o commit do persist e a chegada do ACK):

1. **Nunca despachado** — processo caiu entre persistir (`BuySignalHandler`, persist-first) e o `orderDispatch.dispatch(...)`.
2. **Dispatch falhou** — exceção técnica no envio; contrato fire-and-forget mantém `PENDING`.
3. **Despachado, mas ACK perdido** — ordem foi enviada e **pode estar viva** na exchange; o `executionReport` (NEW) se perdeu (blip no USER_DATA, `listenKey`, reconexão). O Binance USER_DATA **não reenvia** eventos perdidos.

**Como o boot trata zombie hoje** (`RunnerBootRecoveryUseCase.step3ExpireZombies`): expira **localmente** após TTL, **sem consultar a exchange**. O boot se dá esse luxo porque (a) há carência de TTL para ACK atrasado, (b) é varredura única pós-restart, (c) reconciliação/DLQ é backstop se errar.

**Por que runtime não pode copiar isso:** o caso 3 ocorre em runtime **sem restart** (basta um blip no USER_DATA), e **nada** consulta um zombie em runtime — o recovery watchdog (`RecoverStaleTransactionsUseCase`) só cobre `SUBMITTED`/`PARTIAL`. Expirar local + liberar capital de uma ordem que está **viva** seria divergência sem rede que a pegasse antes do próximo boot.

---

## Solução proposta

### Parte 1 — Limpeza de zombie em runtime com query-before-expire

Reusar o `RecoverTransactionStatusUseCase`, que **já aceita `PENDING`** como status elegível e já faz exatamente o "consulta-e-decide":

- consulta por `clientOrderId`;
- **exchange conhece** → concilia (vira `SUBMITTED`/`FILLED`) — salva o Caso 3, capital **não** é liberado indevidamente;
- **exchange não conhece** → `APPLY_TERMINAL_FALLBACK`, que para `PENDING` aplica **`EXPIRED`** — mesma semântica do zombie expiry do boot, agora **verificada**.

Quem não liga `PENDING` a nenhum gatilho em runtime é o batch (`RecoverStaleTransactionsUseCase`, eligible só `SUBMITTED`/`PARTIAL`). Esta task fecha isso:

- Criar `RuntimeZombieCleanupWatchdog` no Spring, espelhando `RunnerTransactionRecoveryWatchdog` (`@Scheduled`, `fixedDelayString`).
- Selecionar `PENDING` (zombies) mais velhos que um **cutoff generoso** (`findByStatusesUpdatedBefore` já existe), respeitando `maxPerRun`.
- Para cada um, chamar `RecoverTransactionStatusUseCase` com **`MissingOrderPolicy.APPLY_TERMINAL_FALLBACK`** — **não** o `forRuntimeWatchdog` padrão, que usa `REGISTER_DLQ`. Um zombie nunca-confirmado que a exchange desconhece é órfão limpo (→ `EXPIRED` + liberação de capital), **não** um conflito de reconciliação (→ DLQ geraria ruído/falso conflito).
  - Adicionar uma factory de request dedicada, ex.: `RecoverTransactionStatusRequest.forZombieCleanup(txId)` (= `APPLY_TERMINAL_FALLBACK`), ao lado de `forRuntimeWatchdog` (= `REGISTER_DLQ`).
- A conciliação resultante (reconcile ou EXPIRED) passa pelo `ConciliationOrderUpdateExecutor` idempotente, que libera a reserva via `MarginReleaseBuilder` pelo caminho canônico.

**Cutoff generoso:** o cutoff deve dar tempo de sobra para um ACK atrasado ou reconexão do USER_DATA chegar antes de declarar zombie. Isso minimiza queries desperdiçadas nos casos 1/2 (nunca enviados) e evita corrida com fills tardios.

> Por que isso é seguro e universal: nenhuma estratégia quer um zombie. E, ao consultar antes de expirar, nunca liberamos capital de uma ordem que esteja viva (Caso 3).

### Parte 2 — Verbo de cancelamento no `OrderDispatchPort` (fundação da T32)

Adicionar a capacidade de cancelamento como contrato de domínio, espelhando o fire-and-forget de `dispatch`:

```java
public interface OrderDispatchPort {
    void dispatch(OrderDispatchCommand command);
    void cancel(OrderCancelCommand command);   // novo
}
```

- `OrderCancelCommand`: record no core (`dto/runner/`) com `clientOrderId`, `runnerId`, `symbol`, `exchangeId`. Agnóstico — nenhum DTO de exchange entra no core.
- `OrderDispatchAdapter` implementa `cancel(...)` traduzindo para `SendCancelOrderRequest` (mesmo padrão de `dispatch` → `OrderDispatchCommand`). O transporte já sabe cancelar (`CancelOrderProcessor`, `ExchangeOrderExecutionPort.cancelOrder`).
- **Nesta task não há gatilho automático ligado a esse verbo para ordens vivas.** É só o mecanismo; primeiro consumidor real é a T32. (A limpeza de zombie da Parte 1 **não** usa `cancel` — usa query+conciliação.)

---

## Configuração (application.yml)

```yaml
runner:
  zombie-cleanup:
    enabled: true
    interval-ms: 60000        # frequência do watchdog
    cutoff-ms: 1800000        # 30 min — generoso: cobre ACK atrasado / reconexão antes de consultar
    max-per-run: 50
```

> Este cutoff governa **apenas a limpeza de zombie** (Caso A, query-before-expire). Não existe configuração global que cancele ordem viva por idade — intencional.

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/outbound/exchange/OrderDispatchPort.java` | Adicionar `cancel(OrderCancelCommand)` |
| `core/.../dto/runner/OrderCancelCommand.java` | Novo — comando de cancelamento agnóstico |
| `core/.../dto/runner/request/RecoverTransactionStatusRequest.java` | Factory `forZombieCleanup(txId)` (`APPLY_TERMINAL_FALLBACK`) |
| `core/.../usecase/runner/RuntimeZombieCleanupUseCase.java` (ou reuso/extensão do batch) | Seleciona `PENDING` velhos e roteia por `RecoverTransactionStatusUseCase` |
| `spring-application/.../infrastructure/exchange/OrderDispatchAdapter.java` | Implementar `cancel(...)` → `SendCancelOrderRequest` |
| `spring-application/.../bootstrap/RuntimeZombieCleanupWatchdog.java` | Novo — scheduler |
| `spring-application/.../bootstrap/RuntimeZombieCleanupProperties.java` | Novo — propriedades |
| `spring-application/src/main/resources/application.yml` | Configuração do cleanup |
| `spring-application/.../config/core/RunnerConfig.java` | Wiring |

---

## Fora de escopo (explicitamente)

- **Cancelar ordem viva por idade/TTL global.** Removido — anula intenção de estratégia. É da T32.
- **Cancel-replace / reprice automático.** Só faria sentido como opt-in por runner, em task futura.
- **Expiração local cega de zombie em runtime.** Substituída por query-before-expire — runtime não tem o backstop do boot.

---

## Riscos / pontos de atenção

- **Não competir com o recovery watchdog:** este cobre `PENDING`; o recovery cobre `SUBMITTED`/`PARTIAL`. Cutoffs/cadências separados, sem sobreposição de estado.
- **Política de não-encontrado:** usar `APPLY_TERMINAL_FALLBACK` (→ `EXPIRED`), nunca `REGISTER_DLQ`, para zombie — caso contrário, todo zombie nunca-enviado (casos 1/2, o caso comum) viraria falso conflito na DLQ.
- **Corrida query × fill tardio:** mesmo com cutoff generoso, um fill pode chegar durante o ciclo. A conciliação idempotente (`ConciliationOrderUpdateIdempotencyTest`) é a fonte da verdade; não tomar decisão fora dela.
- **Custo de query:** uma chamada por zombie velho, incluindo casos 1/2 (desperdiçada-mas-inofensiva). Mitigado por cutoff generoso + `maxPerRun`. Combina bem com o circuit breaker da T24 quando esta existir.
- **Liberação de capital:** sempre via `ConciliationOrderUpdateExecutor`, nunca liberação direta.
- **Boot permanece como está:** o boot (`step3ExpireZombies`) continua com expiração local — tem carência + varredura única. Esta task **não** altera o boot; só cobre o runtime, que é mais exposto.

---

## Critérios de aceitação

1. Um `PENDING` (zombie) mais velho que `cutoff-ms` é **consultado na exchange por `clientOrderId`** antes de qualquer expiração, em runtime.
2. Se a exchange conhece a ordem (estava viva — Caso 3), ela é **reconciliada** (ex.: `SUBMITTED`), e o capital **não** é liberado.
3. Se a exchange não conhece a ordem (casos 1/2), ela vira **`EXPIRED`** e o capital é liberado via conciliação — **sem** criar entrada de DLQ.
4. Nenhuma ordem com `exchangeOrderId` (viva) é tocada por esta task; não existe TTL global cancelando ordem viva.
5. `OrderDispatchPort.cancel(OrderCancelCommand)` existe, é implementado pelo adapter e traduz para `SendCancelOrderRequest` sem vazar tipo de provider para o core.
6. `runner.zombie-cleanup.enabled: false` desabilita o watchdog sem afetar boot recovery nem o recovery watchdog.
7. Boot, recovery e reconciliação existentes continuam sem regressão.
