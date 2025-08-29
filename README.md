# Ctrade

Aplicação de trading de criptomoedas desenvolvida com Spring Boot e arquitetura hexagonal. O sistema permite gerenciar ordens de compra e venda de criptomoedas com uma arquitetura robusta e bem testada.

## 🚀 Tecnologias

- **Framework**: Spring Boot 3.5.4
- **Linguagem**: Java 24
- **Build Tool**: Gradle
- **Arquitetura**: Hexagonal (Ports and Adapters)
- **Containerização**: Docker & Docker Compose
- **Banco de Dados**: H2 (desenvolvimento), JPA/Hibernate
- **Documentação**: Swagger/OpenAPI 3
- **Testes**: JUnit 5, Mockito, Spring Test, AssertJ

## 🏗️ Arquitetura

O projeto segue a **Arquitetura Hexagonal** com clara separação de responsabilidades:

### Camada de Domínio
- **Entidades**: `TradingPair`, `Order`
- **Value Objects**: `Price`
- **Portas**: `ExchangePort` (interfaces que definem contratos)
- **Lógica de Negócio**: Regras de validação e comportamentos isolados

### Camada de Aplicação
- **Services**: `TradingService` (orquestra operações de trading)
- **Casos de Uso**: Implementação das regras de negócio
- **Coordenação**: Entre domínio e infraestrutura

### Camada de Infraestrutura
- **Exchange Adapters**: Implementações modulares por exchange
  - **Mock**: `MockExchangeAdapter`, `MockWebSocketAdapter` (simulação para desenvolvimento)
  - **Binance**: `BinanceWebSocketAdapter`, `BinanceWebSocketListener` (integração real)
- **Stream Processing**: Sistema modular de processamento de streams
  - **Strategy Pattern**: `StreamProcessingStrategy` para diferentes exchanges
  - **Binance Strategy**: `BinanceStreamProcessingStrategy` com `TickerStreamProcessor`
  - **Flexibilidade**: Suporte a streams individuais (@ticker) e arrays (!ticker@arr)
- **WebSocket Infrastructure**: Arquitetura resiliente para conexões em tempo real
  - **Connection Management**: `ConnectionManager`, `ReconnectionStrategy`
  - **Circuit Breaker**: `WebSocketCircuitBreaker` para prevenção de falhas
  - **Observer Pattern**: Notificações automáticas com listeners
- **Controllers REST**: API endpoints para trading, health check e métricas
- **Configuração**: Exception handlers, validação, propriedades por profile
- **Persistência**: Configuração H2 para desenvolvimento

