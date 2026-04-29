# T7 — Boot Readiness real

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T2, T5  
**Status:** Pendente

---

## Descrição

O `BinanceExchangeAdapter.checkBootReadiness()` atual verifica apenas se os componentes internos do adapter não são nulos:

```java
if (webSocketPort == null || receivedMessageProcessor == null ...) {
    return ExchangeBootReadiness.notReady(...);
}
return ExchangeBootReadiness.ready("BINANCE", "Binance adapter initialized for streaming.");
```

Isso não detecta falha real de conectividade. O boot vai passar na fase 1 mesmo com a Binance indisponível ou com API key inválida, e o problema só vai aparecer na primeira tentativa de operação.

Esta tarefa substitui a verificação por um ping real à API da Binance.

---

## Escopo técnico

**Endpoint a usar:**
- `GET /api/v3/ping` → retorna `{}` com HTTP 200 se a API está acessível (não requer autenticação)
- Usar este endpoint para verificar conectividade básica
- Usar `GET /api/v3/account` (autenticado) para verificar que a API key é válida e tem permissões de trading

**Sequência de verificações:**
1. `GET /api/v3/ping` — conectividade básica (sem auth)
2. `GET /api/v3/account` — validade da API key e permissões

**Arquivos a modificar:**
- `spring-application/.../config/exchange/BinanceExchangeAdapter.java` — substituir implementação de `checkBootReadiness()`
- O cliente REST criado em T5 (`BinanceRestClient`) deve ser reutilizado aqui

**Comportamento esperado:**

| Situação                             | Resultado                                                        |
|--------------------------------------|------------------------------------------------------------------|
| Binance acessível, API key válida    | `ExchangeBootReadiness.ready("BINANCE", "...")`                 |
| Binance inacessível (timeout/503)    | `notReady("BINANCE", "CONNECTIVITY_FAILURE", "...")`            |
| API key inválida (HTTP 401)          | `notReady("BINANCE", "INVALID_API_KEY", "...")`                 |
| API key sem permissão de trading     | `notReady("BINANCE", "INSUFFICIENT_PERMISSIONS", "...")`        |
| Erro inesperado                      | `notReady("BINANCE", "UNKNOWN_ERROR", mensagem do erro)`        |

**Timeout:** a verificação deve ter timeout de 10 segundos (configurável via `runner.boot.phase1.readiness-timeout-ms`).

---

## Critérios de aceitação

1. Com credenciais válidas e Binance testnet acessível, `checkBootReadiness()` retorna `ready`.
2. Com Binance inacessível (host errado ou sem rede), retorna `notReady` com código `CONNECTIVITY_FAILURE` sem lançar exceção não tratada.
3. Com API key inválida, retorna `notReady` com código `INVALID_API_KEY`.
4. A verificação respeita o timeout configurado; se exceder, retorna `notReady` com código `CONNECTIVITY_FAILURE`.
5. O resultado de `checkBootReadiness()` é logado com nível INFO incluindo o código de resultado.
6. A implementação reutiliza o `BinanceRestClient` de T5 — não cria segundo cliente HTTP.
## Testes de integração obrigatórios

Usar MockWebServer ou WireMock para simular os endpoints `GET /api/v3/ping` e `GET /api/v3/account`. Exercitar o boot completo (fase 1) com a aplicação subindo contra o mock server.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Binance acessível, API key válida | Boot fase 1 completa com sucesso; `ExchangeBootReadiness.ready` retornado; log INFO com resultado |
| `GET /api/v3/ping` com timeout | Boot fase 1 falha com `CONNECTIVITY_FAILURE`; aplicação não sobe em modo FAIL_FAST; log com causa |
| API key inválida (mock retorna HTTP 401) | Boot fase 1 falha com `INVALID_API_KEY`; aplicação não sobe em modo FAIL_FAST |
| Verificação respeita timeout configurado | Com mock configurado para delay > timeout, resultado é `CONNECTIVITY_FAILURE` dentro do prazo esperado |
