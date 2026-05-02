# T4 — User Data Stream

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T2, T3  
**Status:** Pendente

---

## Descrição

A Binance opera dois canais WebSocket distintos com naturezas completamente diferentes:

- **Market Data Stream** — já implementado. Público, sem autenticação, envia preços contínuos. Tolerante a perda de mensagens.
- **User Data Stream** — este trabalho. Privado, autenticado via listen key, envia eventos transacionais da conta (`executionReport`). Cada mensagem importa.

Sem o User Data Stream, o sistema envia ordens mas nunca recebe confirmação. O `OrderConciliationUseCase` nunca é acionado, posições nunca fecham, capital nunca é liberado.

Esse padrão de dois canais é universal nas exchanges (Coinbase, Kraken, etc.), o que motivou uma decisão arquitetural: **separar os dois fluxos em ports distintos no core**, de forma que a Binance e futuras exchanges sigam o mesmo contrato.

---

## Decisão arquitetural: separação dos fluxos

### Problema com a arquitetura atual

O sistema hoje tem um único pipeline para tudo:

```
WebSocket → ProcessMessageHandler → instanceof MarketDataDto?  → estratégias
                                  → instanceof OrderDataDto?   → conciliação
```

Dois fluxos com naturezas distintas passam pelo mesmo tubo, e a infraestrutura (`ExchangeStreamingPort`, `ProcessMessageHandler`, `RawMessageReceivedEvent`) não distingue de qual canal veio a mensagem.

### Solução: dois ports, dois fluxos, dois eventos

```
Fluxo Market (já existe, refatorado)
ExchangeMarketStreamPort
    → WebSocket público (tickers, trades, book)
    → BinanceMarketMessageProcessor
    → MarketDataDto → PriceUpdateListeners → estratégias

Fluxo User/Account (novo)
ExchangeUserStreamPort
    → WebSocket privado (listen key / JWT)
    → BinanceUserDataProcessor (executionReport)
    → OrderDataDto → OrderUpdateListeners → OrderConciliationUseCase
```

**Cada exchange declara explicitamente o que suporta.** Se uma exchange não tiver user stream, simplesmente não registra o bean `ExchangeUserStreamPort`.

---

## Ciclo de vida do User Data Stream (Binance)

1. `POST /api/v3/userDataStream` (header `X-MBX-APIKEY`, sem signing) → `{ "listenKey": "..." }`
2. Conectar WebSocket em `wss://<host>/ws/<listenKey>`
3. Receber eventos `executionReport`
4. `PUT /api/v3/userDataStream?listenKey=<key>` a cada 30min (listen key expira em 60min)
5. `DELETE /api/v3/userDataStream?listenKey=<key>` no shutdown

---

## Mapeamento `executionReport` → `OrderDataDto`

| Campo Binance          | Campo OrderDataDto   | Observação                             |
|------------------------|----------------------|----------------------------------------|
| `c` (newClientOrderId) | clientOrderId        | Nosso identifier primário              |
| `i` (orderId)          | orderId              | ID da exchange                         |
| `s` (symbol)           | symbol               |                                        |
| `S` (side)             | side                 | BUY / SELL                             |
| `X` (currentStatus)    | status               | NEW / PARTIALLY_FILLED / FILLED / etc. |
| `z` (cumulativeQty)    | executedQuantity     | Quantidade total executada até agora   |
| `L` (lastPrice)        | executedPrice        | Preço da última execução               |
| `n` (fee)              | fee                  |                                        |
| `N` (feeAsset)         | feeAsset             |                                        |
| `T` (transactTime)     | timestamp            |                                        |
| `r` (rejectReason)     | rejectReason         | Presente quando status = REJECTED      |

---

## Invariantes de domínio

- `executedQty` no evento é **cumulativo** (não delta). O delta é calculado pelo `ConciliationOrderUpdate`, não aqui.
- Status `PARTIALLY_FILLED` mapeia para `PARTIAL` no domínio interno.
- Status `EXPIRED` mapeia para `EXPIRED` (runner deve liberar reserva).
- `executionReport` duplicado não deve reprocessar efeito financeiro — o `OrderConciliationUseCase` já tem idempotência.
- `clientOrderId` desconhecido: logar como warning e descartar sem lançar exceção.

---

## Plano de implementação

### PR 1 — Separação de fluxos: core + spring-application

**Objetivo:** introduzir o conceito de dois canais separados na arquitetura, sem ainda implementar nada de Binance.

**`core` — novo port:**
```
ExchangeUserStreamPort
    getExchangeName(): String
    getWebSocketPort(): WebSocketPort
    getReceivedMessageProcessor(): ReceivedMessageProcessorPort
```
Port receive-only — sem sender, sem url builder. Paralelo ao `ExchangeStreamingPort` (que passa a ser explicitamente o canal de market data).

**`core` — novo handler:**
```
ProcessUserMessageHandler implements HandlerProcessUserMessagePort
    execute(rawMessage, context): ProcessingResult<?>
    → busca ExchangeUserStreamPort pelo exchangeName
    → chama receivedMessageProcessor.processMessage()
    → roteia OrderDataDto → notifyOrderUpdate()
```
Separado do `ProcessMessageHandler` existente. Cada handler tem uma única responsabilidade.

**`core` — repositório:**
- `ExchangeAdapterRepositoryPort` ganha `findUserStreamByName(String exchangeName)`
- `InMemoryExchangeAdapterRepository` registra beans `ExchangeUserStreamPort` separadamente

