# T4 — User Data Stream

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T2, T3  
**Status:** Concluído

---

## Descrição

O `BinanceReceivedMessageProcessor` atual processa apenas eventos de mercado (TickerProcessor). Ele não tem processador para `executionReport` — o evento que a Binance emite via WebSocket quando uma ordem muda de estado (NEW, PARTIAL_FILL, FILLED, CANCELED, REJECTED, EXPIRED).

Sem este evento, nenhum fill chega ao sistema. O `OrderConciliationUseCase` nunca é acionado, posições nunca fecham, capital nunca é liberado.

O User Data Stream é um stream WebSocket separado do stream de mercado, autenticado via listen key. Esta tarefa implementa todo o ciclo de vida desse stream.

---

## Escopo técnico

**Ciclo de vida do User Data Stream na Binance:**

1. `POST /api/v3/userDataStream` → retorna `{ "listenKey": "..." }` (requer `X-MBX-APIKEY`, sem signing)
2. Conectar WebSocket em `wss://<host>/ws/<listenKey>`
3. Receber eventos `executionReport` e processar
4. `PUT /api/v3/userDataStream?listenKey=<key>` a cada 30 minutos (keepalive; listen key expira em 60min sem renovação)
5. `DELETE /api/v3/userDataStream?listenKey=<key>` no shutdown

**Mapeamento de `executionReport` para `OrderDataDto`:**

| Campo Binance         | Campo OrderDataDto        | Observação                              |
|-----------------------|---------------------------|-----------------------------------------|
| `c` (newClientOrderId)| clientOrderId             | Nosso identifier primário               |
| `i` (orderId)         | exchangeOrderId           |                                         |
| `s` (symbol)          | symbol                    |                                         |
| `S` (side)            | side                      | BUY / SELL                              |
| `X` (currentStatus)   | status                    | NEW / PARTIALLY_FILLED / FILLED / etc.  |
| `z` (cumulativeQty)   | executedQty               | Quantidade total executada até agora    |
| `Z` (cumulativeQuote) | cumulativeQuoteQty        |                                         |
| `L` (lastPrice)       | lastExecutedPrice         |                                         |
| `n` (fee)             | fee                       |                                         |
| `N` (feeAsset)        | feeAsset                  |                                         |
| `T` (transactTime)    | transactTime              |                                         |
| `r` (rejectReason)    | rejectReason              | Presente quando status = REJECTED       |

**Arquivos a criar/modificar:**
- `adapter-binance/src/main/java/com/marmitt/binance/userdata/ListenKeyManager.java` — gerencia o ciclo de vida do listen key (obter, keepalive, revogar)
- `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/ExecutionReportProcessor.java` — novo processador especializado
- `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/BinanceReceivedMessageProcessor.java` — registrar o novo processador
- `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/BinanceExchangeAdapter.java` — integrar o `ListenKeyManager` no lifecycle do adapter

**Invariantes de domínio que este fluxo deve preservar:**
- `executionReport` duplicado (mesmo `clientOrderId` + mesmo `status` + mesmo `executedQty`) não deve reprocessar efeito financeiro — o `OrderConciliationUseCase` já tem idempotência, mas o processador não deve entregar duplicatas desnecessariamente.
- `executedQty` no evento é cumulativo (não delta). O delta é calculado pelo `ConciliationOrderUpdate`, não aqui.
- Status `PARTIALLY_FILLED` mapeia para `PARTIAL` no domínio interno.
- Status `EXPIRED` mapeia para `EXPIRED` (runner deve liberar reserva).

---

## Critérios de aceitação

1. Ao iniciar com `BINANCE` como exchange, a aplicação obtém um listen key via REST e conecta ao User Data Stream.
2. Ao receber um `executionReport` de FILLED, o `OrderConciliationUseCase` é acionado e a transação transita para o estado correto.
3. Ao receber um `executionReport` de PARTIALLY_FILLED, a conciliação aplica apenas o delta (não a quantidade total).
4. Ao receber um `executionReport` de CANCELED ou REJECTED, a reserva de capital é liberada.
5. O listen key é renovado automaticamente a cada 30 minutos sem intervenção manual.
6. No shutdown da aplicação, o listen key é revogado na Binance.
7. Se o listen key expirar (stream cair), a aplicação obtém um novo listen key e reconecta automaticamente.
8. Eventos com `clientOrderId` não reconhecido são logados como warning e descartados sem lançar exceção.
## Testes de integração obrigatórios

Usar MockWebServer para simular os endpoints REST de listen key e um servidor WebSocket local para simular o User Data Stream. A conciliação deve ser verificada no estado persistido (banco).

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| `executionReport` FILLED recebido via stream | `transactions.status = FILLED`, `positions` atualizada, `global_balance` com reserva liberada, `transaction_matches` populado |
| `executionReport` PARTIALLY_FILLED recebido | Apenas delta aplicado; `transactions.executed_qty` incrementado; reserva parcialmente mantida |
| `executionReport` CANCELED recebido | Reserva liberada, `transactions.status = CANCELED`, sem entrada em `positions` |
| `executionReport` REJECTED recebido | Reserva liberada, `transactions.status = REJECTED`, `reject_reason` preservado |
| Mesmo `executionReport` FILLED entregue duas vezes | Nenhum efeito financeiro duplicado; `transaction_matches` sem duplicata |
| Listen key expirado (stream desconecta) | Aplicação obtém novo listen key e reconecta sem intervenção manual; log de WARNING emitido |
| `clientOrderId` desconhecido no evento | Warning logado, evento descartado, nenhuma exceção propagada, sistema continua operando |
---

