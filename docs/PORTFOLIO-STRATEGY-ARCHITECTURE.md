# Portfolio-Strategy Architecture Plan

Este documento descreve a arquitetura básica para implementar o sistema **1 Portfolio = 1 Strategy**, que servirá como base para evoluir para Pod Architecture (Citadel Style) no futuro.

## 📁 Estrutura de Pacotes

### Seguindo Hexagonal Architecture Existente:

```
core/src/main/java/com/marmitt/core/
├── domain/                           # Entidades e Value Objects
│   ├── portfolio/                    # 🆕 Portfolio Domain
│   │   ├── Portfolio.java           # Entidade principal
│   │   ├── Position.java            # Posição em um ativo
│   │   ├── Balance.java             # Saldo do portfolio
│   │   └── Transaction.java         # Registro de transações
│   ├── strategy/                     # 🆕 Strategy Domain  
│   │   ├── StrategyExecution.java   # Execução de estratégia
│   │   └── StrategyPerformance.java # Performance da estratégia
│   └── value/                        # 🆕 Value Objects
│       ├── Asset.java               # Valor de ativos (crypto, fiat, stablecoin)
│       ├── PortfolioId.java         # ID do portfolio
│       └── Percentage.java          # Percentual
├── application/                      # Use Cases e Services
│   ├── portfolio/                    # 🆕 Portfolio Use Cases
│   │   ├── CreatePortfolioUseCase.java
│   │   ├── ExecuteStrategyUseCase.java
│   │   └── CalculatePerformanceUseCase.java
│   ├── listener/                     # Listeners existentes + novo
│   │   ├── MarketDataPriceUpdateListener.java  # Já existe
│   │   └── PortfolioStrategyListener.java      # 🆕 Novo
│   └── service/                      # 🆕 Application Services
│       ├── PortfolioOrchestrator.java
│       └── StrategyExecutionService.java
├── ports/
│   ├── inbound/                      # Contratos para entrada
│   │   └── portfolio/                # 🆕 Portfolio Ports
│   │       ├── CreatePortfolioPort.java
│   │       ├── ExecuteStrategyPort.java
│   │       └── GetPerformancePort.java
│   └── outbound/                     # Contratos para saída
│       ├── portfolio/                # 🆕 Portfolio Repositories
│       │   ├── PortfolioRepositoryPort.java
│       │   └── TransactionRepositoryPort.java
│       ├── order/                    # 🆕 Order Management
│       │   └── OrderExecutionPort.java
│       └── strategy/                 # Já existe + extensão
│           ├── TradingStrategy.java  # Já existe
│           └── StrategyExecutionPort.java # Já existe
└── dto/                              # Data Transfer Objects
    └── portfolio/                    # 🆕 Portfolio DTOs
        ├── PortfolioResponse.java
        ├── PerformanceMetrics.java
        └── ExecutionResult.java
```

## 🏗️ Responsabilidades de Cada Classe

### 🎯 Domain Layer

#### Portfolio.java (Aggregate Root)
```java
class Portfolio {
    // Responsabilidades:
    // - Manter estado do portfolio (id, name, strategy)
    // - Gerenciar capital inicial e atual
    // - Manter lista de positions
    // - Validar operações de compra/venda
    // - Calcular valor total do portfolio
}
```

#### Position.java (Entity)
```java
class Position {
    // Responsabilidades:
    // - Representar posição em um ativo específico
    // - Manter quantidade, preço médio, valor atual
    // - Calcular P&L unrealized
    // - Validar quantidade para venda
}
```

#### Balance.java (Value Object)
```java
class Balance {
    // Responsabilidades:
    // - Manter saldo disponível vs. investido
    // - Validar se há capital suficiente
    // - Calcular utilização de capital
}
```

#### Transaction.java (Entity)
```java
class Transaction {
    // Responsabilidades:
    // - Registrar operação de compra/venda
    // - Manter timestamp, símbolo, quantidade, preço
    // - Calcular fees e custos
    // - Immutable record de operação
}
```

#### Asset.java (Value Object)
```java
class Asset {
    // Responsabilidades:
    // - Representar valor de qualquer tipo de ativo (crypto, fiat, stablecoin)
    // - Manter amount, symbol e type do ativo
    // - Validar operações matemáticas entre assets do mesmo tipo
    // - Conversão e formatação adequada por tipo de ativo
    // - Factory methods para diferentes tipos (crypto, fiat, stablecoin)
}
```

