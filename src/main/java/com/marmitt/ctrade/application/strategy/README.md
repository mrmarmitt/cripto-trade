# Sistema Modular de Estratégias de Trading

Este documento descreve o sistema modular de estratégias implementado para o CTrace, seguindo a arquitetura hexagonal do projeto.

## Visão Geral

O sistema permite criar, registrar e executar múltiplas estratégias de trading de forma modular e configurável. Cada estratégia implementa a interface `TradingStrategy` e pode ser habilitada/desabilitada dinamicamente.

## Componentes Principais

### 1. Interface TradingStrategy
```java
public interface TradingStrategy {
    StrategySignal analyze(MarketData marketData, Portfolio portfolio);
    String getStrategyName();
    Map<String, Object> getConfiguration();
    boolean isEnabled();
    void setEnabled(boolean enabled);
}
```

### 2. StrategySignal
Value object que representa um sinal de trading:
- `SignalType`: BUY, SELL ou HOLD
- `TradingPair`: Par de moedas para a operação
- `BigDecimal quantity`: Quantidade a ser negociada
- `BigDecimal price`: Preço da operação
- `String reason`: Justificativa do sinal
- `String strategyName`: Nome da estratégia que gerou o sinal

### 3. StrategyRegistry
Gerencia o registro e controle das estratégias:
- Registrar/desregistrar estratégias
- Listar estratégias ativas
- Habilitar/desabilitar estratégias
- Contadores de estratégias

### 4. TradingOrchestrator
Coordena a execução das estratégias:
- Executa todas as estratégias ativas
- Processa sinais gerados
- Valida sinais antes de criar ordens
- Atualiza portfolio após ordens
- Execução assíncrona para performance

## Estratégias Implementadas

### PairTradingStrategy
Estratégia de pair trading que:
- Monitora dois ativos correlacionados
- Calcula spread e z-score histórico
- Gera sinais quando spread diverge significativamente
- Configura thresholds e histórico dinamicamente

**Parâmetros:**
- `pair1`: Primeiro ativo do par (ex: "BTC/USDT")
- `pair2`: Segundo ativo do par (ex: "ETH/USDT")
- `upperThreshold`: Threshold superior para z-score (padrão: 2.0)
- `lowerThreshold`: Threshold inferior para z-score (padrão: -2.0)
- `maxHistorySize`: Tamanho máximo do histórico (padrão: 50)
- `tradingAmount`: Valor base para trading (padrão: 100.0)

## Configuração

### application.yml
```yaml
trading:
  strategies:
    auto-register: true
    max-concurrent-strategies: 10
    strategies:
      pairtradingstrategy:
        enabled: false
        priority: 1
        max-order-value: 1000
        min-order-value: 10
        parameters:
          pair1: "BTC/USDT"
          pair2: "ETH/USDT"
          upperThreshold: 2.0
          lowerThreshold: -2.0
          maxHistorySize: 50
          tradingAmount: 100.0
```

### Auto-registro
O sistema possui auto-registro de estratégias via:
1. Beans Spring detectados automaticamente
2. Configurações no application.yml
3. Registro dinâmico via API

## Como Criar uma Nova Estratégia

### 1. Implementar TradingStrategy
```java
@Component
public class MinhaEstrategia extends AbstractTradingStrategy {
    
    public MinhaEstrategia() {
        super("MinhaEstrategia");
    }
    
    @Override
    public StrategySignal analyze(MarketData marketData, Portfolio portfolio) {
        // Lógica da estratégia
        if (/* condição de compra */) {
            return StrategySignal.buy(pair, quantity, price, reason, getStrategyName());
        }
        return StrategySignal.hold(getStrategyName());
    }
}
```

### 2. Configurar no application.yml
```yaml
trading:
  strategies:
    strategies:
      minhaestrategia:
        enabled: true
        parameters:
          parametro1: "valor1"
          parametro2: 123
```

### 3. Registro Dinâmico (Opcional)
```java
@Autowired
private StrategyAutoConfiguration strategyConfig;

public void criarEstrategia() {
    Map<String, Object> params = Map.of("param1", "value1");
    strategyConfig.createAndRegisterStrategy("MinhaEstrategia", params);
}
```

## Fluxo de Execução

1. **Inicialização**: StrategyAutoConfiguration registra estratégias automaticamente
2. **Execução**: TradingOrchestrator.executeStrategies() é chamado com MarketData
3. **Análise**: Cada estratégia ativa analisa os dados e gera StrategySignal
4. **Validação**: Sinais são validados (limites, portfolio, etc.)
5. **Processamento**: Sinais válidos geram Orders via ExchangePort
6. **Atualização**: Portfolio é atualizado após ordens

## Benefícios da Arquitetura

### Modularidade
- Estratégias independentes e intercambiáveis
- Fácil adição de novas estratégias
- Isolamento de falhas

### Configurabilidade
- Estratégias habilitadas/desabilitadas dinamicamente
- Parâmetros configuráveis via application.yml
- Limites de ordem por estratégia

### Testabilidade
- Interface bem definida facilita mocks
- Estratégias testáveis isoladamente
- Testes de integração com TradingOrchestrator

### Performance
- Execução assíncrona de estratégias
- Thread pool configurável
- Estratégias inativas não consomem recursos

## Monitoramento e Logs

O sistema produz logs detalhados:
- Registro de estratégias
- Sinais gerados
- Ordens executadas
- Erros e validações

```
INFO  - Registered strategy: PairTradingStrategy
DEBUG - Executing 2 active strategies with market data timestamp: 2024-01-15T10:30:00
INFO  - Strategy PairTradingStrategy generated signal: BUY for pair BTCUSDT - Reason: Pair trading: Spread below lower threshold (z-score: -2.15)
INFO  - Submitting order: BUY 0.001 BTCUSDT at 50000 (Strategy: PairTradingStrategy)
```

## Próximos Passos

### Estratégias Potenciais
- Moving Average Crossover
- RSI Overbought/Oversold
- Bollinger Bands
- Arbitragem entre exchanges
- Mean Reversion
- Momentum Trading

### Melhorias
- Interface web para gerenciar estratégias
- Métricas de performance por estratégia
- Backtesting automático
- Machine Learning integration
- Risk management avançado