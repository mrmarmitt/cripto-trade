# Binance User Data Stream

## Objetivo

Conectar o User Data Stream da Binance, manter o `listenKey` ativo, transformar
eventos privados de ordem em `OrderDataDto` e entregar esses updates ao fluxo de
conciliacao de ordens.

Este fluxo e a entrada real de callbacks de execucao da Binance. Ele nao deve
decidir regra de dominio; o adapter traduz payload externo e o core aplica a
conciliacao.

## Quando Consultar

- Bugs em callbacks de ordem Binance.
- Mudancas em `listenKey`, keepalive, revoke ou reconexao de User Data Stream.
- Alteracoes no parsing de `executionReport`.
- Problemas em status `NEW`, `PARTIALLY_FILLED`, `FILLED`, `CANCELED`,
  `EXPIRED` ou `REJECTED` vindos da Binance.
- Review de mudancas que conectam Binance ao fluxo de conciliacao.

## Entradas E Saidas

- Entrada de conexao: `ConnectUserStreamUseCase.execute("BINANCE")`.
- Entrada raw: mensagem WebSocket privada da Binance.
- Payload processavel: evento `executionReport`.
- Saida interna: `OrderDataDto`.
- Efeito colateral: notificacao de `OrderUpdateListener`, que chama
  `OrderConciliationPort`.

## Fluxo Principal

1. `BinanceUserStreamConfiguration` registra os beans de User Data Stream quando
   `binance.api-key` e `binance.api-secret` nao estao em branco.
2. `ConnectUserStreamUseCase` valida se ha `UserStreamSessionPort` e
   `ExchangeUserStreamPort` para a exchange.
3. O use case registra/recupera a conexao `ConnectionKey.userStream("BINANCE")`.
4. `BinanceUserStreamSessionAdapter` cria uma `BinanceUserStreamSession`.
5. `BinanceUserStreamSession.open()` pede o `listenKey` via REST.
6. A sessao agenda keepalive a cada 30 minutos.
7. O use case conecta o `WebSocketPort` de user stream no URL
   `wsBaseUrl + "/ws/" + listenKey`.
8. `OkHttp3ListenerConverter` publica `RawUserDataMessageReceivedEvent` para
   mensagens recebidas no canal `USER_DATA`.
9. `ProcessUserMessageEventListener` chama `HandlerProcessUserMessagePort`.
10. `ProcessUserMessageHandler` usa `ExchangeUserStreamPort` para processar a
    mensagem raw.
11. `BinanceUserDataProcessor` roteia pelo campo `e`.
12. `ExecutionReportProcessor` converte `executionReport` em `OrderDataDto`.
13. O handler notifica listeners de ordem registrados.
14. `PortfolioStrategyRunnerOrderUpdateListener` chama `OrderConciliationPort`.

## Listen Key

`ListenKeyManager` encapsula o ciclo REST do listen key:

- `POST /api/v3/userDataStream`: obtem `listenKey`.
- `PUT /api/v3/userDataStream?listenKey={listenKey}`: keepalive.
- `DELETE /api/v3/userDataStream?listenKey={listenKey}`: revoke.

Todas as chamadas usam header `X-MBX-APIKEY`.

## Parsing De executionReport

`ExecutionReportProcessor` le campos Binance e cria `OrderDataDto`:

- `i` -> `orderId`
- `c` -> `clientOrderId`
- `s` -> `Symbol`
- `S` -> side
- `o` -> type
- `X` -> status
- `q` -> quantidade total
- `z` -> quantidade executada acumulada
- `Z` -> quote acumulado
- `p` -> preco informado
- `n` -> fee
- `r` -> reject reason
- `T` -> timestamp

O preco executado e calculado como `Z / z` quando `z > 0`; caso contrario fica
zero.

## Reconexao

Falhas WebSocket publicam `WebSocketFailedEvent`. `ConnectionFailedHandler`:

1. marca a conexao como falha;
2. agenda reconnect com backoff linear de 5 segundos por tentativa;
3. para `USER_DATA`, fecha a sessao ativa anterior;
4. remove a sessao antiga do repositorio;
5. chama `ConnectUserStreamPort` novamente;
6. a nova conexao obtem novo `listenKey`.

