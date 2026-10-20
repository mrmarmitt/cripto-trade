# T55 — Operabilidade por IA no Resto do Sistema

**Complexidade:** Alta
**Responsável:** Claude
**Dependências:** T54 (primeira API no padrão), T27 (envelope `ApiError`), T51 (estado operacional como
métrica). Base do monitoramento periódico por IA da T53.
**Status:** Rascunho — **decisões de negócio pendentes** (ver seção própria).

---

## Descrição

O ctrade será operado por um agente de IA (`.ai/product-direction.md`). A T54 entregou o catálogo de
estratégias no padrão definido lá: estado autoexplicativo, `code` estável nas recusas, idempotência,
`reason` com histórico, OpenAPI e runbook executável. O resto da API veio antes desse padrão. Esta task
leva o sistema inteiro ao mesmo padrão e cria o que o agente mais precisa para operar: **uma consulta
única que diz se o sistema está consistente e, se não estiver, o quê, onde e o que fazer**.

## Achado de segurança (prioritário)

Só `/api/admin/**` e `/api/exchanges/**` exigem `X-Admin-Key` (`AdminWebMvcConfig`). Ficaram abertos
endpoints que **mudam estado**:

| Endpoint | Efeito |
|---|---|
| `POST /api/websocket/exchange/orders/create` | **Envia ordem direto à exchange**, sem transação local, sem reserva de capital, sem conciliação nem proteções. Gera `binanceOnly` na reconciliação e pode gastar saldo real. |
| `POST /api/websocket/exchange/orders/cancel` | Cancela ordem direto na exchange, fora do core. |
| `POST /api/websocket/connect`, `/disconnect`, `.../market-data/*`, `.../order-notifications/*` | Liga/desliga conexões e assinaturas que o core usa. |
| `DELETE /api/listeners/clear` | Remove todos os listeners: o core para de receber market data e atualizações de ordem. |
| `POST /api/dead-letters/{id}/resolve` e `/reprocess` | Resolve ou reaplica efeito financeiro de entrada da DLQ. |
| `POST /api/portfolios`, `POST /api/portfolios/{id}/runners` | Cria portfolio e runner. |

Os de websocket/listeners são de uma fase anterior ao core atual; as ordens diretas contrariam a regra
de que toda ordem nasce de uma transação do core (`PHASE0_INVARIANTS`).

## Escopo proposto

### Parte A — Superfície da API

1. **Autenticação em tudo que muda estado**: toda rota de escrita exige `X-Admin-Key`.
2. **Remover os caminhos que contornam o core**: ordens diretas e controle manual de websocket e
   listeners (decisão 1).
3. **Inventário de leitura**: leituras sem efeito (`GET`) seguem abertas ou também exigem a chave
   (decisão 1).

### Parte B — Padrão de resposta e erro

1. **`code` estável em toda recusa**, por exceção do core (hoje: `RunnerNotFound`, `RunnerOperation`,
   `SafeModeTransition`, `DeadLetterNotFound`, `DeadLetterConflict`, `PortfolioNotFound`,
   `ConcurrentPositionLock`, `ExchangeQuery`, `UnsupportedCapability`, validação de entrada) e para os
   erros genéricos (`VALIDATION_FAILED`, `MALFORMED_BODY`, `INTERNAL_ERROR`). Lista fechada publicada
   no OpenAPI.
2. **Estado resultante** em toda mudança (hoje vários comandos devolvem só a transição ou `202`).
3. **Idempotência** nos comandos do operador: repetir halt, resume, terminate, cancelar ordem já
   cancelada, resolver DLQ já resolvida etc. devolve o estado atual sem erro nem efeito.
4. **`reason` obrigatório e histórico** nos comandos do operador que ainda não têm: halt, resume,
   terminate, limites do runner, cancelar ordem, fechar posição, resolver/reprocessar DLQ, criar
   portfolio/runner (o Safe Mode já exige). Histórico consultável num log único de ações do operador
   (decisão 4).
5. **OpenAPI completo**: descrição de campos, estados e códigos em todos os controllers.

### Parte C — Estado consolidado e inconsistências

`GET /api/admin/operational-state` → visão única para o agente verificar o sistema:

```json
{
  "status": "OK | WARN | CRITICAL",
  "generatedAt": "...",
  "checks": [
    {"id": "CAPITAL_RESERVED_WITHOUT_ORDER", "status": "CRITICAL", "summary": "...",
     "subjects": [{"portfolioId": "...", "runnerId": "...", "amount": "..."}],
     "since": "...", "action": "OPERATIONS_RUNBOOK#..."}
  ]
}
```

Cada verificação tem `id` estável, severidade, os sujeitos afetados e a seção do runbook a seguir.
Verificações propostas:

