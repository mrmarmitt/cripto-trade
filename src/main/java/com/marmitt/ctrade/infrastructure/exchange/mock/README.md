# Mock Exchange Integration

Este pacote contém toda a implementação de simulação para desenvolvimento e testes, fornecendo uma alternativa completa às integrações reais para facilitar o desenvolvimento local.

## Estrutura do Pacote

```
mock/
├── MockExchangeAdapter.java           # Adapter simulado para operações de trading
├── MockWebSocketAdapter.java          # Adapter simulado para WebSocket com estratégias de feed
├── strategy/
│   ├── FeedStrategy.java              # Interface para estratégias de feed
│   ├── FeedStrategyFactory.java       # Factory para criar estratégias
│   ├── StrictFeedStrategy.java        # Feed usando arquivos mock-data
│   ├── RandomFeedStrategy.java        # Feed com dados aleatórios
│   └── RealFeedStrategy.java          # Feed conectando a exchanges reais
├── service/
│   └── MockMarketDataLoader.java      # Carregador de dados mock
└── README.md                          # Este arquivo
```

## Componentes

### MockWebSocketAdapter
- **Responsabilidade**: Simula conexão WebSocket com múltiplas estratégias de feed
- **Features**: 
  - **Múltiplas estratégias de feed**: Strict, Random, Real
  - **Feed Strict**: Usa dados exatos dos arquivos mock-data
  - **Feed Random**: Gera dados aleatórios com volatilidade configurável
  - **Feed Real**: Conecta a exchanges reais (Binance, etc.)
  - **Arquitetura pluggável**: Troca de estratégia via configuração
  - **Reutilização de código**: Aproveita StreamProcessingStrategy existente
- **Arquitetura**: Extende `AbstractWebSocketAdapter` seguindo padrões da infraestrutura
- **Ativação**: `websocket.exchange=MOCK` (padrão)

### MockExchangeAdapter  
- **Responsabilidade**: Simula operações de trading (compra/venda)
- **Features**: 
  - Operações simuladas com validação
  - Simulação de diferentes cenários de mercado
  - Integração com sistema de auditoria

## Configuração

### Estratégias de Feed

#### 1. Feed Strict (Padrão)
Usa dados exatos dos arquivos mock-data sem randomização:
```yaml
# application-mock-strict.yml
mock:
  exchange:
    feed:
      strict:
        enable: true
        data-folder: "classpath:mock-data/"
        message-delay:
          price-updates: 1000     # timing específico para strict
          order-updates: 5000
          min-delay: 500
          max-delay: 1000
      random:
        enable: false
      real:
        enable: false
```

#### 2. Feed Random  
Gera dados aleatórios com volatilidade configurável:
```yaml
# application-mock-random.yml
mock:
  exchange:
    feed:
      strict:
        enable: false
      random:
        enable: true
        data-folder: "classpath:mock-data/"
        message-delay:
          price-updates: 800      # timing específico para random
          order-updates: 4000
        price-simulation:         # configuração específica para random
          volatility: 0.02
          trend-probability: 0.1
          trend-strength: 0.005
      real:
        enable: false
```

#### 3. Feed Real
Conecta a exchanges reais - sem configurações de timing artificial:
```yaml
# application-mock-real-feed.yml
mock:
  exchange:
    feed:
      strict:
        enable: false
      random:
        enable: false
      real:
        enable: true
        url: "wss://stream.binance.com:9443/ws/!ticker@arr"
        exchange: "BINANCE"
        # Sem message-delay ou price-simulation - usa dados reais
```

### WebSocket Configuration
A configuração `websocket` é genérica e usada independente do tipo de feed:
```yaml
websocket:
  exchange: MOCK                     # Sempre usa MockWebSocketAdapter
  url: "ws://mock.exchange.local"    # Placeholder (real URL está em real.url)
  connection-timeout: 5000
  max-retries: 3
```

## Simuladores Automáticos

O MockWebSocketAdapter inicia automaticamente simuladores para:
- **BTC/USD**: Preços variando entre $40,000-60,000 com flutuações realistas
- **ETH/USD**: Preços variando entre $2,500-3,500 com correlação ao BTC
- **Updates de Ordem**: Status simulados baseados em ordens ativas
- **Price Updates**: Notificações automáticas via Observer pattern

## Arquitetura

### Integração com Infrastructure
```java
// Herança da infraestrutura comum
public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
    // Implementação específica mock
}
```

### Observer Pattern
- Usa `PriceUpdateListener` para notificações automáticas
- Integração transparente com `WebSocketService`
- Cache automático via `PriceCacheService`

## Exemplo de Uso

```java
@Autowired
private ExchangeWebSocketAdapter webSocketAdapter; // Injeta MockWebSocketAdapter

// Simuladores iniciam automaticamente
webSocketAdapter.connect(); // Inicia simulação de preços

// Listeners são notificados automaticamente
// Cache é populado com preços simulados
```

## Vantagens para Desenvolvimento

### Produtividade
- **Desenvolvimento offline**: Sem dependências de APIs externas
- **Start rápido**: Sem configuração de credenciais ou conectividade
- **Dados consistentes**: Preços previsíveis para testes reprodutíveis

### Testing
- **Cenários controlados**: Simulação de diferentes condições de mercado
- **Logs detalhados**: Debug facilitado com logs estruturados
- **Performance**: Respostas instantâneas sem latência de rede

### Integração
- **API consistente**: Mesma interface que adaptadores reais
- **Switch transparente**: Mudança de profile sem alteração de código
- **Observer pattern**: Notificações automáticas como em produção

## Extensibilidade

### Adicionar Novos Pares
```java
// Em MockWebSocketAdapter.startSimulators()
simulators.put("ADA/USD", createPriceSimulator("ADA/USD", 0.5, 2.0));
```

### Novos Tipos de Simulação
```java
// Implementar novos simuladores seguindo o padrão
private Runnable createOrderStatusSimulator() {
    return () -> {
        // Lógica de simulação de status
        notifyOrderUpdate(orderUpdate);
    };
}
```

### Integração com Testes
```java
@TestConfiguration
public class MockTestConfig {
    @Bean
    @Primary
    public ExchangeWebSocketAdapter mockAdapter() {
        return new MockWebSocketAdapter(/* config para teste */);
    }
}
```