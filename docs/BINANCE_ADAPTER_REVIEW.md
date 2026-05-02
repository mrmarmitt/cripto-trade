# Revisão arquitetural — Binance Adapter

**Data:** 2026-04-30  
**Contexto:** Avaliação após conclusão de T2 (testnet profile) e T3 (HMAC signing).  
**Objetivo:** Identificar pontos que vão dificultar o desenvolvimento de T4–T5 antes que o custo de correção aumente.

---

## O que está bem

- Separação de módulos funciona: `adapter-binance` é Spring-free e testável isoladamente.
- `BinanceRequestSigner` é imutável, sem efeitos colaterais, responsabilidade única.
- `BinanceCredentials` como value object no módulo do adapter (sem Spring) com bridge no `BinanceAdapterConfiguration` é o padrão correto para cruzar o boundary.
- Chain of Responsibility nos processors de envio (`BinanceSenderMessageProcessor`) é idiomático.
- Ports no `core` estão bem separados: streaming, REST write, REST read, REST account, boot readiness.

---

## Problemas identificados

### ~~P1 — `BinanceExchangeAdapter` instancia colaboradores no construtor~~ ✅ Resolvido (PR #84)

**Situação atual:**
```java
public BinanceExchangeAdapter(ObjectMapper objectMapper, EventPublisherPort eventPublisher,
                              Configuration configuration, BinanceCredentials credentials) {
    this.webSocketPort = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
    this.receivedMessageProcessor = new BinanceReceivedMessageProcessor(objectMapper);
    this.senderMessageProcessor = new BinanceSenderMessageProcessor(objectMapper, new BinanceRequestSigner(credentials));
    this.urlBuilder = new BinanceUrlBuilder(configuration);
}
```

O adapter faz o trabalho que deveria ser do `BinanceAdapterConfiguration`. Todo colaborador é criado com `new` dentro do construtor.

**Consequência direta em T5 (REST):** o cliente HTTP vai ser instanciado aqui junto com o cliente WebSocket — dois clientes de transporte distintos no mesmo construtor. Quem quiser testar o REST não consegue sem subir o WebSocket junto.

**Correção:** o adapter deve receber os colaboradores já construídos; o `BinanceAdapterConfiguration` faz toda a montagem:

```java
// adapter recebe tudo pronto
public BinanceExchangeAdapter(WebSocketPort webSocketPort,
                              ReceivedMessageProcessorPort receivedMessageProcessor,
                              SenderMessageProcessorPort senderMessageProcessor,
                              ExchangeUrlBuilderPort urlBuilder) { ... }

// BinanceAdapterConfiguration monta tudo
@Bean
BinanceExchangeAdapter binanceExchangeAdapter(...) {
    var signer = new BinanceRequestSigner(new BinanceCredentials(...));
    var sender = new BinanceSenderMessageProcessor(objectMapper, signer);
    var receiver = new BinanceReceivedMessageProcessor(objectMapper);
    var ws = new OkHttp3WebSocketAdapter(new OkHttp3ListenerConverter(eventPublisher));
    var urlBuilder = new BinanceUrlBuilder(configuration);
    return new BinanceExchangeAdapter(ws, receiver, sender, urlBuilder);
}
```

**Quando corrigir:** antes de T5.

---

### P2 — `BinanceExchangeAdapter` implementa 5 ports num único bean

**Situação atual:**
```java
public class BinanceExchangeAdapter implements
    ExchangeStreamingPort,
    ExchangeOrderExecutionPort,
    ExchangeOrderQueryPort,
    ExchangeAccountQueryPort,
    ExchangeBootReadinessPort { ... }
```

Hoje 4 desses ports têm `throw new UnsupportedOperationException`. Quando T4 e T5 chegarem, este bean vai acumular um cliente WebSocket (market data), um cliente WebSocket (user data stream), um cliente HTTP REST e lógica de boot readiness. Isso é a God class clássica.

**Separação natural:**
- `BinanceStreamingAdapter` → `ExchangeStreamingPort`
- `BinanceRestAdapter` → `ExchangeOrderExecutionPort` + `ExchangeOrderQueryPort` + `ExchangeAccountQueryPort`
- Boot readiness pode ser delegada a quem já conhece o estado de ambos

**Quando corrigir:** T5 é o ponto natural de split. Chegar a T5 sem dividir torna o problema caro.

---

### ~~P3 — `TickerProcessor.canProcess()` vai colidir com T4 (User Data Stream)~~ ✅ Resolvido (PR #85)

**Situação atual:**
```java
return json.has("s") && json.has("c") && (json.has("e") || json.has("P") || json.has("v"));
```

O User Data Stream (T4) também envia eventos com o campo `e` (event type). Um `executionReport` tem `s` (symbol), `e` ("executionReport") e outros campos que passam nesse heurístico. O `TickerProcessor` vai aceitar eventos de execução como se fossem tickers e quebrar o parsing silenciosamente.

