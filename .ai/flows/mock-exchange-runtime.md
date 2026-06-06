# Mock Exchange Runtime

## Objetivo

Descrever como o adapter mock simula conexao local, market data, execucao de
ordens, cenarios deterministas, consultas REST-like e eventos raw para usar o
mesmo pipeline de uma exchange real.

O mock existe para desenvolvimento local, testes de fluxo e cenarios
controlados. Ele deve continuar usando os mesmos contratos internos que os
adapters reais usam.

## Quando Consultar

- Bugs em testes ou desenvolvimento com exchange `MOCK`.
- Mudancas em feed simulado, slippage, fees, partial fills ou eventos duplicados.
- Alteracoes em overrides deterministas por `clientOrderId`.
- Problemas em boot/recovery usando snapshots mock.
- Review de mudancas em `MockExchangeAdapter` ou `MockExchangeRuntime`.

## Entradas E Saidas

- Entrada de stream: `StreamSubscriptionRequest`.
- Entrada de ordem: `SendOrderRequest` ou `SendCancelOrderRequest`.
- Entrada de consulta: `queryOrderByClientOrderId`, `queryOrderByExchangeOrderId`,
  `listOpenOrders*`, `queryAccountSnapshot`.
- Saida de ordem: `OrderDataDto`.
- Saida de mercado: `MarketDataDto`.
- Efeitos colaterais: publicacao de `RawMarketMessageReceivedEvent` para entrar
  no mesmo roteamento WebSocket/eventos usado por exchanges reais.

## Fluxo De Configuracao

1. `MockAdapterConfiguration` cria `LocalEventWebSocketAdapter` e registra a
   porta WebSocket `MOCK`.
2. A mesma config cria `MockExchangeAdapter`.
3. `InMemoryExchangeAdapterRepository` registra as capacidades implementadas:
   streaming, execucao de ordem, consulta de ordem, consulta de conta e boot
   readiness.
4. `MockExchangeAdapter` cria `MockExchangeRuntime`, `MockReceivedMessageProcessor`
   e `MockSenderMessageProcessor`.

## Conexao Local

`LocalEventWebSocketAdapter` nao abre socket real. Ele:

- marca conexao como ativa;
- publica `WebSocketConnectedEvent` com canal `MARKET`;
- publica `WebSocketDisconnectedEvent` em disconnect manual;
- ignora `sendMessage`, porque o runtime mock ja processa comandos localmente.

Isso permite que o mesmo fluxo post-connection usado por exchanges reais seja
executado para `MOCK`.

## Feed De Market Data

1. `ConnectMarketStreamUseCase` conecta `MOCK`.
2. `LocalEventWebSocketAdapter` publica conexao estabelecida.
3. `PostConnectionEstablishHandler` envia a subscription salva no historico.
4. `MockSenderMessageProcessor` recebe `StreamSubscriptionRequest`.
5. `MockExchangeRuntime.handleStream` chama `MockMarketDataFeedEngine`.
6. O feed cria tarefas por simbolo e publica ticks periodicos.
7. Cada tick vira `MarketDataDto`.
8. `MockRawMessagePublisher` serializa o DTO e publica
   `RawMarketMessageReceivedEvent`.
9. O fluxo segue por `ProcessMessageHandler` e listeners de preco.

## Execucao De Ordem

1. Chamadas sincronas REST-like usam `MockExchangeAdapter.submitOrder`.
2. O adapter delega para `MockExchangeRuntime.submitOrderRest`.
3. O runtime gera `orderId` e salva o mapeamento por `clientOrderId`.
4. `MockOrderExecutionSimulator.simulateAccepted` cria snapshot `NEW`.
5. O runtime agenda simulacao assincrona de eventos.
6. `MockOrderExecutionSimulator.buildScenarioSchedule` gera sequencia de updates.
7. Cada `MockScheduledOrderEvent` e publicado via `MockRawMessagePublisher`.
8. `MockReceivedMessageProcessor` desserializa o raw em `OrderDataDto`.
9. `ProcessMessageHandler` notifica `OrderUpdateListener`.
10. `PortfolioStrategyRunnerOrderUpdateListener` chama conciliacao.

## Cenarios E Overrides

`MockScenarioConfig.defaultConfig()` define comportamento aleatorio controlado:

- seed fixa;
- latencia entre 100ms e 400ms;
- 2 partial fills;
- chance de duplicatas;
- chance de out-of-order;
- chance de cancelamento/expiracao;
- fee em mesma moeda;
- slippage para market orders;
- validacao basica de quantidade, step size e notional;
- saldos iniciais.

`MockOrderScenarioOverride` permite cenarios deterministas por `clientOrderId`:

- lista ordenada de eventos planejados;
- status, quantidade executada, preco, fee e reject reason por evento;
- delay por evento;
- duplicatas por evento;
- ordem normal ou reversa.

