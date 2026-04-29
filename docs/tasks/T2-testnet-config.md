# T2 — Configuração de perfil testnet

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T1  
**Status:** Pendente

---

## Descrição

O adapter Binance atual tem URLs hardcoded para produção (`wss://stream.binance.com:9443`) na classe `Configuration.java`. API keys também não têm ponto de injeção.

Esta tarefa parametriza todas as URLs e credenciais do adapter Binance, cria um perfil Spring para testnet e define o contrato de variáveis de ambiente necessárias para staging.

A Binance testnet usa o domínio `testnet.binance.vision` tanto para WebSocket quanto para REST. As API keys da testnet são distintas das de produção e obtidas em [testnet.binance.vision](https://testnet.binance.vision/).

---

## Escopo técnico

**Arquivos principais:**
- `adapter-binance/src/main/java/com/marmitt/binance/Configuration.java` — atualmente hardcoded
- `spring-application/src/main/resources/application.yml` — adicionar bloco `binance:`
- `spring-application/src/main/resources/application-testnet.yml` — novo arquivo de perfil
- `docker-compose.yml` — adicionar variáveis de ambiente para API keys
- `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/BinanceExchangeAdapter.java` — receber configuração por construtor

**Variáveis de ambiente a definir:**
```
BINANCE_WS_BASE_URL       wss://stream.binance.com:9443  (padrão produção)
BINANCE_REST_BASE_URL     https://api.binance.com        (padrão produção)
BINANCE_API_KEY           (obrigatório, sem padrão)
BINANCE_API_SECRET        (obrigatório, sem padrão)
```

**Valores para testnet:**
```
BINANCE_WS_BASE_URL       wss://testnet.binance.vision
BINANCE_REST_BASE_URL     https://testnet.binance.vision
```

---

## Critérios de aceitação

1. `Configuration.java` não contém mais nenhuma URL hardcoded; recebe valores por construtor ou propriedades injetadas.
2. `application.yml` define os valores padrão de produção para todas as URLs Binance.
3. `application-testnet.yml` sobrescreve com URLs da testnet; ao ativar o perfil `testnet` (`-Dspring.profiles.active=testnet`), a aplicação usa as URLs corretas sem nenhuma alteração de código.
4. API key e API secret são lidos exclusivamente de variáveis de ambiente (`BINANCE_API_KEY`, `BINANCE_API_SECRET`); a aplicação falha no startup com mensagem clara caso estejam ausentes.
5. `docker-compose.yml` documenta as variáveis de ambiente esperadas (valores de exemplo, sem secrets reais).
6. Nenhuma URL ou credencial Binance é hardcoded em qualquer arquivo commitado.
7. A aplicação sobe normalmente com `MOCK` sem exigir as variáveis Binance.