## ComentÃ¡rio de revisÃ£o geral

### Achados

1. `P1` `ExecutionReportProcessor` estÃ¡ alimentando o `core` com o preÃ§o errado para fills parciais/finais. Em `adapter-binance/.../ExecutionReportProcessor.java`, o campo `L` estÃ¡ sendo usado como `executedPrice`, mas o `core` trata `executedPrice` como WAP cumulativo no `FillCalculator`. Na documentaÃ§Ã£o da Binance, `L` representa o last executed price; o preÃ§o mÃ©dio cumulativo precisa ser derivado de `Z / z`. Do jeito atual, a partir do segundo fill o cÃ¡lculo marginal tende a ficar financeiramente incorreto e pode distorcer `TransactionMatch`, custo mÃ©dio e PnL.

2. `P1` O reconnect do user stream vaza `listenKey` antigo. Em `BinanceUserStreamAdapter.connect(...)`, o fluxo cancela apenas o keepalive anterior e obtÃ©m um novo listen key, mas a revogaÃ§Ã£o do key anterior sÃ³ ocorre em `disconnect()`. Em cenÃ¡rios de reconnect automÃ¡tico apÃ³s falha, o `listenKey` anterior pode ser sobrescrito no `ListenKeyManager` sem passar pela revogaÃ§Ã£o, deixando chaves ativas atÃ© expiraÃ§Ã£o do lado da exchange e abrindo espaÃ§o para stream duplicado/stale.

3. `P1` O endpoint genÃ©rico de conexÃ£o agora tenta subir user stream para qualquer exchange e transforma “capability nÃ£o suportada” em falha operacional. O `ExchangeConnectService` chama market + user stream sempre, enquanto `ConnectUserStreamUseCase` retorna failure quando nÃ£o existe adapter. Para exchanges como `MOCK`, isso vira uma resposta com sucesso parcial artificial. AlÃ©m disso, se o market falhar e o user stream subir, o sistema fica em estado meio-conectado sem uma regra explÃ­cita no `core` para esse cenÃ¡rio.

4. `P2` O PR puxou uma abstraÃ§Ã£o de transporte puro para dentro do `core` sem dono de negÃ³cio. `HttpClientPort` existe apenas para viabilizar o `ListenKeyManager` Binance. Isso Ã© detalhe de provider/infra, nÃ£o necessidade da regra de negÃ³cio. Pela fronteira do projeto, esse tipo de contrato deveria nascer fora do `core`; do jeito atual, o mÃ³dulo de domÃ­nio passa a carregar um port que nÃ£o representa intenÃ§Ã£o de negÃ³cio, sÃ³ mecÃ¢nica HTTP de adapter.

5. `P2` O PR quebra contratos REST existentes sem compatibilidade. O `/websocket/connect` mudou de `WebSocketConnectionResponse` para `Map<String, WebSocketConnectionResponse>`, e `/websocket/all` e `/websocket/stats/all` passaram a retornar chaves `exchange:channel`. Isso Ã© uma mudanÃ§a externa de payload, nÃ£o sÃ³ um refactor interno.

6. `P3` A nova telemetria de `USER_DATA` fica incoerente com o prÃ³prio endpoint de stats. `ProcessUserMessageHandler` nÃ£o atualiza `WebSocketConnectionManager.onMessageReceived()`, ao contrÃ¡rio do fluxo de market data. Como o PR passa a expor status/stats por canal, os nÃºmeros do canal `USER_DATA` tendem a ficar zerados ou defasados mesmo com trÃ¡fego real.

### CoerÃªncia arquitetural

A principal quebra de fronteira estÃ¡ em `HttpClientPort`: o `core` passou a carregar uma abstraÃ§Ã£o criada apenas para um detalhe REST do Binance. TambÃ©m houve deslocamento de orquestraÃ§Ã£o para o delivery layer em `ExchangeConnectService`: a regra “conectar market + user stream juntos” nÃ£o estÃ¡ modelada como use case do `core`, mas decidida no `spring-application`.

### CoerÃªncia de boas prÃ¡ticas

O PR altera contratos externos (`/connect`, `/all`, `/stats/all`) sem compatibilidade visÃ­vel e sem uma trilha de versionamento. TambÃ©m faltam testes focados para os cenÃ¡rios mais arriscados dessa mudanÃ§a: parsing de `executionReport`, reconnect com reaproveitamento de `listenKey`, exchanges sem user stream e coerÃªncia dos novos payloads REST.

### CoerÃªncia de negÃ³cio

O risco mais sÃ©rio estÃ¡ no tratamento de fills: o preÃ§o acumulado vindo do user stream estÃ¡ incompatÃ­vel com a contabilidade marginal do `core`. O segundo ponto Ã© o ciclo de vida do user stream: reconnect e connect genÃ©rico hoje permitem estados “meio conectados” e vazamento de `listenKey`, o que pode gerar callback stale e reconciliaÃ§Ã£o fora do esperado.
