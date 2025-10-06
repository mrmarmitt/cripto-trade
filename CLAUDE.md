# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This is a cryptocurrency trading application built with Spring Boot using hexagonal architecture. The project follows a modular design with clear separation between domain, application, and infrastructure layers.

## Technology Stack

- **Framework**: Spring Boot (Java)
- **Architecture**: Hexagonal Architecture (Ports and Adapters)
- **Build Tool**: Gradle
- **Containerization**: Docker & Docker Compose
- **Database**: To be configured
- **Validation**: Manual testing and validation process

## Architecture Overview

The application follows hexagonal architecture with these main components:

### Domain Layer
- Core business entities and value objects
- Domain ports (interfaces) defining contracts
- Business logic isolated from external concerns

### Application Layer  
- **TradingOrchestrator**: Coordinates trading operations
- **Modular Strategy System**: Pluggable trading strategies
- **Application Services**: Business use cases implementation

### Infrastructure Layer
- **Exchange Adapters**: Modular exchange integrations organized by provider
  - **Mock Package**: `MockExchangeAdapter`, `MockWebSocketAdapter` for development/testing
  - **Binance Package**: `BinanceWebSocketAdapter`, `BinanceWebSocketListener` for real trading
- **WebSocket Infrastructure**: `ReconnectionStrategy`, `WebSocketCircuitBreaker` for resilience
- **Database Configuration**: Data persistence layer
- **REST Controllers**: HTTP API endpoints for trading, health, and metrics
- **Configuration System**: WebSocket properties and environment-specific settings

### Additional Components
- **Backtesting Engine**: Historical strategy validation
- **Historical Data Generator**: Market data simulation
- **Configuration System**: Application settings management
- **Structured Logging**: Observability and monitoring

## Development Commands

```bash
# Build the project
./gradlew build

# Check build and compilation
./gradlew check

# Run the application
./gradlew bootRun

# Package the application
./gradlew bootJar

# Run with Docker Compose
docker-compose up -d

# Stop Docker Compose
docker-compose down
```

## GitHub CLI Usage

Always use GitHub CLI (`gh`) for GitHub-related operations:

```bash
# View project board
gh project view 3 --owner mrmarmitt

# List issues
gh issue list

# Create new issue
gh issue create --title "Issue title" --body "Issue description"

# View project items
gh project item-list 3 --owner mrmarmitt

# Create pull request
gh pr create --title "PR title" --body "PR description"

# View pull requests
gh pr list

# Check project status
gh project view 3 --owner mrmarmitt --format json
```

## Project Management

Para acompanhar o progresso completo do projeto, status de implementação e roadmap detalhado:

📋 **[Estratégia de Implementação](docs/STRATEGY-PL-IMPLEMENTATION.md)** - Status completo do projeto  
🎯 **[Próximos Passos](docs/NEXT-STEPS-PL-IMPLEMENTATION.md)** - Roadmap detalhado de implementação

O sistema segue desenvolvimento incremental com foco atual no sistema de P&L e performance tracking.

## Package Organization

The project follows a well-organized package structure for exchange integrations:

### Exchange Packages
- **`infrastructure.exchange.mock`**: Mock implementations for development and testing
  - Contains `MockExchangeAdapter` and `MockWebSocketAdapter`
  - Provides automated simulators for price updates
  - See [Mock Package README](src/main/java/com/marmitt/ctrade/infrastructure/exchange/mock/README.md)

- **`infrastructure.exchange.binance`**: Real Binance exchange integration
  - Contains `BinanceWebSocketAdapter`, `BinanceWebSocketListener`, and DTOs
  - Implements exponential backoff and circuit breaker patterns
  - See [Binance Package README](src/main/java/com/marmitt/ctrade/infrastructure/exchange/binance/README.md)

### WebSocket Configuration
- Use `websocket.exchange=BINANCE` to activate Binance adapter
- Use `websocket.exchange=MOCK` (default) to use mock adapter  
- Both adapters implement the same interfaces for seamless switching

## Development and Validation Strategy

The application follows a validation-driven development approach where features are manually tested and validated according to business requirements. Testing implementation will be done on-demand based on validation results and specific needs identified during the development process.

## Documentation Requirements

### Project Structure Updates
When creating new classes, **MUST** update the project structure tree in the README.md file to reflect the new additions. This ensures the documentation stays current and helps team members understand the codebase organization.

- Add new classes to the appropriate section in the project tree
- Include brief descriptions for new packages or significant architectural changes
- Update any relevant architecture diagrams or documentation sections
- Maintain consistency with existing documentation format

## Security Considerations

- Never commit API keys, private keys, or sensitive credentials
- Use Spring profiles for environment-specific configuration
- Implement proper error handling for exchange API failures
- Use @ConfigurationProperties for secure configuration binding
- Consider rate limiting and connection pooling for exchange APIs