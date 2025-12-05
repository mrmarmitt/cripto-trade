# Portfolio-Strategy Architecture Plan

Este documento descreve a arquitetura básica para implementar o sistema **1 Portfolio = 1 Strategy**, que servirá como base para evoluir para Pod Architecture (Citadel Style) no futuro.

## 📁 Estrutura de Pacotes

### Seguindo Hexagonal Architecture Existente:

```
core/src/main/java/com/marmitt/core/
├── domain/                           # Entidades e Value Objects
│   ├── portfolio/                    # 🆕 Portfolio Domain
│   │   ├── Portfolio.java           # Entidade principal (Aggregate Root)
│   │   ├── Position.java            # Posição em um ativo (Entity)
│   │   ├── Balance.java             # Saldo do portfolio (Value Object)
│   │   ├── Transaction.java         # Registro de transações (Entity)
│   │   └── Asset.java               # Valor de ativos (Value Object)
│   ├── Symbol.java                   # Símbolo do par de trading (Value Object)
│   ├── StrategyInput.java            # Input para estratégias (Value Object)
│   ├── StrategyOutput.java           # Output de estratégias (Value Object)
│   └── data/                         # Domain Data Objects existentes
│       ├── MarketData.java
│       ├── TradeData.java
│       └── ...
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
│       │   ├── TransactionRepositoryPort.java
│       │   └── StrategyRepositoryPort.java
│       ├── order/                    # 🆕 Order Management
│       │   └── OrderExecutionPort.java
│       └── strategy/                 # Ports para estratégia
│           ├── TradingStrategy.java      # Interface para implementações externas
│           └── StrategyExecutionPort.java # Port para execução de estratégia
└── dto/                              # Data Transfer Objects
    └── portfolio/                    # 🆕 Portfolio DTOs
        ├── PortfolioResponse.java
        ├── PerformanceMetrics.java
        └── ExecutionResult.java
```

## 🏗️ Classes de Domínio - Responsabilidades e Detalhamento

### Portfolio (Aggregate Root)

**Responsabilidades:**
- Manter estado do portfolio (id, name, strategyId, strategyName)
- Gerenciar capital inicial e atual através do Balance
- Manter e organizar lista de positions por Symbol
- Validar operações de compra/venda antes da execução
- Calcular valor total do portfolio e P&L
- Referenciar estratégia por ID (não executar diretamente)
- Controlar estado ativo/inativo para safety

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `id` | `UUID` | Identificador único do portfolio | Chave primária, referência externa |
| `name` | `String` | Nome descritivo do portfolio | Interface do usuário, relatórios |
| `strategyId` | `UUID` | ID da estratégia associada | Referência estável para execução |
| `strategyName` | `String` | Nome da estratégia | Interface, logs, identificação |
| `balance` | `Balance` | Gestão de capital disponível/investido | Controle de liquidez, validações |
| `positions` | `Map<Symbol, Position>` | Posições ativas indexadas por símbolo | Acesso rápido O(1), gestão de ativos |
| `transactions` | `List<Transaction>` | Histórico de todas as transações | Auditoria, P&L, compliance |
| `isActive` | `boolean` | Status ativo/inativo do portfolio | Controle de execução, safety |
| `createdAt` | `Instant` | Timestamp de criação | Auditoria, métricas temporais |

### Position (Entity)

**Responsabilidades:**
- Representar posição em um ativo específico (par de trading)
- Manter quantidade, preço médio e preço atual
- Calcular P&L unrealized automaticamente
- Validar quantidade para operações de venda
- Atualizar preço médio com weighted average em compras
- Reduzir posição em vendas mantendo integridade

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `symbol` | `Symbol` | Par de trading (ex: "BTCUSDT") | Identificador do mercado/contexto |
| `quantity` | `Asset` | Quantidade do ativo base | Volume possuído (ex: "0.5 BTC") |
| `averagePrice` | `Asset` | Preço médio de compra | Cálculo de P&L, base de custo |
| `currentPrice` | `Asset` | Preço atual de mercado | Avaliação mark-to-market |

