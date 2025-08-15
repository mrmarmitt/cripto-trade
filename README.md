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
- **Exchange Adapters**: Implementações específicas por exchange
  - **Mock**: `MockExchangeAdapter`, `MockWebSocketAdapter` (simulação para desenvolvimento)
  - **Binance**: `BinanceWebSocketAdapter`, `BinanceWebSocketListener` (integração real)
- **WebSocket System**: Sistema de notificações em tempo real com Observer pattern
- **Controllers REST**: API endpoints para trading, health check e métricas
- **Configuração**: Exception handlers, validação, propriedades
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
│   │       │       └── dto/
│   │       │           └── BinanceTickerMessage.java
│   │       ├── websocket/
│   │       │   ├── ReconnectionStrategy.java
│   │       │   └── WebSocketCircuitBreaker.java
│   │       ├── config/
│   │       │   └── WebSocketProperties.java
│   │       └── repository/
│   │           └── TradingAuditLogRepository.java
│   └── resources/
│       └── application.yml
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
        │           └── BinanceWebSocketListenerTest.java
        ├── integration/
        │   └── TradingWorkflowIntegrationTest.java
        └── CtradeApplicationTests.java
```

## 🔧 Funcionalidades Implementadas

### Trading Core
- ✅ Criação e validação de pares de trading (BTC/USD, ETH/USD, etc.)
- ✅ Gerenciamento de ordens (compra/venda, market/limit)
- ✅ Cálculo de valores totais e validações
- ✅ Sistema de status de ordens (PENDING, FILLED, CANCELLED)
- ✅ Value Object para preços com aritmética decimal segura

### API REST
- ✅ **POST** `/api/trading/orders/buy` - Criar ordem de compra
- ✅ **POST** `/api/trading/orders/sell` - Criar ordem de venda  
- ✅ **POST** `/api/trading/orders/market-buy` - Ordem de compra a mercado
- ✅ **DELETE** `/api/trading/orders/{orderId}` - Cancelar ordem
- ✅ **GET** `/api/trading/orders/{orderId}` - Status da ordem
- ✅ **GET** `/api/trading/orders/active` - Listar ordens ativas
- ✅ **GET** `/api/trading/price/{baseCurrency}/{quoteCurrency}` - Preço atual
- ✅ **GET** `/health` - Health check
- ✅ **GET** `/api/system/health` - Health check detalhado com cache e WebSocket
- ✅ **GET** `/api/metrics/summary` - Métricas do sistema em tempo real
- ✅ **GET** `/api/metrics/prices` - Histórico de preços em cache
- ✅ **POST** `/api/prices/alerts` - Criar alertas de preço

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
- ✅ **WebSocket Service**: Gerenciamento de conexões WebSocket com Observer pattern
- ✅ **Price Cache**: Cache histórico de preços com TTL e limpeza automática
- ✅ **Mock WebSocket Adapter**: Simulação para desenvolvimento com preços automáticos
- ✅ **Binance WebSocket Adapter**: Integração real com Binance usando OkHttp
- ✅ **Exponential Backoff**: Estratégia de reconexão automática resiliente
- ✅ **Circuit Breaker**: Prevenção de falhas cascata em conexões WebSocket
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

# Executar a aplicação
./gradlew bootRun

# Gerar JAR
./gradlew bootJar
```

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

### Consultar Preço Atual
```bash
curl -X GET http://localhost:8080/api/trading/price/BTC/USD
```

### Criar Ordem de Compra
```bash
curl -X POST http://localhost:8080/api/trading/orders/buy \
  -H "Content-Type: application/json" \
  -d '{
    "tradingPair": "BTC/USD",
    "quantity": 0.1,
    "price": 50000
  }'
```

### Criar Ordem de Venda
```bash
curl -X POST http://localhost:8080/api/trading/orders/sell \
  -H "Content-Type: application/json" \
  -d '{
    "tradingPair": "BTC/USD", 
    "quantity": 0.05,
    "price": 52000
  }'
```

