# Exemplo: Usando Feed Real no MockWebSocketAdapter

Este exemplo demonstra como configurar e usar o feed real para conectar a exchanges reais usando o MockWebSocketAdapter.

## Configuração

### 1. Configurar Feed Real para Binance

```yaml
# application-mock-real-feed.yml
mock:
  exchange:
    feed:
      # Desabilitar feeds simulados
      strict:
        enable: false
      random:
        enable: false
      # Habilitar feed real
      real:
        enable: true
        url: "wss://stream.binance.com:9443/ws/!ticker@arr"
        exchange: "BINANCE"

# Continuar usando MockWebSocketAdapter
websocket:
  exchange: MOCK
  url: "ws://mock.exchange.local"
  connection-timeout: 5000
  max-retries: 3

trading:
  pairs:
    active:
      - BTCUSDT
      - ETHUSDT
    stream-format: ticker
```

### 2. Executar com Feed Real

```bash
# Usar o profile de feed real
java -jar app.jar --spring.config.additional-location=application-mock-real-feed.yml
```

## Como Funciona

### Fluxo de Dados

1. **MockWebSocketAdapter** inicia normalmente
2. **FeedStrategyFactory** detecta configuração `real.enable: true`
3. **RealFeedStrategy** é criada e inicializada
4. **GenericWebSocketClient** conecta à URL real da Binance
5. **BinanceStreamProcessingStrategy** processa mensagens reais
6. **PriceUpdateMessage** são enviados para o sistema normalmente

### Arquitetura

```
MockWebSocketAdapter
    ↓
FeedStrategyFactory
    ↓
RealFeedStrategy
    ↓
GenericWebSocketClient ─────> Binance WebSocket
    ↓                              ↓
BinanceStreamProcessingStrategy ←──┘
    ↓
PriceUpdateMessage → Sistema
```

## Vantagens

### 🔄 **Reutilização de Código**
- Usa `BinanceStreamProcessingStrategy` existente
- Aproveita `WebSocketConnectionHandler` 
- Mantém a mesma interface do MockWebSocketAdapter

### 🔧 **Flexibilidade**
- Troca entre mock e real apenas mudando configuração
- Sem alteração de código na aplicação
- Mesma interface para desenvolvimento e testes

### 🚀 **Facilidade de Uso**
- Continua usando `websocket.exchange: MOCK`
- Aplicação não sabe que está usando dados reais
- Logs e métricas funcionam normalmente

## Logs Esperados

```
2024-01-20 10:30:15 INFO  MockWebSocketAdapter - Mock WebSocket connecting to: ws://mock.exchange.local
2024-01-20 10:30:15 INFO  FeedStrategyFactory - Creating REAL feed strategy for exchange: BINANCE
2024-01-20 10:30:15 INFO  RealFeedStrategy - Initialized feed strategy: REAL_BINANCE
2024-01-20 10:30:15 INFO  GenericWebSocketClient - Connecting to BINANCE WebSocket: wss://stream.binance.com:9443/ws/!ticker@arr
2024-01-20 10:30:16 INFO  WebSocketConnectionHandler - Connected to BINANCE WebSocket successfully
2024-01-20 10:30:16 DEBUG RealFeedStrategy - Received real price update for BTCUSDT: 43250.50
```

## Cenários de Uso

### 1. Desenvolvimento com Dados Reais
```yaml
# Para desenvolvimento usando preços reais da Binance
mock.exchange.feed.real.enable: true
```

### 2. Testes de Integração
```yaml  
# Para testes com dados reais mas sem risk
mock.exchange.feed.real.enable: true
mock.exchange.orders.acceptance-rate: 0.0  # Não executar ordens
```

### 3. Backtesting com Dados Live
```yaml
# Coletar dados reais para backtesting posterior
mock.exchange.feed.real.enable: true
# + lógica de coleta e armazenamento
```

## Extensões Possíveis

### Adicionar Outras Exchanges

```java
// No StreamProcessingStrategyRegistry
registerStrategy("COINBASE", new CoinbaseStreamProcessingStrategy(objectMapper));
```

```yaml
# Configuração para Coinbase
mock:
  exchange:
    feed:
      real:
        enable: true
        url: "wss://ws-feed.pro.coinbase.com"
        exchange: "COINBASE"
```

### Múltiplos Feeds Simultâneos

```java
// Possível extensão futura
public class MultiFeedStrategy implements FeedStrategy {
    // Agregar dados de múltiples exchanges
}
```

## Troubleshooting

### Erro de Conexão
```
ERROR RealFeedStrategy - Failed to connect to real exchange BINANCE
```
- Verificar conectividade de rede
- Confirmar URL da exchange
- Verificar se StreamProcessingStrategy está registrado

### Sem Mensagens
```
WARN RealFeedStrategy - Message queue full, dropping message
```
- Aumentar capacidade da queue
- Verificar se consumer está processando mensagens
- Confirmar rate de mensagens da exchange