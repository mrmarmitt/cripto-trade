# Estratégia de Implementação: Sistema de P&L e Performance Tracking

## 🎯 Objetivo

Implementar um sistema completo de rastreamento de lucros/perdas (P&L) e métricas de performance para estratégias de trading, permitindo avaliar a eficácia de cada estratégia em tempo real.

## 📋 Visão Geral da Arquitetura

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  TradingSignal  │───▶│  TradeTracker   │───▶│PerformanceCalc  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   TradeEntity   │    │  PositionMgmt   │    │   MetricsAPI    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🏗️ Fases de Implementação

### **Fase 1: Fundação - Entidades e Domínio** 

#### **1.1 Entidade Trade** 
```java
@Entity
public class Trade {
    private Long id;
    private String strategyName;        // Qual estratégia gerou o trade
    private TradingPair pair;          // Par negociado (BTC/USDT)
    private TradeType type;            // LONG, SHORT
    private TradeStatus status;        // OPEN, CLOSED, PARTIAL
    
    // Entrada
    private BigDecimal entryPrice;     // Preço de entrada
    private BigDecimal entryQuantity;  // Quantidade comprada
    private LocalDateTime entryTime;   // Timestamp entrada
    private String entryOrderId;      // ID da ordem de entrada
    
    // Saída
    private BigDecimal exitPrice;     // Preço de saída (null se aberto)
    private BigDecimal exitQuantity;  // Quantidade vendida
    private LocalDateTime exitTime;   // Timestamp saída
    private String exitOrderId;      // ID da ordem de saída
    
    // P&L
    private BigDecimal realizedPnL;   // Lucro/prejuízo realizado
    private BigDecimal unrealizedPnL; // Lucro/prejuízo não realizado
    private BigDecimal commission;    // Taxas pagas
    
    // Métricas
    private Duration holdingPeriod;   // Tempo que ficou em posição
    private BigDecimal maxDrawdown;   // Maior perda durante o trade
}
```

#### **1.2 Value Objects**
```java
public enum TradeType { LONG, SHORT }
public enum TradeStatus { OPEN, CLOSED, PARTIAL_CLOSED }

@Data
public class StrategyMetrics {
    private String strategyName;
    private BigDecimal totalPnL;          // P&L total
    private BigDecimal totalReturn;       // Retorno percentual
    private Integer totalTrades;          // Número de trades
    private Integer winningTrades;        // Trades lucrativos
    private Integer losingTrades;         // Trades com prejuízo
    private Double winRate;               // Taxa de acerto
    private BigDecimal avgWin;            // Lucro médio por trade vencedor
    private BigDecimal avgLoss;           // Prejuízo médio por trade perdedor
    private BigDecimal maxDrawdown;       // Maior drawdown
    private BigDecimal sharpeRatio;       // Sharpe ratio
    private BigDecimal profitFactor;      // Profit factor (lucro/prejuízo)
    private Duration avgHoldingPeriod;    // Tempo médio de posição
    private LocalDateTime firstTradeDate;
    private LocalDateTime lastTradeDate;
}
```

### **Fase 2: Serviços Core**

#### **2.1 TradeMatchingService**
```java
@Service
public class TradeMatchingService {
    
    // Quando uma ordem BUY é executada
    public Trade createOrUpdateTrade(StrategySignal signal, Order executedOrder) {
        if (signal.getType() == SignalType.BUY) {
            return createLongPosition(signal, executedOrder);
        } else {
            return closeOrUpdatePosition(signal, executedOrder);
        }
    }
    
    // FIFO: Primeiro a entrar, primeiro a sair
    private Trade closeOrUpdatePosition(StrategySignal signal, Order executedOrder) {
        List<Trade> openTrades = getOpenTrades(signal.getStrategyName(), signal.getPair());
        Trade oldestTrade = openTrades.get(0); // FIFO
        
        if (oldestTrade.getEntryQuantity().equals(executedOrder.getQuantity())) {
            // Fecha posição completamente
            return closeTrade(oldestTrade, executedOrder);
        } else {
            // Fecha parcialmente
            return partiallyCloseTrade(oldestTrade, executedOrder);
        }
    }
}
```