### Ordem a Mercado
```bash
curl -X POST http://localhost:8080/api/trading/orders/market-buy \
  -H "Content-Type: application/json" \
  -d '{
    "tradingPair": "BTC/USD",
    "quantity": 0.01
  }'
```

### Listar Ordens Ativas
```bash
curl -X GET http://localhost:8080/api/trading/orders/active
```

### Cancelar Ordem
```bash
curl -X DELETE http://localhost:8080/api/trading/orders/{orderId}
```

## 🎯 Status do Projeto

### ✅ Implementado

**[1.0] Criação da aplicação Spring Boot com Gradle e Docker Compose**
- [x] Estrutura básica Spring Boot
- [x] Configuração Gradle  
- [x] Docker e Docker Compose
- [x] Endpoint de health check

**[1.1] Estrutura base Spring Boot hexagonal**
- [x] Arquitetura hexagonal implementada
- [x] Separação clara de camadas
- [x] Inversão de dependências

**[1.2] Modelar entidades do domínio**
- [x] TradingPair entity
- [x] Order entity com enums
- [x] Price value object
- [x] Validações de domínio

**[1.3] Definir portas do domínio**  
- [x] ExchangePort interface
- [x] Contratos bem definidos

**[2.1] Sistema de Trading**
- [x] TradingService implementado
- [x] Operações de compra/venda
- [x] Gerenciamento de ordens

**[3.1] MockExchangeAdapter**
- [x] Simulação de exchange
- [x] Preços dinâmicos
- [x] Processamento de ordens

**[3.4] Controllers REST**
- [x] TradingController completo
- [x] DTOs de request/response
- [x] Tratamento de exceções

**[Sistema de Auditoria e Compliance]**
- [x] TradingAuditLog entity com JPA
- [x] TradingAuditService para logs de auditoria
- [x] TradingAuditLogRepository para persistência
- [x] Rastreamento completo de todas as operações
- [x] Logs de erros e validações com contexto

**[Documentação da API]**
- [x] OpenApiConfig com Swagger/OpenAPI 3
- [x] Interface Swagger UI interativa
- [x] Documentação automática dos endpoints
- [x] Especificação OpenAPI acessível via REST

**[Sistema WebSocket e Notificações Tempo Real]**
- [x] WebSocketService com Observer pattern e auto-discovery de listeners
- [x] PriceCacheService com histórico, TTL e limpeza automática
- [x] MockWebSocketAdapter com simuladores automáticos para desenvolvimento
- [x] BinanceWebSocketAdapter com OkHttp, Exponential Backoff e Circuit Breaker
- [x] Sistema de Health Check detalhado para cache e WebSocket
- [x] REST endpoints para métricas do sistema e histórico de preços
- [x] Notificações automáticas de price/order updates via listeners

**[Testes Abrangentes]**
- [x] 100+ testes unitários e integração
- [x] Cobertura completa de todas as camadas incluindo WebSocket
- [x] Cenários de sucesso e erro com mocks apropriados

### 🔄 Próximos Passos

**[2.2] Sistema modular de estratégias**
- [ ] Interface de estratégias de trading
- [ ] Implementação de estratégias básicas

**[3.2] Configuração de banco de dados**
- [ ] Entidades JPA
- [ ] Repositories
- [ ] Migrations

**[3.3] Sistema de agendamento**
- [ ] Jobs para processamento de ordens
- [ ] Monitoramento de preços

**[4.1] Engine de backtesting**
- [ ] Simulação histórica
- [ ] Métricas de performance

**[4.2] Gerador de dados históricos**
- [ ] Simulação de dados de mercado
- [ ] Integração com APIs reais

**[5.1] Sistema de configuração**
- [ ] Configurações dinâmicas
- [ ] Profiles avançados

**[5.2] Logging estruturado**
- [ ] Logs estruturados JSON
- [ ] Métricas e observabilidade

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