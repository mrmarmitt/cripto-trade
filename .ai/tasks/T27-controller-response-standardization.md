# T27 — Padronização do Retorno das Controllers (camada Spring)

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** Nenhuma  
**Status:** Concluído

---

## Descrição

A camada de entrada HTTP (`spring-application/.../controller/`) não tem um padrão definido de retorno. Cada controller decide por conta própria como modelar resposta de sucesso, body de erro, código de status e tratamento de exceção. O resultado é uma API inconsistente para o consumidor e regra de mapeamento exceção→status duplicada e divergente entre controllers.

Esta task define e aplica um **padrão único** de resposta para todas as controllers, centralizando o tratamento de erro e padronizando o envelope de erro, os status codes e o prefixo de rota.

---

## Contexto técnico — estado atual

### Inconsistências observadas

| Tema | Situação hoje |
|---|---|
| Handler global de exceção | **Não existe** `@ControllerAdvice` / `@RestControllerAdvice`. Tratamento é inline, por controller. |
| Body de erro | `ReconciliationController` retorna `String` crua (`ResponseEntity<?>`); `DeadLetterController` retorna o DTO de resposta (`ResolveDeadLetterResponse.failure(...)`); `AdminController` retorna body vazio (`.notFound().build()`). Não há envelope comum. |
| Mapeamento exceção→status | Duplicado e divergente: `IllegalArgumentException`→400 em vários, mas falha de runtime vira **502** no `ReconciliationController` e **500** no `DeadLetterController`. |
| Decisão de status | `DeadLetterController` decide NOT_FOUND vs CONFLICT por `response.message().toLowerCase().contains("not found")` — frágil, baseado em string. |
| Prefixo de rota | Misturado: `/api/...` em `AdminController`, `BootController`, `DeadLetterController`, `ReconciliationController`, `ListenerController`; **sem** prefixo em `RunnerController` (`/runners`), `PortfolioController` e `PortfolioRunnerController` (`/portfolios`), `WebsocketController` (`/websocket`), `WebsocketExchangeController` (`/websocket/exchange`). |
| Logging | Cada controller loga erro com formato próprio dentro do `catch`. |

### Controllers no escopo

`AdminController`, `BootController`, `DeadLetterController`, `ListenerController`, `PortfolioController`, `PortfolioRunnerController`, `ReconciliationController`, `RunnerController`, `WebsocketController`, `WebsocketExchangeController`.

---

## Solução proposta

### 1. Envelope de erro padrão

Criar um record único de erro na camada Spring, ex.:

```java
public record ApiError(
    Instant timestamp,
    int status,
    String error,      // reason phrase (ex: "Bad Request")
    String message,    // mensagem amigável, sem stacktrace nem detalhe interno
    String path
) {}
```

Toda resposta de erro retorna `ApiError` — nunca `String` crua nem o DTO de resultado do use case.

### 2. Handler global de exceção

Criar um `@RestControllerAdvice` (ex.: `GlobalExceptionHandler`) que centraliza o mapeamento exceção→status e monta o `ApiError`:

| Exceção | Status |
|---|---|
| `IllegalArgumentException` / validação (`MethodArgumentNotValidException`, `ConstraintViolationException`) | 400 |
| `RunnerNotFoundException` e demais "not found" de domínio | 404 |
| Conflito de estado (DLQ já resolvida/reprocessada) — exceção dedicada de domínio | 409 |
| `ExchangeQueryException` / falha de comunicação com exchange | 502 |
| Fallback `Exception` | 500 |

> A decisão NOT_FOUND vs CONFLICT do `DeadLetterController` deve deixar de ser baseada em `message().contains(...)`: o use case/domínio deve sinalizar o caso via exceção ou tipo explícito, e o handler traduz para o status.

### 3. Sucesso padronizado

- Controller permanece fino: chama o port inbound e retorna `ResponseEntity<DTO>` (nunca `ResponseEntity<?>`).
- Sem try/catch de mapeamento de status no controller — exceção sobe para o `@RestControllerAdvice`.
- Convenção de status de sucesso: `200` para leitura/comando com corpo; `204 No Content` para comando sem corpo (ex.: `refreshFilters`).

### 4. Prefixo de rota

Padronizar todas as rotas sob `/api/...`. Migrar `RunnerController` (`/runners`→`/api/runners`), `PortfolioController` e `PortfolioRunnerController` (`/portfolios`→`/api/portfolios`), `WebsocketController` e `WebsocketExchangeController` (`/websocket...`→`/api/websocket...`).

> **Atenção a quebra de contrato:** mudança de path é breaking change para qualquer consumidor (scripts, dashboards, testes de integração). Inventariar consumidores antes; se necessário, manter rota antiga deprecada por um ciclo ou alinhar a quebra explicitamente.

---

## Arquivos a criar/modificar

| Arquivo | Mudança |
|---|---|
| `spring-application/.../web/ApiError.java` (novo) | Envelope de erro padrão |
| `spring-application/.../web/GlobalExceptionHandler.java` (novo) | `@RestControllerAdvice` central |
| Controllers no escopo | Remover try/catch de mapeamento; padronizar retorno e prefixo de rota |
| Exceções de domínio (`core`) | Eventual exceção dedicada de conflito para substituir o match por string no DLQ |
| Testes de controller (`spring-application/src/test`) | Atualizar asserts de status/body e paths novos |

---

## Critérios de aceitação

1. Nenhuma controller retorna `String` crua nem o DTO de resultado como body de erro — todo erro é `ApiError`.
2. Existe um único `@RestControllerAdvice` responsável pelo mapeamento exceção→status; controllers não contêm try/catch para definição de status.
3. NOT_FOUND vs CONFLICT no fluxo de DLQ deixa de depender de `message().contains("not found")`.
4. Todas as rotas seguem o prefixo `/api/...`; quebras de contrato foram inventariadas e tratadas (deprecação ou alinhamento explícito).
5. Status de sucesso seguem a convenção (200 com corpo, 204 sem corpo).
6. Suíte de testes verde, incluindo testes de controller atualizados para o novo contrato.

---

## Impacto documental

- Avaliar atualização de `docs/` (qualquer doc de API/endpoints) e de `/.ai` se houver referência a contratos HTTP.
- Mudança de path é visível externamente: registrar no changelog/PR de forma destacada.
