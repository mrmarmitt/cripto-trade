# Binance Exchange Integration

Este pacote contém toda a implementação específica para integração com a exchange Binance.

## Estrutura do Pacote

```
binance/
├── BinanceWebSocketAdapter.java     # Adapter principal para WebSocket da Binance
├── BinanceWebSocketListener.java    # Listener para eventos WebSocket da Binance  
├── dto/
│   └── BinanceTickerMessage.java    # DTO para mensagens de ticker da Binance
└── README.md                        # Este arquivo
```

## Componentes

### BinanceWebSocketAdapter
- **Responsabilidade**: Gerencia conexão WebSocket com a Binance
- **Features**: Exponential backoff, circuit breaker, connection stats
- **Ativação**: `websocket.exchange=BINANCE`

### BinanceWebSocketListener  
- **Responsabilidade**: Processa eventos WebSocket (onOpen, onMessage, onClose, onFailure)
- **Features**: Message parsing, error handling, callback management

### BinanceTickerMessage (DTO)
- **Responsabilidade**: Mapeia mensagens JSON da Binance para objetos Java
- **Formato**: 24hrTicker stream format da Binance WebSocket API

## Configuração

```yaml
websocket:
  exchange: BINANCE  # Ativa integração real com Binance
  url: wss://stream.binance.com:9443/ws/!ticker@arr
```

## Exemplo de Uso

```java
@Autowired
private BinanceWebSocketAdapter binanceAdapter;

// O adapter é automaticamente ativado quando websocket.exchange=BINANCE
// e injeta automaticamente no WebSocketService
```

## URLs da Binance

- **Mainnet**: `wss://stream.binance.com:9443/ws/!ticker@arr`
- **Testnet**: `wss://testnet.binance.vision/ws/!ticker@arr` 

## Futuras Extensões

Para adicionar suporte a outras exchanges, siga este padrão:
- `infrastructure/exchange/coinbase/`
- `infrastructure/exchange/kraken/`
- etc.