O override e consumido uma vez quando a ordem correspondente e submetida.

## Consultas E Boot Recovery

O runtime mantem:

- `orderIdByClientOrderId`;
- `latestEventByOrderId`;
- planos de falha de consulta por `clientOrderId`;
- snapshots sem publicacao de callbacks.

Isso suporta:

- `queryOrderByClientOrderId`;
- `queryOrderByExchangeOrderId`;
- `listOpenOrdersBySymbol`;
- `listAllOpenOrders`;
- `seedQueriedOrderSnapshot`;
- `registerQueryFailurePlan`.

Esses recursos sao usados por testes de boot/recovery e watchdog sem depender de
exchange real.

## Variacoes E Falhas

- Runtime parado: submissao retorna snapshot `REJECTED` com
  `MOCK_LIFECYCLE_STOPPED`; consultas lancam `ExchangeQueryException`.
- Cancel de ordem desconhecida: retorna `REJECTED` com `ORDER_NOT_FOUND`.
- Cancel de ordem terminal: retorna o snapshot terminal atual.
- Ordem invalida por regra de quantidade/notional/saldo: simulador emite
  `REJECTED`.
- Eventos duplicados e fora de ordem sao intencionais e devem ser tratados pelo
  fluxo de conciliacao.
- Feed desabilitado ou sem pares: subscription nao gera ticks.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/MockAdapterConfiguration.java` | Registra WebSocket local e adapter mock. |
| `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/MockExchangeAdapter.java` | Expoe capacidades mock para streaming, ordem, query, conta e readiness. |
| `adapter-mock/src/main/java/com/marmitt/mock/adapter/LocalEventWebSocketAdapter.java` | Simula ciclo de conexao sem socket real. |
| `adapter-mock/src/main/java/com/marmitt/mock/runtime/MockExchangeRuntime.java` | Orquestra feed, ordens, snapshots, query failures e lifecycle. |
| `adapter-mock/src/main/java/com/marmitt/mock/processor/MockSenderMessageProcessor.java` | Processa comandos enviados ao mock. |
| `adapter-mock/src/main/java/com/marmitt/mock/processor/MockReceivedMessageProcessor.java` | Converte payload raw mock em `MarketDataDto` ou `OrderDataDto`. |
| `adapter-mock/src/main/java/com/marmitt/mock/processor/MockRawMessagePublisher.java` | Publica payloads mock no pipeline raw. |
| `adapter-mock/src/main/java/com/marmitt/mock/simulator/MockMarketDataFeedEngine.java` | Gera ticks sinteticos de mercado. |
| `adapter-mock/src/main/java/com/marmitt/mock/simulator/MockOrderExecutionSimulator.java` | Gera lifecycle de ordens, fills, falhas e duplicatas. |
| `adapter-mock/src/main/java/com/marmitt/mock/config/MockScenarioConfig.java` | Configuracao aleatoria controlada do simulador. |
| `adapter-mock/src/main/java/com/marmitt/mock/config/MockOrderScenarioOverride.java` | Cenarios deterministas por ordem. |
| `adapter-mock/src/main/java/com/marmitt/mock/simulator/FeeModel.java` | Calcula fee por incremento executado. |
| `adapter-mock/src/main/java/com/marmitt/mock/simulator/SlippageModel.java` | Calcula preco executado com slippage. |

## Invariantes

- O mock deve publicar eventos pelo mesmo pipeline raw usado por exchanges reais.
- Eventos mock de ordem precisam carregar `clientOrderId` para conciliacao.
- Duplicatas e out-of-order sao cenarios validos; o core deve preservar
  idempotencia.
- Overrides deterministas devem ser consumidos uma vez por `clientOrderId`.
- Snapshots seedados para query nao devem publicar callbacks automaticamente.
- `LocalEventWebSocketAdapter` nao deve abrir rede real.
- O mock nao deve introduzir regra de dominio; ele simula exchange e traduz para
  DTOs internos.

## Validacao

- `adapter-mock/src/test/java/com/marmitt/mock/simulator/MockOrderExecutionSimulatorDeterministicScenarioTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/ProcessTradeSignalMockIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/OrderLifecycleMockIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/MockBuyOrderOverrideIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/MockSellOrderOverrideIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/MockTerminalOrderOverrideIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/MockTerminalMonotonicityStabilityIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/MockTransientOrderFailureIntegrationTest.java`
- Comando recomendado:
  `./scripts/gradle-run.ps1 -q :adapter-mock:test :spring-application:test`

## Fontes

- `/.ai/flows/websocket-event-routing.md`.
- `/.ai/flows/order-conciliation.md`.
- `adapter-mock/src/main/java/com/marmitt/mock/`.
- `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/MockExchangeAdapter.java`.
- `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/MockAdapterConfiguration.java`.