| id | O que detecta | Fonte |
|---|---|---|
| `SAFE_MODE_ACTIVE` | Portfolio fora de `NORMAL` (nível, origem, desde quando) | Portfolio (T45/T25/T43) |
| `CAPITAL_RESERVED_WITHOUT_ORDER` | `reserved > 0` sem ordem BUY em voo que o explique | Saldos × transações |
| `CAPITAL_RESERVATION_STALE` | Reserva mais velha que o limiar | T51 |
| `TRANSACTION_STUCK` | `PENDING`/`SUBMITTED`/`PARTIAL` além do limiar do watchdog | Transações |
| `POSITION_LOCK_ORPHAN` | Lote travado por transação já terminal | Posições × transações |
| `DLQ_UNRESOLVED` | Entradas não resolvidas | DLQ |
| `RUNNER_HALTED` / `RUNNER_RECONCILING` / `RUNNER_STUCK_TERMINATING` | Runners fora de operação | Runners (T49/T25) |
| `RUNNER_ON_HOLD_STRATEGY_UNUSABLE` | Runner `ACTIVE` cuja estratégia não está utilizável | Runners × catálogo (T54) |
| `STRATEGY_VERIFICATION_FAILED` | Estratégia habilitada sem passar na conferência | Catálogo (T54) |
| `MARKET_DATA_STALE` | Sem tick recente para símbolo com runner ativo | Último preço / tick |
| `EXCHANGE_REST_CIRCUIT_OPEN` | Circuit breaker das consultas REST aberto | T24 |
| `BOOT_NOT_READY` | Boot não concluído ou com falha | Boot |
| `EXCHANGE_BALANCE_DIVERGENCE` | Saldo local ≠ saldo na exchange | T29, **sob demanda** (decisão 3) |
| `EXCHANGE_TRADE_DIVERGENCE` | `binanceOnly`/`localOnly` na janela | T18, **sob demanda** (decisão 3) |

Também exposto como métrica (`operational_state_status` e por `id`) para alerta no Grafana.

### Parte D — Runbook executável

`docs/OPERATIONS_RUNBOOK.md` reescrito como procedimentos para agente: para cada `id` de verificação e
cada alerta, a consulta de diagnóstico, a ação com o corpo exato, a resposta esperada e como confirmar
que resolveu. As seções atuais viram a fonte; o formato muda.

## Decisões pendentes

1. **Endpoints que contornam o core e leituras:** remover de vez ordens diretas e controle manual de
   websocket/listeners (recomendado) ou só protegê-los com a chave? E as leituras (`GET`): exigir a
   chave também (recomendado, um único perímetro) ou manter abertas?
2. **Limiares e severidade** das verificações. Proposta: DLQ ≥ 1 `WARN`, ≥ 3 `CRITICAL` (igual ao HALT
   automático da T25); reserva sem ordem `CRITICAL`; transação presa `WARN` acima do limiar do watchdog e
   `CRITICAL` acima de 3 ciclos (igual à T25); Safe Mode `HALT` `WARN`, `CANCEL_ALL`/`PANIC_SELL`
   `CRITICAL`; tick ausente há mais de 60s com runner ativo `CRITICAL`.
3. **Comparação com a exchange:** só sob demanda (`?includeExchange=true`, recomendado, porque consome
   cota da API da Binance) ou sempre.
4. **Histórico das ações do operador:** um log único (`operator_actions`: quem/qual ação/alvo/`reason`/
   resultado/quando) consultável por API (recomendado) ou histórico por recurso, como na T54.
5. **`reason` obrigatório nos comandos existentes** é mudança de contrato: aplicar já (recomendado — o
   único cliente será o agente) ou aceitar ausência por um período.
6. **Entrega em partes:** um PR por parte (A → B → C → D, recomendado, com a Parte A primeiro pelo risco
   de segurança) ou um PR único.

## Riscos

- Mudança de contrato da API (auth, `reason`, códigos): qualquer script existente que chame a API quebra.
  Hoje não há consumidor além dos testes.
- Verificações caras no caminho da consulta: a Parte C precisa de consultas agregadas no banco, sem
  varrer tabelas inteiras a cada chamada.
- Falso positivo em verificação de consistência gera ação errada do agente: cada verificação precisa de
  teste com o caso positivo e o negativo.

## Critérios de aceitação (preliminares)

1. Nenhuma rota que muda estado responde sem `X-Admin-Key`; nenhuma rota envia ordem fora do core.
2. Toda recusa da API traz `code` da lista publicada no OpenAPI.
3. Repetir qualquer comando do operador já aplicado não falha nem gera efeito ou registro novo.
4. Todo comando do operador exige `reason` e aparece no histórico.
5. `GET /api/admin/operational-state` aponta cada inconsistência listada com sujeitos e ação, e devolve
   `OK` num sistema consistente (teste com Postgres para cada verificação, positivo e negativo).
6. Runbook cobre cada `id` de verificação com diagnóstico, ação, resposta esperada e confirmação.

## Fontes

- `.ai/product-direction.md` (princípios de operabilidade por IA)
- `.ai/tasks/T54-dynamic-strategy-catalog.md` (padrão de referência)
- `docs/OPERATIONS_RUNBOOK.md`, `docs/PHASE0_INVARIANTS.md`
- `spring-application/.../config/AdminWebMvcConfig.java`, `.../controller/*`
