# T4 — User Data Stream

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T2, T3  
**Status:** Pendente

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