## 📁 Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/marmitt/ctrade/
│   │   ├── CtradeApplication.java
│   │   ├── controller/
│   │   │   ├── TradingController.java
│   │   │   ├── HealthController.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── dto/
│   │   │       ├── OrderRequest.java
│   │   │       ├── OrderResponse.java
│   │   │       └── PriceResponse.java
│   │   ├── application/
│   │   │   └── service/
│   │   │       ├── TradingService.java
│   │   │       ├── TradingAuditService.java
│   │   │       ├── WebSocketService.java
│   │   │       ├── PriceCacheService.java
│   │   │       └── HealthCheckService.java
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── TradingPair.java
│   │   │   │   ├── Order.java
│   │   │   │   └── TradingAuditLog.java
│   │   │   ├── valueobject/
│   │   │   │   └── Price.java
│   │   │   ├── port/
│   │   │   │   ├── ExchangePort.java
│   │   │   │   ├── WebSocketPort.java
│   │   │   │   └── ExchangeWebSocketAdapter.java
│   │   │   ├── dto/
│   │   │   │   ├── PriceUpdateMessage.java
│   │   │   │   └── OrderUpdateMessage.java
│   │   │   └── listener/
│   │   │       ├── PriceUpdateListener.java
│   │   │       └── OrderUpdateListener.java
│   │   ├── config/
│   │   │   └── OpenApiConfig.java
│   │   └── infrastructure/
│   │       ├── exchange/
│   │       │   ├── mock/
│   │       │   │   ├── MockExchangeAdapter.java
│   │       │   │   └── MockWebSocketAdapter.java
│   │       │   └── binance/
│   │       │       ├── BinanceWebSocketAdapter.java
│   │       │       ├── BinanceWebSocketListener.java
│   │       │       ├── strategy/
│   │       │       │   ├── BinanceStreamProcessingStrategy.java
│   │       │       │   └── processor/
│   │       │       │       └── TickerStreamProcessor.java
│   │       │       └── dto/
│   │       │           └── BinanceTickerMessage.java
│   │       ├── websocket/
│   │       │   ├── AbstractWebSocketAdapter.java
│   │       │   ├── AbstractWebSocketListener.java
│   │       │   ├── ConnectionManager.java
│   │       │   ├── ReconnectionStrategy.java
│   │       │   ├── WebSocketCircuitBreaker.java
│   │       │   └── WebSocketConnectionHandler.java
│   │       ├── config/
│   │       │   └── WebSocketProperties.java
│   │       └── repository/
│   │           └── TradingAuditLogRepository.java
│   └── resources/
│       ├── application.yml
│       └── application-binance.yml
└── test/
    └── java/com/marmitt/ctrade/
        ├── controller/
        │   ├── TradingControllerIntegrationTest.java
        │   ├── HealthControllerIntegrationTest.java
        │   ├── SystemHealthControllerIntegrationTest.java
        │   ├── MetricsControllerIntegrationTest.java
        │   └── PriceAlertControllerIntegrationTest.java
        ├── application/service/
        │   ├── TradingServiceTest.java
        │   ├── TradingAuditServiceTest.java
        │   ├── PriceCacheServiceTest.java
        │   ├── PriceCacheHistoryServiceTest.java
        │   ├── PriceCacheServiceTTLTest.java
        │   ├── HealthCheckServiceTest.java
        │   └── PriceListenersUnitTest.java
        ├── domain/
        │   ├── entity/
        │   │   ├── TradingPairTest.java
        │   │   └── OrderTest.java
        │   └── valueobject/
        │       └── PriceTest.java
        ├── infrastructure/
        │   └── exchange/
        │       ├── mock/
        │       │   └── MockWebSocketAdapterTest.java
        │       │   └── MockExchangeAdapterTest.java        
        │       └── binance/
        │           ├── strategy/
        │           │   ├── processor/
        │           │   │   └── BinanceStreamProcessingStrategyTest.java
        │           │   └── BinanceWebSocketAdapterIntegrationTest.java
        │           ├── BinanceWebSocketAdapterTest.java
        │           ├── BinanceWebSocketAdapterIntegrationTest.java
        │           ├── BinanceWebSocketListenerTest.java
        └── CtradeApplicationTests.java
