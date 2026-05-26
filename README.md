# CTrace - Cryptocurrency Trading Application

## Praticas de Desenvolvimento para Agentes

As praticas de desenvolvimento e review do projeto estao centralizadas no diretorio `/.ai`.

Arquivos principais:

- `/.ai/README.md`: guia principal e ordem de leitura
- `/.ai/architecture.md`: fronteiras e responsabilidades por modulo
- `/.ai/coding-standards.md`: padroes de implementacao
- `/.ai/review.md`: heuristicas de review
- `/.ai/agents/code-review-agent.md`: agente compartilhado de review para Codex e Claude
- `/.ai/skills/pr-review-publisher/`: skill compartilhada para publicar findings em PR
- `/.ai/validation.md`: comandos e estrategia de validacao

Os arquivos `AGENTS.md` e `CLAUDE.md` devem apontar para `/.ai` como fonte canonica dessas regras.

Sistema modular de trading com arquitetura hexagonal para conexão WebSocket com múltiplas exchanges.

## Tecnologias

- **Framework**: Spring Boot 3.5.4
- **Linguagem**: Java 21
- **Build**: Gradle multi-módulo
- **WebSocket**: OkHttp3
- **Database**: H2 (em memória)
- **Documentação**: Swagger/OpenAPI 3

## Arquitetura

### Módulos

- **core/**: Domínio, DTOs, ports, use cases
- **spring-application/**: Controllers REST, services, configuração Spring
- **adapter-binance/**: Integração Binance WebSocket
- **adapter-coinbase/**: Integração Coinbase WebSocket
- **adapter-mock/**: Simulação para desenvolvimento
- **strategy/**: Algoritmos de trading (SMA implementado)

## Estrutura

```
ctrade/
├── core/                           # Domínio e contratos
├── spring-application/             # Aplicação Spring Boot
├── adapter-binance/               # Integração Binance
├── adapter-coinbase/              # Integração Coinbase
├── adapter-mock/                  # Mock para testes
├── strategy/                      # Estratégias de trading
├── build.gradle                   # Build root
└── settings.gradle                # Configuração módulos
```

## Funcionalidades

- WebSocket management (connect/disconnect/status)
- Market data streaming (ticker, book, trades)
- Order management via WebSocket
- Multiple exchange support (Binance, Coinbase, Mock)
- Event-driven architecture
- Strategy framework (SMA implementado)

## Como Executar

### Pré-requisitos
- Java 21
- Gradle

### Comandos

```bash
# Build todos os módulos
./gradlew build

# Executar aplicação
./gradlew :spring-application:bootRun

# Build módulo específico
./gradlew :core:build
```

### Padrao local para Gradle

Para execucao local e pelos agentes, o projeto passa a usar um unico cache Gradle do repositorio:

- `GRADLE_USER_HOME=.gradle-local`
- nao criar diretorios paralelos como `.gradle-local-pr`, `.gradle-local-ci` ou equivalentes
- em caso de lock transitório, reutilizar o mesmo cache com retry

Use o wrapper documentado abaixo no PowerShell:

```powershell
./scripts/gradle-run.ps1 -q :core:compileJava
./scripts/gradle-run.ps1 -q :spring-application:test
./scripts/gradle-run.ps1 build
```

O script `scripts/gradle-run.ps1` fixa `GRADLE_USER_HOME` em `.gradle-local` e faz retry curto quando encontrar lock de execucao concorrente.

### Configuração

- **Porta**: 8080
- **Database**: H2 em memória
- **Swagger**: `/swagger-ui/index.html`

## APIs

### Principais Endpoints

```bash
# WebSocket Management
POST /websocket/connect
POST /websocket/disconnect
GET  /websocket/stats

# Market Data
POST /websocket/exchange/market-data/subscribe
POST /websocket/exchange/market-data/unsubscribe

# Order Management  
POST /websocket/exchange/orders/create
POST /websocket/exchange/orders/cancel
```

### Documentação
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI Spec**: `http://localhost:8080/v3/api-docs`

## Status

### Implementado
- Sistema WebSocket multi-exchange
- Market data streaming
- Order management
- Strategy framework (SMA)

### Em desenvolvimento
- Sistema P&L
- Performance metrics
- Risk management
- Backtesting engine


