# cripto-trade

Aplicação de trading de criptomoedas desenvolvida com Spring Boot e arquitetura hexagonal.

## Tecnologias

- **Framework**: Spring Boot 3.2.0
- **Linguagem**: Java 21
- **Build Tool**: Gradle 8.5
- **Arquitetura**: Hexagonal (Ports and Adapters)
- **Containerização**: Docker & Docker Compose
- **Banco de Dados**: H2 (desenvolvimento) / PostgreSQL (produção)

## Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/mrmarmitt/criptotrade/
│   │   ├── CriptoTradeApplication.java
│   │   └── controller/
│   │       └── HealthController.java
│   └── resources/
│       ├── application.yml
│       └── application-prod.yml
└── test/
    └── java/com/mrmarmitt/criptotrade/
        └── CriptoTradeApplicationTests.java
```

## Como Executar

### Localmente com Gradle

```bash
# Build da aplicação
./gradlew build

# Executar a aplicação
./gradlew bootRun

# Executar testes
./gradlew test
```

### Com Docker Compose

#### Ambiente de Desenvolvimento
```bash
# Usar H2 em memória (container de desenvolvimento)
docker-compose -f docker-compose.dev.yml up -d

# Verificar logs
docker-compose -f docker-compose.dev.yml logs

# Parar o ambiente
docker-compose -f docker-compose.dev.yml down
```

**Nota**: O ambiente de desenvolvimento está configurado como um container preparado para desenvolvimento Spring Boot. O build do Gradle será implementado na próxima fase.

#### Ambiente de Produção
```bash
# Com PostgreSQL
docker-compose up -d
```

## Endpoints Disponíveis

- **Health Check**: `GET /api/status`
- **H2 Console** (apenas dev): `http://localhost:8080/h2-console`
- **Actuator Health**: `http://localhost:8080/actuator/health`
- **PgAdmin** (apenas prod): `http://localhost:5050`

## Configuração

### Perfis Spring
- **dev**: Usa H2 em memória, logs detalhados
- **prod**: Usa PostgreSQL, logs reduzidos

### Variáveis de Ambiente
- `DB_USERNAME`: Usuário do banco PostgreSQL
- `DB_PASSWORD`: Senha do banco PostgreSQL

## Status do Projeto

✅ **[1.0] Criação da aplicação Spring Boot com Gradle e Docker Compose**
- [x] Estrutura básica Spring Boot
- [x] Configuração Gradle
- [x] Docker e Docker Compose
- [x] Profiles de desenvolvimento e produção
- [x] Endpoint de health check

## Próximos Passos

- [1.1] Estrutura base Spring Boot hexagonal
- [1.2] Modelar entidades do domínio
- [1.3] Definir portas do domínio