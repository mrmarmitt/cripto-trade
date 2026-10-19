# T40 — Revisão do Contrato do Strategy Service antes do Adapter

**Complexidade:** Média (contrato; sem código de produção)
**Responsável:** Claude
**Dependências:** nenhuma (contrato v1 entregue no PR #134)
**Status:** Concluído (2026-10-19) — ver "Entrega".

---

## Descrição

O contrato out-of-process (`docs/strategy-service/CONTRACT.md` + `openapi.yaml`) resolve bem
decimal-como-string, `oneOf` por decisão e readiness escopada, mas deixa abertos quatro pontos que
viram bug assim que houver estado de warm-up atrás de uma chamada de rede. Como **ainda não há
consumidor**, mudar o contrato agora é gratuito; depois da T41, cada mudança vira migração coordenada
entre ctrade e apps de estratégia.

---

## Problemas a resolver

### 1. Chave de warm-up só por `strategyRef`

O contrato chaveia o estado de warm-up por `strategyRef`. Dois runners usando a mesma versão em
símbolos diferentes (BTCUSDT e ETHUSDT) misturariam o histórico. O mesmo bug existe hoje em
processo: `SimpleMovingAverageStrategy` é uma instância única registrada no
`InMemoryStrategyRepository`, com um único `priceHistory`.

**Proposta:** chave de estado = `(strategyRef, context.runnerId)`. Readiness escopada passa a poder
responder por runner (`GET /v1/health/{strategyRef}?runnerId=`), mantendo a agregada por versão.

### 2. `/evaluate` não é seguro de repetir

A decisão é declarada pura, mas cada chamada **muta** o warm-up (acrescenta o tick ao histórico). Um
retry de transporte conta o mesmo tick duas vezes.

**Proposta:** `EvaluateRequest` ganha `tickId` (string opaca) e `sequence` (inteiro monotônico por
runner). A app descarta para o warm-up um `sequence <= último visto` daquele runner e devolve a
decisão em cache para o mesmo `tickId`. O contrato declara: **o ctrade não faz retry de `/evaluate`**;
falha vira HOLD implícito.

### 3. Decisão pode chegar velha

A decisão calculada no tick *t* é aplicada em *t + latência*. Para `SHOULD_BUY`/`SHOULD_SELL` sem
`limitPrice` (preço de mercado do tick), isso é risco de execução.

**Proposta:** documentar que o ctrade descarta (como HOLD implícito, com métrica) decisões cuja
latência exceda `ctrade.strategy.remote.max-decision-age` — validação do lado do ctrade, sem campo
novo obrigatório na resposta (o `input.timestamp` do request já é a âncora).

### 4. Campos de PnL não confiáveis no `StrategyContext`

`Position.updateCurrentPrice` não tem chamador: `positionContext.currentPrice` fica igual ao
`averagePrice` da abertura, e `unrealizedPnl` / `currentPnlPercent` são sempre zero. Enviar esses
campos a apps externas induz estratégias a decidir sobre dado falso.

**Proposta:** até a marcação a mercado existir (ver T43), documentar no contrato que esses campos
não são confiáveis e que a app deve calcular PnL de lote a partir de `input.currentPrice` e
`averagePrice`/`entryPrice` do lote — ou removê-los do contrato v1.

---

## Arquivos a modificar

| Arquivo | Mudança |
|---|---|
| `docs/strategy-service/CONTRACT.md` | Seções "Estado, warm-up e readiness", "Falha e timeout" e exemplo atualizados com os quatro pontos |
| `docs/strategy-service/openapi.yaml` | `tickId`, `sequence` no `EvaluateRequest`; `runnerId` opcional em `/health/{strategyRef}`; descrição dos campos de PnL |

---

## Critérios de aceitação

1. Chave de estado `(strategyRef, runnerId)` declarada no contrato e no spec.
2. `tickId`/`sequence` no `EvaluateRequest` com semântica de dedupe documentada; "sem retry" explícito.
3. Regra de descarte por idade da decisão documentada do lado do ctrade.
4. Campos de PnL não confiáveis documentados ou removidos.
5. `openapi.yaml` valida (lint) e o exemplo do `CONTRACT.md` é válido contra ele.

---

## Fontes

- `docs/strategy-service/CONTRACT.md`, `docs/strategy-service/openapi.yaml`.
- `strategy/src/main/java/com/marmitt/strategy/impl/simple_moving_avager/SimpleMovingAverageStrategy.java` (`priceHistory` por instância).
- `spring-application/src/main/java/com/marmitt/application/spring/repository/InMemoryStrategyRepository.java`.
- `core/src/main/java/com/marmitt/core/domain/runner/Position.java` (`updateCurrentPrice` sem chamador).

---

## Achados da T39 (2026-10-16)

- **`totalCapital` no contexto:** IG §14.7/§14.8 decidem que a estratégia não vê o capital total do
  portfolio, mas `RunnerContextAssembler` envia `totalCapital`. Decidir no contrato: remover ou
  documentar a exceção.
- **Estratégia stateless (IG §14.11)** já coberta pelo item de warm-up por runner.
- **Idade da decisão / stale signal (IG §12.3)** já coberta; o descarte efetivo no runner acontece na T42.
- **`orderType` no contrato (IG §14.5.2):** a decisão de tipo é do core (T45); a proteção LIMIT
  configurável fica na T48.

---

## Entrega (2026-10-19)

Contrato revisado para **1.1** (ainda `/v1`: não há consumidor).

| Ponto | Resolução |
|---|---|
| 1. Chave de warm-up | `(strategyRef, context.runnerId)`; `GET /health/{strategyRef}?runnerId=` responde pelo runner e `ScopedHealth` ecoa `runnerId`; sem o parâmetro, `ready` só quando todos os runners vistos pela versão estão prontos. O portão de promoção consulta por runner. |
| 2. `/evaluate` repetível | `EvaluateRequest` ganha `tickId` (obrigatório, decisão em cache por runner) e `sequence` (obrigatório, só `sequence` maior entra no warm-up). Nova seção "Entrega e idempotência": **o ctrade não faz retry**. |
| 3. Decisão velha | Descarte do lado do ctrade, como HOLD implícito com métrica, quando `agora − input.timestamp > ctrade.strategy.remote.max-decision-age`; sem campo novo na resposta. Implementação na T41/T42. |
| 4. PnL do contexto | Resolvido pela T43: `currentPrice` é o preço do tick, `unrealizedPnl = preço × quantidade − custo pago`, `currentPnlPercent` sobre o `entryPrice`. Documentado como confiável no contrato e no spec. |
| Achado T39: `totalCapital` | **Mantido e documentado**: `available + reserved` do portfolio para dimensionar a ordem (o SMA aloca um percentual dele). Não é limite — teto do runner e saldo são aplicados pelo ctrade na reserva. IG §14.7/§14.8 corrigidos. |

### Validação

- `@redocly/cli@1.25.11 lint --extends=minimal`: spec válido. Com o perfil `recommended` restam só
  `security-defined` (o contrato não define autenticação — já era assim) e dois avisos de estilo.
- Exemplos do `CONTRACT.md` (request e response) validados contra `EvaluateRequest`/`Decision` com
  `ajv@8.17.1` (conversão de `nullable` do OpenAPI 3.0); o mesmo exemplo sem `tickId` é rejeitado.

### Pendências repassadas

- **T41:** gerar `tickId`/`sequence` por runner, não fazer retry, aplicar `max-decision-age`, consultar
  readiness por runner — e **corrigir o warm-up in-process do SMA** para a mesma chave
  `(strategyRef, runnerId)`: hoje o `priceHistory` é único por instância, e a paridade in-process ×
  remoto só valeria para um runner.