**Relacionamento Symbol ↔ Assets:**
- `Symbol("BTCUSDT")` = Contexto do mercado
- `quantity.symbol()` = "BTC" (base asset)
- `price.symbol()` = "USDT" (quote asset)
- Validação: `symbol == quantity.symbol + price.symbol`

### Transaction (Entity)

**Responsabilidades:**
- Registrar operação de compra/venda de forma imutável
- Manter timestamp, símbolo, quantidade, preço e fees
- Calcular valor total da operação
- Fornecer histórico auditável de todas as operações
- Servir como base para cálculos de P&L e compliance

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `id` | `UUID` | Identificador único da transação | Rastreabilidade, referência |
| `type` | `TransactionType` | BUY ou SELL | Classificação da operação |
| `symbol` | `Symbol` | Par de trading da operação | Contexto de mercado |
| `quantity` | `Asset` | Quantidade transacionada | Volume da operação |
| `price` | `Asset` | Preço unitário | Valor de execução |
| `total` | `Asset` | Valor total da operação | Impacto no capital |
| `fee` | `Asset` | Taxa da transação | Custo operacional |
| `timestamp` | `Instant` | Momento da execução | Ordenação temporal |

### Balance (Value Object)

**Responsabilidades:**
- Manter saldo disponível vs. investido
- Validar se há capital suficiente para operações
- Calcular utilização de capital e P&L do portfolio
- Garantir invariante matemática de conservação de capital
- Alocar/desalocar capital conforme operações

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `available` | `Asset` | Capital disponível para trading | Liquidez, validação de ordens |
| `invested` | `Asset` | Capital atualmente investido | Utilização de capital |
| `initialCapital` | `Asset` | Capital inicial do portfolio | Base para cálculo de P&L |

**Invariante:** `available + invested = totalCapital`

### Asset (Value Object)

**Responsabilidades:**
- Representar valor de qualquer tipo de ativo (crypto, fiat, stablecoin)
- Manter amount, symbol e type do ativo
- Validar operações matemáticas entre assets do mesmo tipo
- Conversão e formatação adequada por tipo de ativo
- Factory methods para diferentes tipos de ativo
- Garantir precisão decimal apropriada por tipo

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `amount` | `BigDecimal` | Quantidade numérica | Precisão decimal para valores |
| `symbol` | `String` | Símbolo do ativo | Identificação (BTC, USDT, USD) |
| `type` | `AssetType` | Tipo do ativo | Comportamento específico |

**AssetType e seus comportamentos:**
- `CRYPTOCURRENCY`: 8 casas decimais, alta volatilidade
- `FIAT`: 2 casas decimais, moedas tradicionais  
- `STABLECOIN`: 4 casas decimais, pareadas com fiat

### Symbol (Value Object)

**Responsabilidades:**
- Representar par de trading seguindo padrão das exchanges
- Servir como chave identificadora única para positions
- Validar formato e consistência do símbolo
- Facilitar mapeamento e indexação de mercados

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `value` | `String` | Representação do par | Identificação padrão exchanges |

**Exemplos:**
- `"BTCUSDT"` = Bitcoin vs Tether
- `"ETHBTC"` = Ethereum vs Bitcoin
- `"ADAUSD"` = Cardano vs US Dollar

### StrategyInput (Value Object)

**Responsabilidades:**
- Encapsular dados de mercado necessários para execução de estratégias
- Validar integridade e consistência dos dados de entrada
- Garantir que informações de preço estejam consistentes (bid ≤ current ≤ ask)
- Verificar se dados estão atualizados e dentro de limites válidos
- Servir como contrato padronizado entre market data e estratégias

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `symbol` | `Symbol` | Par de trading | Contexto do mercado para execução |
| `currentPrice` | `BigDecimal` | Preço atual/last do ativo | Base para decisões da estratégia |
| `volume` | `BigDecimal` | Volume negociado | Liquidez e momentum do mercado |
| `bidPrice` | `BigDecimal` | Melhor oferta de compra | Spread analysis, order placement |
| `askPrice` | `BigDecimal` | Melhor oferta de venda | Spread analysis, order placement |
| `highPrice` | `BigDecimal` | Maior preço em 24h | Range analysis, volatilidade |
| `lowPrice` | `BigDecimal` | Menor preço em 24h | Range analysis, support/resistance |
| `timestamp` | `Instant` | Momento dos dados | Validação de freshness |