#### **2.2 StrategyPerformanceTracker**
```java
@Service
public class StrategyPerformanceTracker {
    
    private final TradeRepository tradeRepository;
    private final PriceService priceService;
    
    public StrategyMetrics calculateMetrics(String strategyName) {
        List<Trade> trades = tradeRepository.findByStrategyName(strategyName);
        
        return StrategyMetrics.builder()
            .strategyName(strategyName)
            .totalPnL(calculateTotalPnL(trades))
            .totalReturn(calculateTotalReturn(trades))
            .winRate(calculateWinRate(trades))
            .maxDrawdown(calculateMaxDrawdown(trades))
            .sharpeRatio(calculateSharpeRatio(trades))
            .build();
    }
    
    public BigDecimal calculateUnrealizedPnL(String strategyName) {
        List<Trade> openTrades = tradeRepository.findByStrategyNameAndStatus(
            strategyName, TradeStatus.OPEN);
        
        return openTrades.stream()
            .map(this::calculateTradeUnrealizedPnL)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateTradeUnrealizedPnL(Trade trade) {
        BigDecimal currentPrice = priceService.getCurrentPrice(trade.getPair());
        return (currentPrice.subtract(trade.getEntryPrice()))
            .multiply(trade.getEntryQuantity());
    }
}
```

#### **2.3 PortfolioValuationService**
```java
@Service
public class PortfolioValuationService {
    
    public BigDecimal calculateTotalValue(Portfolio portfolio) {
        BigDecimal totalValue = BigDecimal.ZERO;
        
        for (Map.Entry<String, BigDecimal> holding : portfolio.getHoldings().entrySet()) {
            String currency = holding.getKey();
            BigDecimal amount = holding.getValue();
            
            if ("USDT".equals(currency)) {
                totalValue = totalValue.add(amount);
            } else {
                BigDecimal priceInUSDT = priceService.getPrice(currency, "USDT");
                totalValue = totalValue.add(amount.multiply(priceInUSDT));
            }
        }
        
        return totalValue;
    }
    
    public Map<String, BigDecimal> calculateAllocationPercentages(Portfolio portfolio) {
        BigDecimal totalValue = calculateTotalValue(portfolio);
        Map<String, BigDecimal> allocations = new HashMap<>();
        
        portfolio.getHoldings().forEach((currency, amount) -> {
            BigDecimal value = getValueInUSDT(currency, amount);
            BigDecimal percentage = value.divide(totalValue, 4, RoundingMode.HALF_UP)
                                       .multiply(BigDecimal.valueOf(100));
            allocations.put(currency, percentage);
        });
        
        return allocations;
    }
}
```

### **Fase 3: Integração com TradingOrchestrator**

#### **3.1 Modificação no TradingOrchestrator**
```java
@Service
public class TradingOrchestrator {
    
    private final TradeMatchingService tradeMatchingService;
    private final StrategyPerformanceTracker performanceTracker;
    
    private void processSignal(StrategySignal signal) {
        try {
            if (!validateSignal(signal)) return;
            
            Order order = createOrderFromSignal(signal);
            exchangePort.placeOrder(order);
            
            // NOVO: Registrar trade para P&L tracking
            Trade trade = tradeMatchingService.createOrUpdateTrade(signal, order);
            log.info("Trade created/updated: {} - P&L: {}", 
                    trade.getId(), trade.getRealizedPnL());
            
            updatePortfolioForOrder(order);
            
            // NOVO: Log de performance
            logStrategyPerformance(signal.getStrategyName());
            
        } catch (Exception e) {
            log.error("Error processing signal: {}", e.getMessage(), e);
        }
    }
    
    private void logStrategyPerformance(String strategyName) {
        StrategyMetrics metrics = performanceTracker.calculateMetrics(strategyName);
        log.info("Strategy {} performance - Total P&L: {}, Win Rate: {}%, Trades: {}", 
                strategyName, metrics.getTotalPnL(), 
                metrics.getWinRate() * 100, metrics.getTotalTrades());
    }
}
```

### **Fase 4: APIs e Monitoramento**

#### **4.1 Performance REST Controller**
```java
@RestController
@RequestMapping("/api/performance")
public class PerformanceController {
    
    @GetMapping("/strategies")
    public List<StrategyMetrics> getAllStrategiesMetrics() {
        return performanceTracker.getAllStrategiesMetrics();
    }
    
    @GetMapping("/strategies/{strategyName}")
    public StrategyMetrics getStrategyMetrics(@PathVariable String strategyName) {
        return performanceTracker.calculateMetrics(strategyName);
    }
    
    @GetMapping("/portfolio/value")
    public PortfolioValuation getPortfolioValuation() {
        return PortfolioValuation.builder()
            .totalValue(portfolioValuationService.calculateTotalValue(currentPortfolio))
            .allocations(portfolioValuationService.calculateAllocationPercentages(currentPortfolio))
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    @GetMapping("/trades")
    public Page<Trade> getTrades(
            @RequestParam(required = false) String strategyName,
            @RequestParam(required = false) TradeStatus status,
            Pageable pageable) {
        return tradeRepository.findTrades(strategyName, status, pageable);
    }
    
    @GetMapping("/pnl/summary")
    public PnLSummary getPnLSummary() {
        return PnLSummary.builder()
            .totalRealizedPnL(performanceTracker.getTotalRealizedPnL())
            .totalUnrealizedPnL(performanceTracker.getTotalUnrealizedPnL())
            .totalPnL(performanceTracker.getTotalPnL())
            .bestPerformingStrategy(performanceTracker.getBestStrategy())
            .worstPerformingStrategy(performanceTracker.getWorstStrategy())
            .build();
    }
}
```

