# Binance User Data Stream

## Objetivo

Conectar o User Data Stream da Binance via WebSocket API, autenticar por mensagem
assinada (HMAC-SHA256) e entregar eventos privados de ordem como `OrderDataDto`
ao fluxo de conciliacao.

Este fluxo e a entrada real de callbacks de execucao da Binance. Ele nao deve
decidir regra de dominio; o adapter traduz payload externo e o core aplica a
conciliacao.

**Nota:** A Binance removeu os endpoints REST de listen key (`/api/v3/userDataStream`)
em 2026-02-04. O protocolo atual usa WebSocket API com autenticacao por mensagem
assinada — sem listen key, sem keepalive REST, sem revogacao.

## Quando Consultar

- Bugs em callbacks de ordem Binance.
- Mudancas em autenticacao WebSocket API, subscription ou reconexao.
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
5. `BinanceUserStreamSession.open()` retorna `wsApiBaseUrl` diretamente (sem
   chamada REST — nao ha mais listen key).
6. O use case conecta o `WebSocketPort` de user stream em `wsApiBaseUrl`.
7. Apos WebSocket abrir, `WebSocketConnectedEvent` e publicado.
8. `PostConnectionEstablishHandler` detecta canal `USER_DATA`, recupera a sessao
   ativa via `adapterRepository.findActiveSession(connectionId)`, chama
   `session.subscriptionMessage()` e envia a mensagem assinada via WebSocket.
9. A mensagem de subscricao usa `userDataStream.subscribe.signature` com
   parametros assinados via HMAC-SHA256 (`BinanceRequestSigner.signWebSocketParams`).
10. A Binance responde com `{"status": 200, "result": {"subscriptionId": N}}`.
11. `BinanceUserDataProcessor` identifica a confirmacao e loga; nao notifica listeners.
12. Eventos de ordem chegam no formato `{"subscriptionId": N, "event": {...}}`.
13. `BinanceUserDataProcessor` extrai `event`, roteia pelo campo `e`.
14. `ExecutionReportProcessor` converte `executionReport` em `OrderDataDto`.
15. O handler notifica listeners de ordem registrados.
16. `PortfolioStrategyRunnerOrderUpdateListener` chama `OrderConciliationPort`.

## Autenticacao WebSocket API

`BinanceUserStreamSession.subscriptionMessage()` gera on-demand a mensagem de
subscricao assinada:

1. Chama `BinanceRequestSigner.signWebSocketParams({"recvWindow": 5000})`.
2. O signer adiciona `apiKey`, `timestamp` e `signature` (HMAC-SHA256) em ordem
   alfabetica.
3. Serializa `{"id": "<uuid>", "method": "userDataStream.subscribe.signature", "params": {...}}`.
4. Retorna `Optional.of(json)`.

A mensagem e enviada via `PostConnectionEstablishHandler` apos cada conexao
(incluindo reconexoes), garantindo timestamp fresco dentro do `recvWindow`.

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
3. para `USER_DATA`, fecha a sessao ativa anterior (`session.close()` e no-op);
4. remove a sessao antiga do repositorio;
5. chama `ConnectUserStreamPort` novamente;
6. apos nova conexao, `PostConnectionEstablishHandler` envia nova subscription
   message assinada com timestamp fresco.

Fechamento normal por `WebSocketClosedEvent` tambem fecha a sessao ativa quando
a conexao nao estava em processo de disconnect manual.

## Variacoes E Falhas

- Sem credenciais Binance: `BinanceUserStreamConfiguration` nao cria os beans.
- Sem adapters de user stream: `ConnectUserStreamUseCase` retorna vazio.
- Falha ao gerar subscription message (ex: erro de signing): `subscriptionMessage()`
  retorna `Optional.empty()`, handler loga e retorna sucesso sem enviar.
- Confirmacao com status != 200: `BinanceUserDataProcessor` loga erro e retorna
  `ProcessingResult.error()` — sem notificacao de listener.
- Confirmacao com status 200: loga info, retorna `ProcessingResult.error()` —
  nao e um evento processavel, listeners nao sao notificados.
- Mensagem sem campo `e` (nem subscriptionId/event, nem status/id): erro.
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
| `core/src/main/java/com/marmitt/core/application/handler/connection/PostConnectionEstablishHandler.java` | Envia subscription message apos conexao USER_DATA. |
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
- `subscriptionMessage()` deve ser gerado on-demand (timestamp fresco por chamada).
- Reconnect de user stream fecha a sessao anterior antes de abrir nova sessao.
- Apenas `OrderDataDto` deve ser notificado aos `OrderUpdateListener` no user
  stream; confirmacoes de subscricao nao sao processaveis.
- `wsApiBaseUrl` nao vaza para o core; `PostConnectionEstablishHandler` nao
  conhece detalhes de provider.
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