### 🔧 Application Layer

#### PortfolioOrchestrator.java (Main Service)
```java
class PortfolioOrchestrator {
    // Responsabilidades:
    // - Coordenar execução de todas as estratégias
    // - Gerenciar múltiplos portfolios
    // - Orquestrar fluxo: MarketData → Strategy → Orders
    // - Resolver conflitos entre portfolios
}
```

#### PortfolioStrategyListener.java (Event Handler)
```java
class PortfolioStrategyListener implements PriceUpdateListener {
    // Responsabilidades:
    // - Receber updates de preço do WebSocket
    // - Filtrar apenas símbolos de portfolios ativos
    // - Converter MarketData → StrategyInput
    // - Triggar execução de estratégias
    // - Processar StrategyOutput → Actions
}
```

#### ExecuteStrategyUseCase.java (Use Case)
```java
class ExecuteStrategyUseCase {
    // Responsabilidades:
    // - Executar estratégia específica de um portfolio
    // - Validar se portfolio está ativo
    // - Processar StrategyOutput
    // - Executar ordens se necessário
    // - Atualizar positions
}
```

#### CreatePortfolioUseCase.java (Use Case)
```java
class CreatePortfolioUseCase {
    // Responsabilidades:
    // - Criar novo portfolio com estratégia
    // - Validar capital inicial
    // - Configurar estratégia
    // - Persistir portfolio
}
```

### 🔌 Ports Layer

#### PortfolioRepositoryPort.java (Outbound Port)
```java
interface PortfolioRepositoryPort {
    // Responsabilidades:
    // - Definir contrato para persistência de portfolios
    // - CRUD operations
    // - Queries por estratégia, status, etc.
}
```

#### OrderExecutionPort.java (Outbound Port)
```java
interface OrderExecutionPort {
    // Responsabilidades:
    // - Definir contrato para execução de ordens
    // - Mock vs Real execution
    // - Buy/Sell operations
    // - Order status tracking
}
```