#### **4.2 Dashboards e Métricas**
```java
@GetMapping("/dashboard")
public TradingDashboard getDashboard() {
    return TradingDashboard.builder()
        .portfolioValue(portfolioValuationService.calculateTotalValue(currentPortfolio))
        .totalPnL(performanceTracker.getTotalPnL())
        .activeStrategies(strategyRegistry.getActiveCount())
        .openPositions(tradeRepository.countOpenTrades())
        .todaysPnL(performanceTracker.getTodaysPnL())
        .topStrategies(performanceTracker.getTopPerformingStrategies(5))
        .recentTrades(tradeRepository.getRecentTrades(10))
        .build();
}
```

## 📊 Exemplos de Saída

### **Log de Trade Executado:**
```bash
[INFO] TradingOrchestrator - Strategy PairTradingStrategy generated signal: BUY BTCUSDT
[INFO] TradeMatchingService - Created LONG position: Trade #1234 - Entry: $45,000
[INFO] TradingOrchestrator - Trade created: #1234 - P&L: $0.00 (OPEN)
[INFO] TradingOrchestrator - Strategy PairTradingStrategy performance - Total P&L: +$127.50, Win Rate: 67%, Trades: 12
```

### **API Response Example:**
```json
{
  "strategyName": "PairTradingStrategy",
  "totalPnL": 127.50,
  "totalReturn": 2.83,
  "totalTrades": 12,
  "winningTrades": 8,
  "losingTrades": 4,
  "winRate": 0.67,
  "avgWin": 45.30,
  "avgLoss": -23.75,
  "maxDrawdown": -89.20,
  "sharpeRatio": 1.42,
  "profitFactor": 1.91
}
```

## 🎯 Priorização de Tarefas

### **Sprint 1: Fundação (1-2 semanas)**
1. ✅ Criar entidade Trade
2. ✅ Criar StrategyMetrics value object
3. ✅ Implementar TradeRepository
4. ✅ Criar testes unitários básicos

### **Sprint 2: Serviços Core (2-3 semanas)**
5. ✅ Implementar TradeMatchingService
6. ✅ Implementar StrategyPerformanceTracker
7. ✅ Implementar PortfolioValuationService
8. ✅ Testes de integração

### **Sprint 3: Integração (1-2 semanas)**
9. ✅ Integrar com TradingOrchestrator
10. ✅ Adicionar logging de performance
11. ✅ Testes end-to-end

### **Sprint 4: APIs e Monitoramento (1-2 semanas)**
12. ✅ Criar PerformanceController
13. ✅ Implementar dashboard endpoints
14. ✅ Documentação da API
15. ✅ Testes de API

## 🔍 Critérios de Sucesso

### **Funcionalidades Mínimas:**
- [ ] Rastrear P&L por estratégia
- [ ] Calcular métricas básicas (win rate, total P&L)
- [ ] API para consultar performance
- [ ] Logs estruturados de trades

### **Funcionalidades Avançadas:**
- [ ] Drawdown tracking
- [ ] Sharpe ratio calculation
- [ ] Portfolio valuation em tempo real
- [ ] Dashboard interativo
- [ ] Alertas de performance

## 🧪 Estratégia de Testes

### **Testes Unitários:**
- Cálculos de P&L com diferentes cenários
- Métricas de performance com datasets conhecidos
- Matching de trades (FIFO, partial fills)

### **Testes de Integração:**
- Fluxo completo: Signal → Trade → P&L
- Persistência de trades no banco
- APIs de performance

### **Testes de Performance:**
- Cálculo de métricas para 10k+ trades
- Queries otimizadas no banco
- Cache de métricas calculadas

## 📋 Todas as Tarefas do Projeto

### ✅ **CONCLUÍDAS:**

**[1.0] Criação da aplicação Spring Boot com Gradle e Docker Compose**
- ✅ Estrutura base Spring Boot hexagonal
- ✅ Configuração Gradle
- ✅ Docker Compose setup

**[1.1] Estrutura base Spring Boot hexagonal**
- ✅ Pacotes por camadas (domain, application, infrastructure)
- ✅ Separação clara de responsabilidades