### StrategyOutput (Value Object)

**Responsabilidades:**
- Encapsular decisão e parâmetros de trading gerados pela estratégia
- Definir ação específica (BUY/SELL/HOLD) com quantidades e preços
- Fornecer contexto e justificativa para a decisão tomada
- Incluir parâmetros de risk management (stop loss, take profit)
- Permitir análise posterior da qualidade das decisões

| Variável | Tipo | Descrição | Utilidade |
|----------|------|-----------|-----------|
| `strategyName` | `String` | Nome da estratégia executada | Identificação, logs, auditoria |
| `symbol` | `Symbol` | Par de trading da decisão | Contexto da operação |
| `decision` | `TradingAction` | Ação recomendada (BUY/SELL/HOLD) | Tipo de operação a executar |
| `quantity` | `BigDecimal` | Quantidade a ser operada | Volume da ordem |
| `targetPrice` | `BigDecimal` | Preço alvo para execução | Limite de preço |
| `stopLoss` | `BigDecimal` | Preço de stop loss | Gestão de risco |
| `takeProfit` | `BigDecimal` | Preço de take profit | Realização de lucros |
| `reasoning` | `String` | Justificativa da decisão | Debug, auditoria, análise |
| `confidence` | `BigDecimal` | Nível de confiança (0-1) | Força do sinal |
| `timestamp` | `Instant` | Momento da decisão | Ordenação temporal |
| `metadata` | `Map<String, Object>` | Dados adicionais da estratégia | Indicadores, parâmetros |

### Enums

#### AssetType
**Responsabilidade:** Definir comportamento específico para cada tipo de ativo

| Valor | Descrição | Comportamento |
|-------|-----------|---------------|
| `CRYPTOCURRENCY` | Ativos digitais voláteis | 8 casas decimais, formatação específica |
| `FIAT` | Moedas tradicionais governamentais | 2 casas decimais, estabilidade |
| `STABLECOIN` | Criptomoedas estáveis | 4 casas decimais, pareadas com fiat |

#### TransactionType
**Responsabilidade:** Classificar tipo de operação para processamento correto

| Valor | Descrição | Impacto no Portfolio |
|-------|-----------|---------------------|
| `BUY` | Operação de compra | Reduz available, aumenta invested, cria/atualiza position |
| `SELL` | Operação de venda | Aumenta available, reduz invested, reduz/remove position |

## 🔄 Relacionamentos e Fluxos

### Portfolio → Position
- Portfolio agrega múltiplas Positions
- Indexação por Symbol para acesso O(1)
- Position atualizada automaticamente nas operações

### Position → Asset
- Symbol como contexto de mercado
- quantity.symbol = base asset do par
- price.symbol = quote asset do par

### Transaction → Portfolio
- Toda Transaction gera atualização no Portfolio
- Balance é atualizado conforme tipo (BUY/SELL)
- Position é criada/atualizada automaticamente

### Asset → AssetType
- Comportamento específico por tipo
- Formatação e precisão adequadas
- Validações de operações matemáticas

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
│  │  │ • strategyId     │ │ • avgPrice       │ │ • symbol         │            │ │
│  │  │ • strategyName   │ │ • currentValue   │ │ • quantity       │            │ │
│  │  │ • balance        │ │ • unrealizedPnL  │ │ • price          │            │ │
│  │  │ • positions[]    │ │                  │ │ • timestamp      │            │ │
│  │  │ • isActive       │ │                  │ │                  │            │ │
│  │  └──────────────────┘ └──────────────────┘ └──────────────────┘            │ │
│  │                                                                            │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌────────────────────────────────────────────────────────────────────────────┐ │
│  │                         VALUE OBJECTS                                      │ │
│  │                                                                            │ │
│  │  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐            │ │
│  │  │     Asset        │ │   PortfolioId    │ │   StrategyId     │            │ │
│  │  │                  │ │                  │ │                  │            │ │
│  │  │ • amount         │ │ • value (UUID)   │ │ • value (UUID)   │            │ │
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