```

## 🔧 Funcionalidades Implementadas

### Trading Core
- ✅ Criação e validação de pares de trading (BTC/USD, ETH/USD, etc.)
- ✅ Gerenciamento de ordens (compra/venda, market/limit)
- ✅ Cálculo de valores totais e validações
- ✅ Sistema de status de ordens (PENDING, FILLED, CANCELLED)
- ✅ Value Object para preços com aritmética decimal segura

### Stream Processing Architecture
- ✅ **Strategy Pattern**: Sistema modular para diferentes exchanges
- ✅ **Stream Processors**: Processadores especializados por tipo de stream
- ✅ **Flexible Parsing**: Suporte a streams únicos e arrays da Binance
- ✅ **Domain Integration**: Conversão automática para `PriceUpdateMessage`

### Validação e Tratamento de Erros
- ✅ Validação de entrada com Bean Validation
- ✅ Tratamento global de exceções
- ✅ Respostas de erro padronizadas
- ✅ Validações de regras de negócio

### Sistema de Auditoria de Trading
- ✅ Logs detalhados de todas as operações de trading
- ✅ Rastreamento de ordens com ID único de request
- ✅ Auditoria de ações (criação, cancelamento, consultas)
- ✅ Registro de erros e validações com contexto
- ✅ Persistência em banco de dados com JPA
- ✅ Logs estruturados para análise e compliance

### Sistema WebSocket e Notificações em Tempo Real
- ✅ **WebSocket Infrastructure**: Arquitetura robusta com classes abstratas
- ✅ **Connection Management**: `ConnectionManager` para gerenciamento centralizado
- ✅ **Resilient Reconnection**: `ReconnectionStrategy` com backoff exponencial
- ✅ **Circuit Breaker**: `WebSocketCircuitBreaker` para prevenção de falhas
- ✅ **Price Cache**: Cache histórico de preços com TTL e limpeza automática
- ✅ **Mock WebSocket Adapter**: Simulação para desenvolvimento com preços automáticos
- ✅ **Binance WebSocket Adapter**: Integração real com Binance usando OkHttp
- ✅ **Stream Processing**: Sistema modular com strategy pattern para diferentes exchanges
- ✅ **Flexible Ticker Processing**: Suporte a streams individuais (@ticker) e arrays (!ticker@arr)
- ✅ **Profile Configuration**: Configuração específica por ambiente (mock/binance)
- ✅ **Price Update Listeners**: Sistema de notificação automática para mudanças de preço
- ✅ **Order Update Listeners**: Notificações de status de ordens em tempo real
- ✅ **Health Monitoring**: Monitoramento de status do sistema (cache + WebSocket)

### Documentação da API
- ✅ Swagger/OpenAPI 3 integrado
- ✅ Interface interativa para testes
- ✅ Documentação automática dos endpoints
- ✅ Exemplos de request/response

## 🧪 Testes

O projeto possui **100+ testes** cobrindo todas as camadas:

### Testes Unitários
- **Domain Layer**: Entidades, Value Objects, validações
- **Application Layer**: Services com mocks
- **Infrastructure Layer**: Adapters e integrações

### Testes de Integração
- **Controller Layer**: APIs REST com MockMvc
- **Workflow Completo**: Cenários end-to-end

### Cobertura de Testes
- ✅ Cenários de sucesso e erro
- ✅ Validação de entrada e regras de negócio
- ✅ Tratamento de exceções
- ✅ Workflows completos de trading

## 🚀 Como Executar

### Pré-requisitos
- Java 24
- Gradle

### Comandos

```bash
# Build da aplicação
./gradlew build

# Executar testes
./gradlew test

# Executar a aplicação (profile padrão - mock)
./gradlew bootRun

# Executar com profile Binance
./gradlew bootRun --args='--spring.profiles.active=binance'

# Gerar JAR
./gradlew bootJar
```

### Configuração de Profiles

#### Profile via Env
- SPRING_PROFILES_ACTIVE=binance,strategies
- SPRING_PROFILES_ACTIVE=mock,strategies

#### Profile Mock (padrão)
- Usa `MockWebSocketAdapter` com simulação automática de preços
- Ideal para desenvolvimento e testes

#### Profile Binance
- Usa `BinanceWebSocketAdapter` com conexão real à Binance
- Configure no IntelliJ: VM options `-Dspring.profiles.active=binance`
- Configuração em `application-binance.yml`

### Com Docker Compose

```bash
# Subir a aplicação
docker-compose up -d

# Ver logs
docker-compose logs -f

# Parar
docker-compose down
```

## 📡 Exemplos de Uso da API

### Documentação Interativa (Swagger UI)
```bash
# Acesse a documentação interativa da API
http://localhost:8080/swagger-ui/index.html