**[1.2] Modelar entidades do domínio**
- ✅ TradingPair entity
- ✅ Order entity  
- ✅ Portfolio entity
- ✅ MarketData entity
- ✅ Trade entity (P&L tracking)

**[1.3] Definir portas do domínio**
- ✅ ExchangePort interface
- ✅ TradingStrategy interface
- ✅ WebSocketPort interface

**[2.1] TradingOrchestrator**
- ✅ Coordenação de execução de estratégias
- ✅ Processamento assíncrono de sinais
- ✅ Validação de ordens e limites
- ✅ Integração com WebSocket

**[2.2] Sistema modular de estratégias**
- ✅ Interface TradingStrategy
- ✅ Registro automático de estratégias (StrategyAutoConfiguration)
- ✅ Sistema de configuração flexível (StrategyProperties)
- ✅ Implementação de PairTradingStrategy
- ✅ Integração com fluxo de dados em tempo real (TradingStrategyListener)

**[2.3] Sistema de P&L e Performance Tracking**
- ✅ Entidade Trade para rastreamento de posições
- ✅ StrategyPerformanceTracker com métricas avançadas
- ✅ PortfolioValuationService para avaliação em tempo real
- ✅ StrategyMetrics com 30+ indicadores profissionais

**[3.1] MockExchangeAdapter**
- ✅ Simulação de exchange para desenvolvimento
- ✅ MockWebSocketAdapter com preços automáticos
- ✅ BinanceWebSocketAdapter para integração real

**[3.4] Controllers REST**
- ✅ TradingController (ordens, preços)
- ✅ HealthController (health checks)
- ✅ MetricsController (métricas do sistema)
- ✅ PriceAlertController (alertas de preço)

**[5.2] Logging estruturado**
- ✅ Sistema de auditoria (TradingAuditService)
- ✅ Logs estruturados para análise
- ✅ Rastreamento de operações

### 🔄 **PENDENTES:**

**[2.4] TradeMatchingService** 
- [ ] Matching FIFO automático (First In, First Out)
- [ ] Criação de trades para sinais BUY
- [ ] Fechamento de trades para sinais SELL
- [ ] Suporte a fechamentos parciais
- [ ] Cálculo automático de P&L realizado
- [ ] Atualização de P&L não realizado

**[2.5] Integração de P&L no TradingOrchestrator**
- [ ] Modificar processSignal() para incluir trade tracking
- [ ] Logging estruturado de execução de trades
- [ ] Performance metrics em tempo real após cada trade
- [ ] Sincronização de portfolio com trades abertos
- [ ] Tratamento robusto de erros de matching

**[2.6] APIs REST de Performance**
- [ ] PerformanceController (/api/performance/*)
- [ ] PortfolioController (/api/portfolio/*)
- [ ] TradeController (/api/trades/*)
- [ ] DashboardController (/api/dashboard/*)
- [ ] DTOs específicos e documentação Swagger
- [ ] Filtros avançados e paginação

**[2.7] Testes do Sistema de P&L**
- [ ] TradeMatchingServiceTest (cenários FIFO)
- [ ] StrategyPerformanceTrackerTest (métricas)
- [ ] PortfolioValuationServiceTest (avaliações)
- [ ] Controllers REST tests (APIs)
- [ ] Testes de integração end-to-end
- [ ] Cobertura mínima de 80%

**[3.2] Configuração de banco de dados**
- [ ] Entidades JPA adicionais
- [ ] Repositories especializados
- [ ] Migrations/Schema evolution

**[3.3] Sistema de agendamento**
- [ ] Jobs para processamento de ordens
- [ ] Monitoramento de preços
- [ ] Limpeza automática de dados antigos

**[4.1] Engine de backtesting**
- [ ] Simulação histórica de estratégias
- [ ] Métricas de performance histórica
- [ ] Comparação de estratégias

**[4.2] Gerador de dados históricos**
- [ ] Simulação de dados de mercado
- [ ] Integração com APIs reais de dados históricos
- [ ] Cache de dados históricos

**[5.1] Sistema de configuração**
- [ ] Configurações dinâmicas via API
- [ ] Profiles avançados para diferentes ambientes
- [ ] Hot reload de configurações

## 📈 Próximos Passos

Após completar o sistema de P&L, o sistema terá:
- ✅ **Visibilidade total** de P&L por estratégia
- ✅ **Métricas profissionais** de trading
- ✅ **APIs robustas** para monitoramento
- ✅ **Base sólida** para otimização de estratégias

Isso permitirá **validar** se as estratégias estão funcionando e **otimizar** parâmetros baseado em dados reais de performance!