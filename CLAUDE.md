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
- **Testing**: JUnit (expected)

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
- **MockExchangeAdapter**: Exchange integration adapter
- **Database Configuration**: Data persistence layer
- **REST Controllers**: HTTP API endpoints
- **Scheduling System**: Automated task execution

### Additional Components
- **Backtesting Engine**: Historical strategy validation
- **Historical Data Generator**: Market data simulation
- **Configuration System**: Application settings management
- **Structured Logging**: Observability and monitoring

## Development Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

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

## Project Tasks (from GitHub Project Board)

**Phase 1 - Foundation:**
- [1.0] Criação da aplicação Spring Boot com Gradle e Docker Compose
- [1.1] Estrutura base Spring Boot hexagonal
- [1.2] Modelar entidades do domínio  
- [1.3] Definir portas do domínio

**Phase 2 - Application Layer:**
- [2.1] TradingOrchestrator
- [2.2] Sistema modular de estratégias
- [2.3] Services de aplicação

**Phase 3 - Infrastructure:**
- [3.1] MockExchangeAdapter
- [3.2] Configuração de banco de dados
- [3.3] Sistema de agendamento
- [3.4] Controllers REST

**Phase 4 - Analysis:**
- [4.1] Engine de backtesting
- [4.2] Gerador de dados históricos

**Phase 5 - Operations:**
- [5.1] Sistema de configuração
- [5.2] Logging estruturado

## Security Considerations

- Never commit API keys, private keys, or sensitive credentials
- Use Spring profiles for environment-specific configuration
- Implement proper error handling for exchange API failures
- Use @ConfigurationProperties for secure configuration binding
- Consider rate limiting and connection pooling for exchange APIs