# Endpoint da especificação OpenAPI
http://localhost:8080/v3/api-docs
```


## 📈 Sistema de Estratégias de Trading

O sistema de estratégias permite automatizar decisões de trading baseadas em dados de mercado em tempo real. As estratégias são executadas automaticamente sempre que novos dados de preço chegam via WebSocket.

### Como Funciona a Execução de uma Estratégia

#### 1. **Fluxo de Execução**

```
WebSocket (Price Update) → TradingStrategyListener → TradingOrchestrator → Estratégias Ativas → Geração de Sinais → Execução de Ordens
```

1. **Recepção de Dados**: O `BinanceWebSocketListener` ou `MockWebSocketAdapter` recebe atualizações de preço
2. **Conversão**: O `TradingStrategyListener` converte `PriceUpdateMessage` em `MarketData`
3. **Orquestração**: O `TradingOrchestrator` executa todas as estratégias ativas
4. **Análise**: Cada estratégia analisa os dados de mercado e o portfólio atual
5. **Geração de Sinais**: As estratégias geram sinais de BUY, SELL ou HOLD
6. **Validação**: Sinais são validados (preço, quantidade, limites)
7. **Execução**: Ordens válidas são enviadas para a exchange

#### 2. **Componentes Principais**

- **`TradingStrategy`**: Interface que define o contrato das estratégias
- **`StrategyRegistry`**: Gerencia registro e ativação de estratégias
- **`TradingOrchestrator`**: Coordena a execução das estratégias
- **`StrategyAutoConfiguration`**: Registra estratégias automaticamente em memoria
- **`TradingStrategyListener`**: Conecta WebSocket às estratégias

### Parâmetros e Configuração

#### **StrategySignal** (Sinal Gerado pela Estratégia)

```java
public class StrategySignal {
    private SignalType type;           // BUY, SELL, HOLD
    private TradingPair pair;          // Par de trading (ex: BTC/USDT)
    private BigDecimal quantity;       // Quantidade a negociar
    private BigDecimal price;          // Preço limite
    private String reason;             // Motivo do sinal
    private String strategyName;       // Nome da estratégia
    private LocalDateTime timestamp;   // Timestamp do sinal
}
```

#### **Parâmetros de Configuração** (`application-strategies.yml`)

```yaml
strategies:
  auto-register: true  # Ativa registro automático
  
  pair-trading:
    enabled: true
    parameters:
      correlation-threshold: 0.8     # Correlação mínima entre pares
      spread-threshold: 0.02         # Spread mínimo para trade
      stop-loss: 0.05               # Stop loss (5%)
      take-profit: 0.10             # Take profit (10%)
      lookback-period: 20           # Períodos para análise
    max-order-value: 1000.00        # Valor máximo por ordem
    min-order-value: 10.00          # Valor mínimo por ordem
    risk-limit: 0.02                # Limite de risco (2% do portfólio)
```

#### **Parâmetros Explicados**

- **`enabled`**: Ativa/desativa a estratégia
- **`correlation-threshold`**: Correlação mínima entre ativos para pair trading
- **`spread-threshold`**: Diferença mínima de preço para executar trade
- **`stop-loss`**: Percentual de perda máxima antes de fechar posição
- **`take-profit`**: Percentual de lucro para fechar posição
- **`lookback-period`**: Número de períodos históricos para análise
- **`max-order-value`**: Valor máximo de uma ordem individual
- **`min-order-value`**: Valor mínimo de uma ordem individual
- **`risk-limit`**: Percentual máximo do portfólio em risco

### Como Adicionar uma Nova Estratégia

#### **Passo 1: Implementar a Interface TradingStrategy**

```java
@Component
public class MinhaEstrategia implements TradingStrategy {
    
    private final StrategyProperties.StrategyConfig config;
    
    public MinhaEstrategia(StrategyProperties.StrategyConfig config) {
        this.config = config;
    }
    
    @Override
    public String getStrategyName() {
        return "minha-estrategia";
    }
    
    @Override
    public StrategySignal analyze(MarketData marketData, Portfolio portfolio) {
        // 1. Obter dados de preço
        TradingPair pair = new TradingPair("BTC", "USDT");
        BigDecimal currentPrice = marketData.getPriceFor(pair);
        
        // 2. Implementar lógica da estratégia
        if (deveComprar(currentPrice, portfolio)) {
            return StrategySignal.builder()
                .type(SignalType.BUY)
                .pair(pair)
                .quantity(calcularQuantidade(portfolio))
                .price(currentPrice)
                .reason("Condição de compra atendida")
                .strategyName(getStrategyName())
                .timestamp(LocalDateTime.now())
                .build();
        }
        
        // 3. Retornar null ou sinal HOLD se não houver ação
        return null;
    }
    
    private boolean deveComprar(BigDecimal price, Portfolio portfolio) {
        // Implementar lógica específica
        BigDecimal threshold = config.getParameters().get("price-threshold");
        return price.compareTo(threshold) < 0;
    }
    
