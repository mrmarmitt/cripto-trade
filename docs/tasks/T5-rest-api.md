# T5 — REST API Binance

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T2, T3  
**Status:** Pendente

---

## Descrição

Todos os métodos REST do `BinanceExchangeAdapter` lançam `UnsupportedOperationException`. Esta tarefa implementa os 6 métodos necessários para operação completa:

- **Submissão de ordens** (`submitOrder`) — caminho crítico de execução de trading
- **Cancelamento** (`cancelOrder`) — graceful shutdown de posições abertas
- **Query de ordem** (`queryOrderByClientOrderId`, `queryOrderByExchangeOrderId`) — boot recovery (fase 3)
- **Listagem de ordens abertas** (`listAllOpenOrders`) — boot recovery e sanidade
- **Snapshot de conta** (`queryAccountSnapshot`) — boot fase 2 (sanity check)

O `OrderProcessor` atual envia ordens via WebSocket API da Binance (protocolo `order.place`). Esta tarefa mantém o WebSocket como caminho principal de envio e adiciona REST como caminho de query e fallback.

---

## Escopo técnico

**Endpoints a implementar:**

| Método                        | Endpoint Binance                    | Auth       |
|-------------------------------|-------------------------------------|------------|
| `submitOrder`                 | `POST /api/v3/order`                | Signed     |
| `cancelOrder`                 | `DELETE /api/v3/order`              | Signed     |
| `queryOrderByClientOrderId`   | `GET /api/v3/order?origClientOrderId=` | Signed  |
| `queryOrderByExchangeOrderId` | `GET /api/v3/order?orderId=`        | Signed     |
| `listOpenOrdersBySymbol`      | `GET /api/v3/openOrders?symbol=`    | Signed     |
| `listAllOpenOrders`           | `GET /api/v3/openOrders`            | Signed     |
| `queryAccountSnapshot`        | `GET /api/v3/account`               | Signed     |

**Arquivos a criar/modificar:**
- `adapter-binance/src/main/java/com/marmitt/binance/rest/BinanceRestClient.java` — novo, cliente HTTP baixo nível (OkHttp3 já está no classpath)
- `adapter-binance/src/main/java/com/marmitt/binance/rest/BinanceOrderMapper.java` — novo, mapeia response JSON → `OrderDataDto`
- `adapter-binance/src/main/java/com/marmitt/binance/rest/BinanceAccountMapper.java` — novo, mapeia response JSON → `AccountDataDto`
- `spring-application/.../config/exchange/BinanceExchangeAdapter.java` — substituir `throw restNotImplemented()` pelas implementações

**Mapeamento da resposta de ordem (`GET /api/v3/order`) para `OrderDataDto`:**

| Campo Binance        | Campo OrderDataDto  |
|----------------------|---------------------|
| `clientOrderId`      | clientOrderId       |
| `orderId`            | exchangeOrderId     |
| `symbol`             | symbol              |
| `side`               | side                |
| `status`             | status              |
| `executedQty`        | executedQty         |
| `cummulativeQuoteQty`| cumulativeQuoteQty  |
| `price`              | price               |
| `type`               | orderType           |
| `time`               | transactTime        |

**Mapeamento de status:**

| Binance              | Domínio interno |
|----------------------|-----------------|
| `NEW`                | PENDING / SUBMITTED |
| `PARTIALLY_FILLED`   | PARTIAL         |
| `FILLED`             | FILLED          |
| `CANCELED`           | CANCELED        |
| `REJECTED`           | REJECTED        |
| `EXPIRED`            | EXPIRED         |

**Tratamento de erros HTTP:**
- `404` em query de ordem → retornar `Optional.empty()` (não lançar exceção — ordem não encontrada é cenário normal no boot recovery)
- `400` em submit/cancel → lançar exceção com código de erro Binance preservado
- `429` (rate limit) → lançar exceção específica para que o caller possa aplicar backoff
- `5xx` → lançar exceção de falha transitória (o boot recovery já tem retry/backoff configurado)

**Restrições de implementação:**
- Não usar `RestTemplate` ou `WebClient` Spring — manter o adapter-binance independente de Spring; usar OkHttp3 diretamente.
- Timeout de conexão e leitura configuráveis via propriedades (padrão: 5s conexão, 10s leitura).
- Nenhuma lógica de negócio dentro do REST client — apenas HTTP + mapeamento.

---

## Critérios de aceitação

1. `queryOrderByClientOrderId` retorna `Optional.empty()` para ordem inexistente na testnet (HTTP 404), sem lançar exceção.
2. `queryOrderByClientOrderId` retorna `Optional<OrderDataDto>` populado para ordem existente, com todos os campos mapeados corretamente.
3. `submitOrder` envia a ordem para a testnet com assinatura válida e retorna o `OrderDataDto` com o `exchangeOrderId` atribuído pela Binance.
4. `queryAccountSnapshot` retorna saldos reais da conta testnet mapeados para `AccountDataDto`.
5. `listAllOpenOrders` retorna lista vazia quando não há ordens abertas (sem lançar exceção).
6. Falha de rede (timeout, connection refused) lança exceção de falha transitória, não derruba a aplicação.
7. Rate limit (HTTP 429) lança exceção identificável como transitória para que o boot recovery aplique backoff.
8. Nenhuma dependência de Spring dentro do módulo `adapter-binance`.
## Testes de integração obrigatórios

Usar MockWebServer (OkHttp3) ou WireMock para simular os endpoints da Binance. Verificar requisição enviada (headers, params, signature) e mapeamento da resposta.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| `queryOrderByClientOrderId` — ordem existe | `Optional<OrderDataDto>` com todos os campos mapeados corretamente; requisição contém `X-MBX-APIKEY` e `signature` |
| `queryOrderByClientOrderId` — ordem não existe (404) | Retorna `Optional.empty()` sem lançar exceção |
| `submitOrder` — sucesso | `OrderDataDto` retornado com `exchangeOrderId` atribuído; requisição POST assinada corretamente |
| `submitOrder` — HTTP 400 com código Binance | Exceção lançada com código de erro Binance preservado na mensagem |
| `queryAccountSnapshot` — sucesso | `AccountDataDto` com saldos mapeados corretamente para todos os assets retornados |
| `listAllOpenOrders` — lista vazia | Retorna lista vazia sem lançar exceção |
| Falha de rede (timeout) | Exceção de falha transitória lançada; aplicação não derruba |
| Rate limit (HTTP 429) | Exceção identificável como transitória lançada |