Fechamento normal por `WebSocketClosedEvent` tambem fecha/revoga a sessao ativa
quando a conexao nao estava em processo de disconnect manual.

## Variacoes E Falhas

- Sem credenciais Binance: `BinanceUserStreamConfiguration` nao cria os beans.
- Sem adapters de user stream: `ConnectUserStreamUseCase` retorna vazio.
- Falha ao obter listen key: a sessao e fechada e o manager recebe status de
  falha.
- Mensagem sem campo `e`: resultado de erro, sem notificacao de listener.
- Evento `e` sem processor registrado: resultado de erro.
- `executionReport` com side/type/status desconhecido: erro de parse.
- `clientOrderId` desconhecido ainda passa pelo listener, mas a conciliacao deve
  descartar sem efeito economico.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/BinanceUserStreamConfiguration.java` | Configura WebSocket de user stream, session adapter e processor Binance. |
| `core/src/main/java/com/marmitt/core/application/usecase/websocket/ConnectUserStreamUseCase.java` | Orquestra abertura de conexao privada por exchange. |
| `adapter-binance/src/main/java/com/marmitt/binance/BinanceUserStreamSessionAdapter.java` | Cria sessoes Binance por conexao. |
| `adapter-binance/src/main/java/com/marmitt/binance/userdata/BinanceUserStreamSession.java` | Abre URL privada e agenda keepalive. |
| `adapter-binance/src/main/java/com/marmitt/binance/userdata/ListenKeyManager.java` | Obtem, renova e revoga listen key. |
| `spring-application/src/main/java/com/marmitt/application/spring/adapter/OkHttp3ListenerConverter.java` | Publica eventos raw por canal WebSocket. |
| `spring-application/src/main/java/com/marmitt/application/spring/handler/ProcessUserMessageEventListener.java` | Consome evento raw de user data de forma async. |
| `core/src/main/java/com/marmitt/core/application/handler/ProcessUserMessageHandler.java` | Processa payload privado e notifica listeners de ordem. |
| `adapter-binance/src/main/java/com/marmitt/binance/BinanceUserStreamAdapter.java` | Adapter de processamento do user stream Binance. |
| `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/BinanceUserDataProcessor.java` | Roteia eventos privados Binance por `e`. |
| `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/ExecutionReportProcessor.java` | Converte `executionReport` em `OrderDataDto`. |
| `core/src/main/java/com/marmitt/core/application/handler/connection/ConnectionFailedHandler.java` | Agenda reconnect e renova sessao privada. |
| `core/src/main/java/com/marmitt/core/application/listener/runner/PortfolioStrategyRunnerOrderUpdateListener.java` | Entrega updates de ordem ao fluxo de conciliacao. |

## Invariantes

- Payload Binance nao deve vazar para o dominio; a borda deve produzir
  `OrderDataDto`.
- User stream privado usa `ConnectionKey.userStream`, separado do market stream.
- Keepalive pertence a sessao Binance, nao ao core.
- Reconnect de user stream precisa fechar/revogar a sessao anterior antes de
  abrir nova sessao.
- Apenas `OrderDataDto` deve ser notificado aos `OrderUpdateListener` no user
  stream.
- O efeito economico final pertence a `order-conciliation.md`, nao ao adapter.

## Validacao

- `spring-application/src/test/java/com/marmitt/application/spring/userdata/UserDataStreamConciliationIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/userdata/BinanceListenKeyReconnectIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/config/exchange/BinanceAdapterConfigurationIntegrationTest.java`
- Comando recomendado:
  `./scripts/gradle-run.ps1 -q :adapter-binance:test :spring-application:test`

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md`, secoes 10 e 15.
- `/.ai/flows/order-conciliation.md`.
- `adapter-binance/src/main/java/com/marmitt/binance/userdata/`.
- `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/`.
- `core/src/main/java/com/marmitt/core/application/usecase/websocket/ConnectUserStreamUseCase.java`.
- `core/src/main/java/com/marmitt/core/application/handler/ProcessUserMessageHandler.java`.
