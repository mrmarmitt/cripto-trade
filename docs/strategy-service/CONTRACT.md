# Strategy Service Contract

Contrato de rede para estratégias executadas **fora do processo** do ctrade. Cada versão de
estratégia sobe como uma app independente (Java, Node, Python, …) que implementa este contrato.
O ctrade é o cliente; a app da estratégia é o servidor.

- **Fonte de verdade da forma:** [`openapi.yaml`](./openapi.yaml) (usado para codegen/validação nos três lados).
- **Fonte de verdade da semântica:** este documento.
- **Espelho interno em Java:** `core/src/main/java/com/marmitt/core/dto/strategy/*`. Se divergir do
  spec, o spec prevalece **na fronteira de rede** — o core continua dono do contrato de domínio.

> Status: **proposta de contrato** para o desacoplamento out-of-process das estratégias (Fase 2 do
> roadmap de decoupling). Ainda não há adapter consumindo isto; ver "Onde isto se encaixa".

---

## Por que este contrato existe

Hoje as estratégias são classes Java linkadas no classpath e registradas no boot
(`InMemoryStrategyRepository`), então propor/melhorar uma estratégia exige recompilar e reiniciar a
aplicação. Empurrando a execução para trás de um contrato de rede, a linguagem atrás do endpoint
deixa de importar: o core permanece Java puro e o ecossistema de estratégias pode ser poliglota e de
iteração rápida — pré-condição para um ciclo autônomo de melhoria (IA propõe → valida → promove) sem
reiniciar o ctrade.

---

## Endpoints

| Método | Caminho | Papel |
|---|---|---|
| `POST` | `/v1/evaluate` | Uma decisão pura por tick. É a chamada quente. |
| `GET`  | `/v1/health`   | Liveness + **readiness** (warm-up concluído), escopada por `strategyRef`. |
| `GET`  | `/v1/meta`     | Identidade (`strategyId`, `name`, `version`) para popular/verificar o catálogo. |

---

## Convenções (as três regras inegociáveis)

### 1. Decimal como string, sempre

Todo valor decimal/monetário (`currentPrice`, `quantity`, `confidence`, capitais, PnL, …) trafega
como **string JSON**, nunca como `number`. Isso protege as invariantes financeiras
(`docs/PHASE0_INVARIANTS.md`) contra ruído de ponto flutuante introduzido fora do JVM.

Cada app **deve** (de)serializar com biblioteca decimal, **nunca** float/Number nativo:

| Linguagem | Use | Nunca |
|---|---|---|
| Java | `BigDecimal` | `double` / `float` |
| Python | `decimal.Decimal` | `float` |
| Node | `decimal.js` / `big.js` | `Number` |

```jsonc
"currentPrice": "64150.75000000"   // ✅ string
"currentPrice": 64150.75            // ❌ number — proibido
```

### 2. Timestamps em ISO-8601 UTC

Strings `date-time` (`2026-07-03T14:22:05.123Z`). Sem epoch, sem timezone local.

### 3. UUIDs como string `uuid`

`strategyRef`, `runnerId`, `lotId`, `transactionId`, etc.

---

## Semântica de `/evaluate`

### Request (`EvaluateRequest`)

- `strategyRef` — identidade da versão alvo (== `strategyId` de `/meta`). Permite que uma única app
  sirva múltiplas versões e é a **chave do estado de warm-up** interno.
- `input` (`MarketInput`) — dados do tick. `currentPrice` é obrigatório e positivo; os demais preços
  são opcionais. A validação semântica rica (spread, faixa diária, idade do dado) é do ctrade — a app
  recebe o dado já aceito.
- `context` (`StrategyContext`) — estado operacional **read-only**: capital, posição agregada, lotes
  abertos e ordens em trânsito. `availableQuantity` de cada lote é o teto de SELL daquele lote.

### Response (`Decision`) — regras de coerência por `decision`

| `decision` | Campos obrigatórios | Ignorados | Efeito |
|---|---|---|---|
| `SHOULD_HOLD` | — | `quantity`, `confidence` | Nenhuma ação neste tick. |
| `SHOULD_BUY` | `quantity > 0`, `confidence ∈ [0,1]` | — | `limitPrice` null ⇒ preço de mercado do tick. |
| `SHOULD_SELL` | `quantity > 0`, `confidence ∈ [0,1]` | — | `targetLotId` null ⇒ FIFO. |
| `SHOULD_CANCEL` | `targetTransactionId` | `quantity`, `confidence` | Cancela ordem em trânsito. |

- `limitPrice` — quando informado, deve ser positivo; é o valor que persiste, reserva capital e vai à
  exchange. `null` = usar o preço de mercado do tick (default).
- `quantity` é **absoluta** (ex.: `"0.05"` BTC), não percentual.

---

## Estado, warm-up e readiness

A app **pode** manter estado de warm-up (ex.: janela de histórico de uma média móvel) chaveado por
`strategyRef`. Esse é o único efeito colateral admitido — nada de I/O externo, rede ou persistência
na decisão. A decisão deve ser função de `(input, context)` + estado de warm-up acumulado.

`GET /health` reporta `ready`:

