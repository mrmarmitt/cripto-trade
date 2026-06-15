# T19 — Serializar conciliação de ordens por clientOrderId via striped lock

**Complexidade:** Baixa  
**Responsável:** Codex  
**Dependências:** nenhuma  
**Status:** Concluída

---

## Motivação

Durante testes na testnet, a Binance enviou três mensagens de conciliação (NEW, PARTIALLY_FILLED, FILLED) quase simultaneamente para a mesma ordem. O processamento é feito pelo `messageProcessingExecutor` (`corePoolSize=2`, `maxPoolSize=4`), que despacha cada mensagem via `@Async` para threads independentes. Isso cria uma race condition: duas threads leem a mesma `Transaction` na mesma versão e a segunda falha com `OptimisticLockingFailureException`.

O retry loop (`for` com `CONCILIATION_MAX_RETRIES=3`) foi adicionado como mitigação, mas trata o sintoma — não elimina a race. Ele depende de timing: se threads concorrerem simultaneamente, as tentativas de retry podem se sobrepor e esgotar o limite. Além disso, não há garantia de ordenação (FILLED pode ser processado antes de NEW), aumentando a dependência sobre a lógica de idempotência.

A causa raiz é a ausência de serialização por `clientOrderId` antes da lógica de negócio.

---

## Solução implementada

No bean `conciliationOrderUpdateExecutor` em `RunnerConfig.java`:

- Adicionado array de 64 `ReentrantLock[]` (`CONCILIATION_LOCKS`) inicializado em bloco `static`.
- Adicionado `lockFor(clientOrderId)` usando `Math.floorMod` para mapear qualquer hash (inclusive negativos) a um índice válido sem risco de `ArrayIndexOutOfBoundsException`.
- No método `execute(OrderDataDto)`, a lock é adquirida antes de chamar `conciliationOrderUpdate.execute()` e liberada em `finally`.
- Removidos: `CONCILIATION_MAX_RETRIES`, o `for` de retry, o import de `OptimisticLockingFailureException` e a anotação `@Slf4j` (que ficou órfã após remoção do `log.warn`).

O `for` foi removido porque, com serialização garantida dentro do processo, o `OptimisticLockingFailureException` não tem mais cenário de disparo. Manter o retry seria código morto.

---

## Arquivo modificado

- `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java`

---

## Critérios de aceitação

1. `OptimisticLockingFailureException` não ocorre quando a exchange envia múltiplos status para a mesma ordem em rajada.
2. A suite de testes existente passa sem alteração de lógica: `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`.
3. `UserDataStreamConciliationIntegrationTest` e `OrderTerminationConciliationIntegrationTest` passam.

---

## Notas

- O striped lock é in-process: serializa concorrência dentro da mesma JVM. Não cobre cenários multi-instância, que não fazem parte do modelo atual.
- 64 stripes são suficientes para o volume de ordens simultâneas esperado e evitam crescimento ilimitado de estrutura (ao contrário de um `ConcurrentHashMap<clientOrderId, Lock>`).
- `Math.floorMod` garante índice não-negativo mesmo quando `hashCode()` retorna `Integer.MIN_VALUE`.