    private BigDecimal calcularQuantidade(Portfolio portfolio) {
        // Calcular quantidade baseada no portfólio e configurações
        BigDecimal balance = portfolio.getBalance("USDT");
        BigDecimal riskLimit = config.getRiskLimit();
        return balance.multiply(riskLimit);
    }
}
```

#### **Passo 2: Registrar no StrategyAutoConfiguration**

```java
// Em StrategyAutoConfiguration.java, método createStrategyInstance()
private TradingStrategy createStrategyInstance(String strategyName, StrategyProperties.StrategyConfig config) {
    switch (strategyName.toLowerCase()) {
        case "pairtradingstrategy":
        case "pair-trading":
            return new PairTradingStrategy(config);
            
        case "minhaestrategia":
        case "minha-estrategia":
            return new MinhaEstrategia(config);
        
        default:
            log.warn("Unknown strategy type in configuration: {}", strategyName);
            return null;
    }
}
```

#### **Passo 3: Adicionar Configuração**

```yaml
# application-strategies.yml
strategies:
  auto-register: true
  
  minha-estrategia:
    enabled: true
    parameters:
      price-threshold: 45000.00
      custom-param: 0.15
    max-order-value: 500.00
    min-order-value: 10.00
    risk-limit: 0.01
```

#### **Passo 4: Criar Testes Unitários**

```java
@ExtendWith(MockitoExtension.class)
class MinhaEstrategiaTest {
    
    @Test
    void shouldGenerateBuySignalWhenPriceBelowThreshold() {
        // Given
        StrategyProperties.StrategyConfig config = createConfig();
        MinhaEstrategia strategy = new MinhaEstrategia(config);
        
        MarketData marketData = createMarketDataWithPrice(new BigDecimal("40000"));
        Portfolio portfolio = createPortfolioWithBalance(new BigDecimal("1000"));
        
        // When
        StrategySignal signal = strategy.analyze(marketData, portfolio);
        
        // Then
        assertNotNull(signal);
        assertEquals(SignalType.BUY, signal.getType());
        assertEquals("minha-estrategia", signal.getStrategyName());
    }
}
```

#### **Passo 5: Ativação Automática**

A estratégia será automaticamente:
1. **Registrada** pelo `StrategyAutoConfiguration` na inicialização
2. **Ativada** se `enabled: true` na configuração
3. **Executada** sempre que chegarem dados de preço via WebSocket

### Monitoramento e Logs

```bash
# Logs de execução de estratégias
2024-01-15 10:30:15 [INFO] TradingOrchestrator - Executing 3 active strategies
2024-01-15 10:30:15 [INFO] TradingOrchestrator - Strategy minha-estrategia generated signal: BUY for pair BTCUSDT
2024-01-15 10:30:15 [INFO] TradingOrchestrator - Submitting order: BUY 0.01 BTCUSDT at 45000
```

### APIs para Gerenciamento

```bash
# Ativar estratégia
POST /api/strategies/minha-estrategia/enable

# Desativar estratégia  
POST /api/strategies/minha-estrategia/disable

# Listar estratégias ativas
GET /api/strategies/active
```

## 🎯 Status do Projeto

O projeto está em desenvolvimento ativo com sistema de trading automatizado baseado em estratégias modulares. 

### 📊 **Funcionalidades Principais Implementadas:**
- Sistema de estratégias de trading em tempo real
- WebSocket integration com Binance e mock adapters  
- Arquitetura hexagonal com separação clara de responsabilidades
- APIs REST para operações de trading e monitoramento
- Sistema de auditoria e logging estruturado
- Documentação completa de APIs com Swagger

### 🚀 **Próximos Desenvolvimentos:**
Para detalhes completos sobre funcionalidades implementadas, em desenvolvimento e roadmap futuro, consulte:

- **[📋 Estratégia de Implementação P&L](docs/STRATEGY-PL-IMPLEMENTATION.md)** - Plano detalhado e status atual
- **[🎯 Próximos Passos](docs/NEXT-STEPS-PL-IMPLEMENTATION.md)** - Roadmap de implementação

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma branch para sua feature (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Abra um Pull Request

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.

---

**Desenvolvido com ❤️ usando Arquitetura Hexagonal e boas práticas de desenvolvimento**