- `ready=false` — app viva, mas ainda aquecendo (ex.: `"warming up: 12/20 ticks"`). Responde `503`.
- `ready=true` — warm-up concluído; a versão pode ir para `ACTIVE`.

**Readiness é escopada por versão.** Como o warm-up é chaveado por `strategyRef`, a readiness também
é: numa app que serve V1 e V2, a V1 pode estar `ready=true` enquanto a V2 ainda aquece. Por isso
`GET /health?strategyRef=<uuid>` responde a readiness **daquela** versão e ecoa o `strategyRef` a que
`ready` se refere. Sem o parâmetro, `ready` é o agregado da app (`true` só se **todas** as versões
servidas estão prontas) — suficiente para apps de versão única. Um `ready` app-wide único seria
ambíguo com múltiplas versões: promoveria uma V2 fria ou barraria uma V1 pronta.

**O portão de promoção do ctrade consulta `/health` com o `strategyRef` alvo e não vira o ponteiro do
runner para uma versão com `ready=false`.** Isso é o que torna o deploy-ao-lado seguro: a V2 sobe e
aquece em paralelo enquanto a V1 opera, e a troca só ocorre quando a V2 se declara pronta — sem janela
cega.

---

## Falha e timeout

A resposta de `/evaluate` **deve** ser sempre uma decisão válida. Em erro interno, a app deve preferir
devolver `SHOULD_HOLD` explícito a estourar exceção.

Do lado do ctrade, qualquer falha de transporte, timeout ou `5xx` é tratada como **HOLD implícito** —
o runner simplesmente não age naquele tick, sem penalizar os demais runners. Isso espelha a
normalização que o `StrategySignalEvaluator` já aplica hoje para retorno nulo/exceção da estratégia
in-process. Ou seja: uma app de estratégia indisponível degrada para "não opera", nunca para erro no
pipeline financeiro.

---

## Versionamento e identidade

- **Uma versão = um `strategyId` (UUID) próprio.** V2 não reusa o UUID da V1.
- Durante a coexistência V1/V2, o runner deve resolver por **`strategyId` explícito**, não por nome
  (o fallback por nome fica ambíguo com duas versões de mesmo nome no ar).
- `contractVersion` (em `/meta`) declara a versão **deste contrato** que a app implementa, separada da
  `version` da estratégia. O caminho é versionado (`/v1`); mudança incompatível de contrato ⇒ `/v2`.

---

## Exemplo

**Request** `POST /v1/evaluate`

```json
{
  "strategyRef": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "input": {
    "symbol": "BTCUSDT",
    "currentPrice": "64150.75000000",
    "previousPrice": "64020.10000000",
    "bidPrice": "64150.00000000",
    "askPrice": "64151.50000000",
    "volume": "1832.44000000",
    "high24h": "64980.00000000",
    "low24h": "63110.00000000",
    "timestamp": "2026-07-03T14:22:05.123Z"
  },
  "context": {
    "runnerId": "11111111-1111-1111-1111-111111111111",
    "portfolioId": "22222222-2222-2222-2222-222222222222",
    "symbol": "BTCUSDT",
    "positionContext": {
      "symbol": "BTCUSDT",
      "quantity": "0",
      "averagePrice": "0",
      "currentPrice": "64150.75000000",
      "unrealizedPnl": "0",
      "realizedPnl": "0",
      "openLots": []
    },
    "openLots": [],
    "pendingOrders": [],
    "totalCapital": "10000.00000000",
    "availableCapital": "10000.00000000",
    "maxOperationAmount": "2000.00000000",
    "minOperationAmount": "50.00000000",
    "maxOpenPositions": 5,
    "currentOpenPositions": 0,
    "realizedPnl": "0",
    "unrealizedPnl": "0"
  }
}
```

**Response** `200`

```json
{
  "strategyName": "SimpleMovingAverageStrategy",
  "decision": "SHOULD_BUY",
  "confidence": "0.80",
  "quantity": "0.03118000",
  "limitPrice": null,
  "targetLotId": null,
  "targetTransactionId": null,
  "reasoning": "Cruzamento de SMA (Alta)",
  "timestamp": "2026-07-03T14:22:05.140Z",
  "metadata": { "sma": "63980.42000000" }
}
```

---

## Onde isto se encaixa

Camada 1 do desacoplamento de estratégias. As demais peças (fora do escopo deste spec):

- **Core:** `StrategyEvaluationPort` + uma `TradingStrategy` proxy que delega `executeStrategy` a este
  endpoint. O core permanece puro e agnóstico de transporte.
- **spring-application:** adapter REST cliente deste contrato + `strategy_catalog` (ref → endpoint →
  status) + registry dinâmico que troca o proxy ativo em runtime via o `registerStrategy` já existente.
- **Loop autônomo:** harness de backtest, gate de invariantes, shadow/canário e máquina de promoção
  (`REGISTERED → SHADOW → ACTIVE → DRAINING → RETIRED`).

Transporte inicial é **REST/JSON**; o `StrategyEvaluationPort` é agnóstico, então gRPC é uma troca de
adapter no futuro sem tocar no core, caso a serialização vire gargalo medido.