## 📊 Diagrama de Arquitetura Básica

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               PRESENTATION LAYER                                │
│                          (spring-application module)                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐                 │
│  │ Portfolio        │ │ Performance      │ │ Strategy         │                 │
│  │ Controller       │ │ Controller       │ │ Controller       │                 │
│  │ /portfolios/*    │ │ /performance/*   │ │ /strategies/*    │                 │
│  └──────────────────┘ └──────────────────┘ └──────────────────┘                 │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                               APPLICATION LAYER                                 │
│                                (core module)                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                       PORTFOLIO ORCHESTRATION                              │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │ Portfolio        │ │ Strategy         │ │ Performance      │            │ │
│  │  │ Orchestrator     │ │ Execution        │ │ Service          │            │ │
│  │  │                  │ │ Service          │ │                  │            │ │
│  │  │ • Manage multiple│ │ • Execute        │ │ • Calculate P&L  │            │ │
│  │  │   portfolios     │ │   strategies     │ │ • Track metrics  │            │ │
│  │  │ • Coordinate     │ │ • Process        │ │ • Generate       │            │ │
│  │  │   flows          │ │   outputs        │ │   reports        │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                            EVENT HANDLING                                  │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐                    ┌──────────────────┐              │ │
│  │  │ MarketData       │                    │ Portfolio        │              │ │
│  │  │ PriceUpdate      │                    │ Strategy         │              │ │
│  │  │ Listener         │                    │ Listener         │              │ │
│  │  │                  │                    │                  │              │ │
│  │  │ • Monitor prices │                    │ • Trigger        │              │ │
│  │  │ • Detect changes │                    │   strategies     │              │ │
│  │  │ • Log movements  │                    │ • Filter active  │              │ │
│  │  │                  │                    │   symbols        │              │ │
│  │  └──────────────────┘                    └──────────────────┘              │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                             USE CASES                                      │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │ Create           │ │ Execute          │ │ Calculate        │            │ │
│  │  │ Portfolio        │ │ Strategy         │ │ Performance      │            │ │
│  │  │ UseCase          │ │ UseCase          │ │ UseCase          │            │ │
│  │  │                  │ │                  │ │                  │            │ │
│  │  │ • Validate input │ │ • Run strategy   │ │ • Compute P&L    │            │ │
│  │  │ • Create         │ │ • Process output │ │ • Calculate      │            │ │
│  │  │   portfolio      │ │ • Execute orders │ │   metrics        │            │ │
│  │  │ • Setup strategy │ │                  │ │ • Update perf    │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 DOMAIN LAYER                                    │
│                                (core module)                                    │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         PORTFOLIO AGGREGATE                                │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │   Portfolio      │ │    Position      │ │   Transaction    │            │ │
│  │  │                  │ │                  │ │                  │            │ │
│  │  │ • portfolioId    │ │ • symbol         │ │ • transactionId  │            │ │
│  │  │ • name           │ │ • quantity       │ │ • type (BUY/SELL)│            │ │
│  │  │ • strategy       │ │ • avgPrice       │ │ • symbol         │            │ │
│  │  │ • balance        │ │ • currentValue   │ │ • quantity       │            │ │
│  │  │ • positions[]    │ │ • unrealizedPnL  │ │ • price          │            │ │
│  │  │ • isActive       │ │                  │ │ • timestamp      │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         VALUE OBJECTS                                      │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │     Asset        │ │   PortfolioId    │ │   Percentage     │            │ │
│  │  │                  │ │                  │ │                  │            │ │
│  │  │ • amount         │ │ • value (UUID)   │ │ • value          │            │ │
│  │  │ • symbol         │ │                  │ │                  │            │ │
│  │  │ • type           │ │                  │ │                  │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                             INFRASTRUCTURE LAYER                                │
│                          (spring-application module)                            │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                          REPOSITORIES                                      │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │ InMemory         │ │ InMemory         │ │ InMemory         │            │ │
│  │  │ Portfolio        │ │ Transaction      │ │ Order            │            │ │
│  │  │ Repository       │ │ Repository       │ │ Repository       │            │ │
│  │  │                  │ │                  │ │                  │            │ │
│  │  │ • portfolios Map │ │ • transactions   │ │ • orders Map     │            │ │
│  │  │ • CRUD ops       │ │   Map            │ │ • CRUD ops       │            │ │
│  │  │                  │ │ • CRUD ops       │ │                  │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                        ORDER EXECUTION                                     │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐                    ┌──────────────────┐              │ │
│  │  │ Mock             │                    │ Order            │              │ │
│  │  │ Order            │                    │ Validator        │              │ │
│  │  │ Executor         │                    │                  │              │ │
│  │  │                  │                    │                  │              │ │
│  │  │ • Simulate       │                    │ • Validate       │              │ │
│  │  │   orders         │                    │   orders         │              │ │
│  │  │ • Return results │                    │ • Check balances │              │ │
│  │  └──────────────────┘                    └──────────────────┘              │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 🔄 Data Flow

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                  DATA FLOW                                      │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  WebSocket → MarketData → PortfolioStrategyListener → StrategyInput             │
│       │                            │                        │                   │
│       ▼                            ▼                        ▼                   │
│  MarketDataPriceUpdateListener → Strategy.execute() → StrategyOutput            │
│       │                            │                        │                   │
│       ▼                            ▼                        ▼                   │
│  Log/Monitor                    ExecuteStrategyUseCase → OrderExecution         │
│                                    │                        │                   │
│                                    ▼                        ▼                   │
│                            Portfolio.updatePosition → Transaction.record        │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 📈 Listeners Strategy

### Abordagem: 2 Listeners Especializados

#### MarketDataPriceUpdateListener (Existente)
- **Responsabilidade**: Monitoring geral de preços
- **Funcionalidades**:
  - Price change detection
  - Significant movement alerts  
  - General market monitoring
  - Logging estruturado

#### PortfolioStrategyListener (Novo)
- **Responsabilidade**: Execução de portfolios/estratégias
- **Funcionalidades**:
  - Strategy execution triggering
  - Portfolio updates
  - Position management
  - Filtrar apenas símbolos de portfolios ativos

### Vantagens desta Abordagem:
1. **Separation of Concerns**: Cada listener tem responsabilidade clara
2. **Independent Evolution**: Podem evoluir independentemente  
3. **Selective Processing**: Portfolio listener só processa símbolos ativos
4. **Performance**: Otimizações específicas para cada caso

## 🚀 Ordem de Implementação

### Fase 1: Foundation (1 semana)
1. Value Objects (`Asset`, `PortfolioId`, `Percentage`)
2. Domain Entities (`Portfolio`, `Position`, `Transaction`)
3. Basic Ports (`PortfolioRepositoryPort`)

### Fase 2: Use Cases (1 semana)  
4. `CreatePortfolioUseCase`
5. In-memory repositories
6. Basic REST endpoints

### Fase 3: Strategy Integration (1 semana)
7. `PortfolioStrategyListener`
8. `ExecuteStrategyUseCase`
9. `PortfolioOrchestrator`

### Fase 4: Order Execution (1 semana)
10. `MockOrderExecutor`
11. `OrderValidator`
12. Position updates

### Fase 5: Performance (1 semana)
13. `CalculatePerformanceUseCase`
14. P&L calculations
15. Performance metrics

## 🎯 Evolução para Pod Architecture

Esta arquitetura básica já prepara o terreno para evoluir para Pod Architecture:

### Atual: Individual Strategies
```
System
├── Portfolio A (SMA Strategy)
├── Portfolio B (RSI Strategy)  
└── Portfolio C (MACD Strategy)
```

### Futuro: Pod Architecture
```
System
├── Crypto Systematic Pod
│   ├── SMA Portfolio
│   ├── RSI Portfolio
│   └── MACD Portfolio
├── Arbitrage Pod
│   ├── Cross-Exchange Arb Portfolio
│   └── Statistical Arb Portfolio
└── Market Making Pod
    └── Liquidity Provision Portfolio
```

## 💰 Asset Value Object - Detalhamento

### Por que Asset ao invés de Money?

Em sistemas de crypto trading, o conceito tradicional de "Money" não é adequado porque:

- **Fiat**: USD, EUR, BRL (moedas tradicionais) 
- **Crypto**: BTC, ETH, ADA (ativos digitais voláteis)
- **Stablecoins**: USDT, USDC, BUSD (híbrido fiat-crypto)

### Estrutura do Asset:

```java
// Value Object
public record Asset(
    BigDecimal amount,
    String symbol,
    AssetType type
) {
    public static Asset crypto(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol, AssetType.CRYPTOCURRENCY);
    }
    
    public static Asset fiat(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol, AssetType.FIAT);
    }
    
    public static Asset stablecoin(BigDecimal amount, String symbol) {
        return new Asset(amount, symbol, AssetType.STABLECOIN);
    }
}

// Enum
public enum AssetType {
    CRYPTOCURRENCY,  // BTC, ETH, ADA
    FIAT,           // USD, EUR, BRL  
    STABLECOIN      // USDT, USDC, BUSD
}
```

### Exemplos de Uso:

```java
// Portfolio com base em USDT
Portfolio portfolio = Portfolio.builder()
    .baseAsset(Asset.stablecoin(new BigDecimal("10000"), "USDT"))
    .build();

// Position em BTC
Position btcPosition = Position.builder()
    .symbol(Symbol.of("BTCUSDT"))
    .quantity(Asset.crypto(new BigDecimal("0.5"), "BTC"))
    .averagePrice(Asset.stablecoin(new BigDecimal("45000"), "USDT"))
    .build();

// Transaction de compra
Transaction buyOrder = Transaction.builder()
    .type(TransactionType.BUY)
    .symbol(Symbol.of("BTCUSDT"))
    .quantity(Asset.crypto(new BigDecimal("0.1"), "BTC"))
    .price(Asset.stablecoin(new BigDecimal("45000"), "USDT"))
    .total(Asset.stablecoin(new BigDecimal("4500"), "USDT"))
    .fee(Asset.stablecoin(new BigDecimal("4.5"), "USDT"))
    .build();
```

## 📝 Notas Importantes

1. **Hexagonal Architecture**: Mantém consistência com arquitetura existente
2. **Single Responsibility**: Cada classe tem uma responsabilidade clara
3. **Testability**: Arquitetura facilita testes unitários e de integração
4. **Scalability**: Preparado para evoluir para Pod Architecture
5. **Industry Standards**: Alinhado com práticas de fundos como Citadel
6. **Asset Flexibility**: Suporte nativo para crypto, fiat e stablecoins

---

Este plano serve como guia para implementar o sistema básico **1 Portfolio = 1 Strategy** que evoluirá naturalmente para Pod Architecture conforme necessário.