**`spring-application` — novo evento e listener:**
```
RawUserDataMessageReceivedEvent   ← publicado pelo listener converter do user stream
ProcessUserMessageEventListener   ← @EventListener para o novo evento, @Async
```
O fluxo de market continua usando `RawMessageReceivedEvent` + `ProcessMessageEventListener` sem alteração.

**`spring-application` — boot:**
- `ExchangeConnectService` (ou equivalente) passa a conectar `ExchangeUserStreamPort` além de `ExchangeStreamingPort`

**Resultado:** arquitetura preparada para qualquer exchange implementar user stream. Nenhum adaptador Binance tocado ainda. Build verde.

---

### PR 2 — Binance User Data Stream

**Objetivo:** implementar o User Data Stream da Binance usando a infraestrutura criada no PR 1.

**Infraestrutura HTTP:**
- Extrair `OkHttpClient` como bean compartilhado em `BinanceAdapterConfiguration`
- `OkHttp3WebSocketAdapter` passa a aceitar `OkHttpClient` injetado (em vez de criar o próprio)
- Segunda instância de `OkHttp3WebSocketAdapter` para o user stream, publicando `RawUserDataMessageReceivedEvent`

**`adapter-binance` — novos arquivos:**
```
binance/userdata/
    ListenKeyManager.java         — obtain(), keepalive() a cada 30min, revoke()
                                    usa OkHttpClient diretamente (Spring-free)
                                    ScheduledExecutorService interno para keepalive

binance/event/
    ExecutionReportEvent.java     — record com campos do JSON da Binance

binance/processor/receive/
    ExecutionReportProcessor.java — BinanceEventProcessor<OrderDataDto>
                                    eventType() = "executionReport"
                                    mapeia ExecutionReportEvent → OrderDataDto

binance/userdata/
    BinanceUserDataProcessor.java — ReceivedMessageProcessorPort
                                    registra apenas ExecutionReportProcessor
                                    (sem bookTicker, sem ticker, sem trade)
```

**`spring-application` — novo adapter:**
```
BinanceUserStreamAdapter implements ExchangeUserStreamPort
    getExchangeName() → "BINANCE"
    getWebSocketPort() → userDataWebSocketPort
    getReceivedMessageProcessor() → BinanceUserDataProcessor
    connect(listenKey) → userDataWebSocketPort.connect(wss://.../ws/<listenKey>)
    disconnect() → userDataWebSocketPort.disconnect() + ListenKeyManager.revoke()
```

**`BinanceAdapterConfiguration` — wiring:**
```java
var httpClient      = new OkHttpClient.Builder()...build();
var listenKeyMgr    = new ListenKeyManager(httpClient, config, credentials);
var userDataProc    = new BinanceUserDataProcessor(objectMapper);
var userDataWs      = new OkHttp3WebSocketAdapter(httpClient, userDataListenerConverter);
var userStreamAdapt = new BinanceUserStreamAdapter(userDataWs, userDataProc, listenKeyMgr, urlBuilder);
// registrar userStreamAdapt como bean ExchangeUserStreamPort
```

**Resultado:** User Data Stream funcionando end-to-end. `executionReport` → `OrderDataDto` → `OrderConciliationUseCase`.

---

## Ordem de execução

```
PR 1 — core + spring (infraestrutura de dois fluxos)
    ↓ merge
PR 2 — adapter-binance (implementação do User Data Stream)
    ↓ merge
T4 concluída
```

PR 1 não toca nenhum adapter — é puro core e spring. Pode ser revisado e mergeado independentemente. PR 2 depende de PR 1.

---

## Critérios de aceitação

1. Ao iniciar com `BINANCE`, a aplicação obtém um listen key e conecta ao User Data Stream.
2. `executionReport` FILLED aciona `OrderConciliationUseCase` e transita a transação corretamente.
3. `executionReport` PARTIALLY_FILLED aplica apenas o delta.
4. `executionReport` CANCELED ou REJECTED libera reserva de capital.
5. Listen key renovado automaticamente a cada 30 minutos.
6. No shutdown, listen key é revogado na Binance.
7. Se o stream cair (listen key expirado), a aplicação obtém novo listen key e reconecta.
8. `clientOrderId` desconhecido é logado como warning e descartado sem exceção.

---

## Testes de integração obrigatórios

Usar MockWebServer para simular endpoints REST de listen key e servidor WebSocket local para simular o User Data Stream.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| `executionReport` FILLED recebido | `transactions.status = FILLED`, `positions` atualizada, reserva liberada, `transaction_matches` populado |
| `executionReport` PARTIALLY_FILLED recebido | Apenas delta aplicado; `executed_qty` incrementado; reserva parcialmente mantida |
| `executionReport` CANCELED recebido | Reserva liberada, `transactions.status = CANCELED`, sem entrada em `positions` |
| `executionReport` REJECTED recebido | Reserva liberada, `transactions.status = REJECTED`, `reject_reason` preservado |
| Mesmo `executionReport` FILLED duas vezes | Nenhum efeito financeiro duplicado; `transaction_matches` sem duplicata |
| Listen key expirado | Aplicação obtém novo listen key e reconecta; log de WARNING emitido |
| `clientOrderId` desconhecido | Warning logado, evento descartado, nenhuma exceção propagada |
