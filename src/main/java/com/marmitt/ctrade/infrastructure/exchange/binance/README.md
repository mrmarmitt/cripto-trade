# Binance Exchange Integration

Este pacote contém toda a implementação específica para integração com a exchange Binance, fornecendo conexão real com dados de mercado em tempo real.

## Estrutura do Pacote

```
binance/
├── BinanceWebSocketAdapter.java     # Adapter principal para WebSocket da Binance
├── BinanceWebSocketListener.java    # Listener para eventos WebSocket da Binance
├── strategy/
│   ├── BinanceStreamProcessingStrategy.java  # Strategy para processamento de streams
│   └── processor/
│       └── TickerStreamProcessor.java        # Processador específico para ticker streams
├── dto/
│   └── BinanceTickerMessage.java    # DTO para mensagens de ticker da Binance
└── README.md                        # Este arquivo
```

## Componentes

### BinanceWebSocketAdapter
- **Responsabilidade**: Gerencia conexão WebSocket com a Binance usando OkHttp
- **Arquitetura**: Extende `AbstractWebSocketAdapter` seguindo padrões da infraestrutura
- **Features**: 
  - Exponential backoff com `ReconnectionStrategy`
  - Circuit breaker pattern com `WebSocketCircuitBreaker`
  - Connection management via `ConnectionManager`
  - Integration com Observer pattern
- **Ativação**: `websocket.exchange=BINANCE` via profile

### BinanceWebSocketListener  
- **Responsabilidade**: Processa eventos WebSocket (onOpen, onMessage, onClose, onFailure)
- **Arquitetura**: Extende `AbstractWebSocketListener` para consistência
- **Features**: 
  - Stream message parsing com strategy pattern
  - Error handling e callback management
  - Integration com `BinanceStreamProcessingStrategy`

### Stream Processing Strategy
- **BinanceStreamProcessingStrategy**: Coordena processamento de diferentes streams
- **TickerStreamProcessor**: Processa especificamente streams de ticker
  - **Flexibilidade**: Suporta streams individuais (`btcusdc@ticker`) e arrays (`!ticker@arr`)
  - **Auto-detection**: Detecta automaticamente formato da resposta
  - **Domain Integration**: Converte para `PriceUpdateMessage` do domínio

### BinanceTickerMessage (DTO)
- **Responsabilidade**: Mapeia mensagens JSON da Binance para objetos Java
- **Formato**: 24hrTicker stream format da Binance WebSocket API
- **Suporte**: Tanto mensagens únicas quanto arrays

## Configuração

### Profile Binance
```yaml
# application-binance.yml
websocket:
  exchange: BINANCE  # Ativa integração real com Binance
  url: wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/btcusdc@ticker/usdcusdt@ticker
  connection-timeout: 30s
  read-timeout: 10s
  max-retries: 10
  retry-interval: 5s
  auto-reconnect: true

logging:
  level:
    com.marmitt.ctrade.infrastructure.adapter.BinanceWebSocketAdapter: DEBUG
    com.marmitt.ctrade.infrastructure.websocket: DEBUG
```

### Ativação do Profile
- **IntelliJ**: VM options `-Dspring.profiles.active=binance`
- **Gradle**: `./gradlew bootRun --args='--spring.profiles.active=binance'`
- **Environment**: `SPRING_PROFILES_ACTIVE=binance`

## Stream Formats Suportados

### Individual Streams
```json
{
  "stream": "btcusdc@ticker",
  "data": {
    "e": "24hrTicker",
    "E": 1755529761436,
    "s": "BTCUSDC",
    "c": "115542.61000000",
    ...
  }
}
```

### Array Streams
```json
[
  {
    "e": "24hrTicker", 
    "E": 1755529761436,
    "s": "BTCUSDC",
    "c": "115542.61000000",
    ...
  }
]
```

## Exemplo de Uso

### Configuração Automática
```java
@Autowired
private ExchangeWebSocketAdapter webSocketAdapter; // Injeta BinanceWebSocketAdapter

// Profile binance → BinanceWebSocketAdapter carregado automaticamente
// Processamento via BinanceStreamProcessingStrategy
```

### Stream Processing
```java
// TickerStreamProcessor automaticamente detecta o formato
// e converte para PriceUpdateMessage
public Optional<PriceUpdateMessage> process(JsonNode data) {
    if (data.isArray()) {
        // Processa array de mensagens
    } else {
        // Processa mensagem única
    }
}
```

## URLs da Binance

### Mainnet
- **All Tickers Array**: `wss://stream.binance.com:9443/ws/!ticker@arr`
- **Individual Streams**: `wss://stream.binance.com:9443/stream?streams=btcusdt@ticker/btcusdc@ticker`
- **Combined Streams**: `wss://stream.binance.com:9443/stream?streams=<stream1>/<stream2>/<streamN>`

### Testnet
- **Base URL**: `wss://testnet.binance.vision/ws/!ticker@arr`
- **Stream URL**: `wss://testnet.binance.vision/stream?streams=<streams>`

## Resilience Features

### Exponential Backoff
- Reconexão automática com delay crescente
- Configurável via `retry-interval` e `max-retries`
- Reset automático após conexão bem-sucedida

### Circuit Breaker
- Prevenção de falhas cascata
- Monitoramento de health da conexão
- Fast-fail quando exchange indisponível

### Connection Management
- Pool de conexões centralizado
- Statistics de conexão em tempo real
- Health checks automáticos

## Monitoramento

### Health Check
```bash
GET /api/system/health
# Retorna status da conexão WebSocket com Binance
```

### Logs Estruturados
```log
DEBUG c.m.c.i.e.b.BinanceWebSocketAdapter - Connected to Binance WebSocket
DEBUG c.m.c.i.e.b.s.TickerStreamProcessor - Processing ticker message for BTCUSDC
```

## Extensibilidade

### Novos Stream Processors
```java
@Component
public class TradeStreamProcessor implements StreamProcessor<TradeUpdateMessage> {
    @Override
    public boolean canProcess(String streamName) {
        return streamName.endsWith("@trade");
    }
    
    @Override
    public Optional<TradeUpdateMessage> process(JsonNode data) {
        // Processar trades
    }
}
```

### Outras Exchanges
Para adicionar suporte a outras exchanges:
```
infrastructure/exchange/
├── coinbase/
│   ├── CoinbaseWebSocketAdapter.java
│   ├── strategy/
│   └── dto/
├── kraken/
└── binance/ (este pacote)
```

## Referências

- [Binance WebSocket API](https://binance-docs.github.io/apidocs/spot/en/#websocket-market-streams)
- [Stream Documentation](https://binance-docs.github.io/apidocs/spot/en/#symbol-ticker-streams)
- [Error Codes](https://binance-docs.github.io/apidocs/spot/en/#error-codes)