**Discriminador correto** é o valor de `e`, não a presença do campo:
```java
return json.has("e") && "24hrTicker".equals(json.get("e").asText());
```

**Quando corrigir:** antes de T4, não durante.

---

### P4 — Duplicação de `buildStreamName()` em dois lugares

`BinanceUrlBuilder` e `StreamProcessor` têm o método idêntico:

```java
private String buildStreamName(CurrencyPair pair) {
    String lowerSymbol = (pair.baseCurrency() + pair.quoteCurrency()).toLowerCase();
    return switch (pair.streamType()) {
        case TICKER -> lowerSymbol + "@ticker";
        case TRADE  -> lowerSymbol + "@trade";
        // ...
    };
}
```

Se um novo `StreamType` for adicionado ao core, é fácil atualizar um e esquecer o outro. A lógica deve viver em um único lugar — candidato natural é o próprio `BinanceUrlBuilder`.

**Quando corrigir:** T4 ou oportunístico.

---

### P5 — `SenderSpecializedProcessorPort` está no `core`

**Situação atual:**
```
core/ports/outbound/exchange/adapter/SenderSpecializedProcessorPort.java
```

Este é um detalhe de implementação interno do adapter. O `core` não deveria saber que adapters têm "specialized processors" — isso é organização interna do `adapter-binance`. Quando a Coinbase implementar seu sender, ela provavelmente não vai usar esse padrão e a interface no core vai ser ruído.

**Correção:** mover para `adapter-binance` como interface interna de pacote.

**Quando corrigir:** próximo refactor de adapter.

---

### ~~P6 — `Configuration.java` tem nome genérico demais~~ ✅ Resolvido (PR #84)

No módulo `adapter-binance` o nome é OK, mas ao ser referenciado do Spring é necessário o nome qualificado:

```java
com.marmitt.binance.Configuration configuration = new com.marmitt.binance.Configuration(...)
```

O nome qualificado no código de produção é um smell de nomenclatura. `BinanceEndpointConfig` eliminaria a ambiguidade sem precisar de qualificação.

**Quando corrigir:** oportunístico (renomear junto com P1 ou P2).

---

### P7 — `@NotBlank` em `wsBaseUrl` e `restBaseUrl` de `BinanceProperties` nunca dispara

```java
@NotBlank(message = "binance.ws-base-url cannot be blank")
private String wsBaseUrl;
```

Essas propriedades sempre têm defaults hardcoded em `application.yml` e só são avaliadas quando o bean Binance é criado (credenciais presentes). A anotação nunca vai disparar na prática — é ruído que sugere uma invariante que não existe.

**Quando corrigir:** oportunístico.

---

### ~~P8 — `TickerProcessor` mistura regra de negócio com validação técnica~~ ✅ Resolvido (PR #85)

```java
if (marketData.price().compareTo(new BigDecimal("10000000")) > 0) {
    return false;
}
```

O threshold de $10M como "preço suspeito" é uma regra de negócio masquerando como validação de parsing. Também usa `BigDecimal.ROUND_HALF_UP` (deprecated — deveria ser `RoundingMode.HALF_UP`).

**Quando corrigir:** oportunístico (ao implementar recepção de eventos em T4).

---

### P9 — `BinanceReceivedMessageProcessor` captura `Exception` genérico

```java
} catch (Exception e) {
    return ProcessingResult.error(...)
}
```

Erros de infraestrutura (`OutOfMemoryError` envolvido em `RuntimeException`) seriam engolidos como erros de processamento. Melhor capturar exceções específicas (`JsonProcessingException`, `IllegalStateException`) e deixar as demais propagar.

**Quando corrigir:** oportunístico (ao adicionar processors em T4).

---

## Prioridades

| # | Item | Impacto direto | Quando | Status |
|---|------|---------------|--------|--------|
| P1 | Construtor do adapter instancia colaboradores | Quebra testabilidade de T5 | Antes de T5 | ✅ PR #84 |
| P3 | `TickerProcessor.canProcess()` heurístico | Quebra T4 | Antes de T4 | ✅ PR #85 |
| P6 | Rename de `Configuration` | Legibilidade | Oportunístico | ✅ PR #84 |
| P8 | Regra de negócio em `isValidMarketData` | Manutenção | Oportunístico | ✅ PR #85 |
| P2 | God class — 5 ports num bean | Acumula débito em T4+T5 | Ao iniciar T5 | 🔲 Pendente |
| P4 | Duplicação de `buildStreamName` | Risco de divergência | T4 ou oportunístico | ✅ PR #86 |
| P5 | `SenderSpecializedProcessorPort` no core | Arquitetura | Próximo refactor | 🔲 Pendente |
| P7 | `@NotBlank` em URLs | Ruído | Oportunístico | 🔲 Pendente |
| P9 | `catch (Exception)` genérico | Resiliência | Oportunístico | 🔲 Pendente |
