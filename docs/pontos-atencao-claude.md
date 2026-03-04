
  ---
⚠️ Pontos de atenção

1. Boot recovery sem fronteira transacional
   ConciliationOrderUpdate.execute(OrderDataDto) (o atalho do boot) chama submitTransaction, processFill, releaseMargin diretamente, sem @Transactional. Uma falha no meio (ex: position salva, evento não publicado) deixa o estado inconsistente. A mitigação atual é halting do runner no step6 — consciente, mas
   vale documentar como limitação conhecida.

2. CompletableFuture.supplyAsync() sem executor explícito
   O timeout de query na exchange usa o ForkJoinPool.commonPool() implicitamente. Para I/O de rede com múltiplos runners em paralelo, isso pode saturar o pool. Para MVP com poucos runners é inofensivo, mas o risco existe.

3. isRetryableQueryFailure() — detecção por mensagem de texto
   Classificar falhas transitórias varrendo strings como "timeout", "rate limit", "429" é frágil — depende da mensagem exata da biblioteca de exchange. Uma exceção tipada do adapter seria mais robusta, mas entende-se que é o melhor possível sem controle sobre a biblioteca.

4. step6 — DLQ de runs anteriores bloqueia boot
   Se existirem entradas de DLQ não resolvidas de runs anteriores, o runner é HALTED mesmo que a reconciliação atual tenha concluído sem erros. Isso é conservador e correto, mas requer uma API/mecanismo de resolução manual de DLQ para desbloquear o runner — que ainda não existe.

5. step1 — limbo.contains() assume identidade de objetos
   List<Transaction> limbo = inFlight.stream()
   .filter(tx -> !zombies.contains(tx))  // usa equals() de Transaction
   .toList();
   Funciona porque zombies é um subconjunto de inFlight (mesmos objetos), mas é uma dependência implícita de referência. Se Transaction.equals() for identity-based e os objetos forem recriados por algum mapper, quebraria silenciosamente.


---
Analise codex (2026-03-04)

1) Boot recovery sem fronteira transacional
- Faz sentido: SIM (importante).
- Quando acontece: no atalho `ConciliationOrderUpdate.execute(OrderDataDto)` usado no `RunnerBootRecoveryUseCase` (steps 3 e 4).
- Ponto tecnico: no fluxo normal, `OrderConciliationUseCase` roda com `TransactionTemplate` no `RunnerConfig`. No boot, o atalho chama a reconciliacao sem esse envelope unico.
- Detalhe importante: os metodos de repositorio ja sao `@Transactional`, entao existe transacao por operacao. O risco aqui e falta de atomicidade de uma sequencia completa (ex.: salvar tx+match, falhar ao salvar position, etc.).
- Solucao sugerida:
  - curto prazo: criar um wrapper transacional de boot (via `TransactionTemplate`) e chamar a reconciliacao por evento dentro dessa fronteira;
  - medio prazo: expor uma porta de reconciliacao transacional unica para evitar divergir fluxo normal vs boot.
- Duvidas em aberto:
  - queremos "all-or-nothing" por evento reconciliado no boot?
  - o comportamento esperado em erro e retry automatico ou halt imediato?

2) CompletableFuture.supplyAsync() sem executor explicito
- Faz sentido: SIM.
- Quando acontece: step 4 (`queryOrderByClientOrderIdWithTimeout`) quando `exchangeQueryTimeoutMs > 0`.
- Risco: uso do `ForkJoinPool.commonPool` para I/O bloqueante pode degradar sob muitos runners/queries.
- Solucao sugerida:
  - injetar `Executor` dedicado do boot recovery (pool pequeno e controlado por config);
  - opcional: evitar `CompletableFuture` e usar timeout nativo do client HTTP do adapter.
- Duvidas em aberto:
  - qual volume maximo de runners/queries alvo para o ambiente de teste?

3) isRetryableQueryFailure() por texto de mensagem
- Faz sentido: SIM (com severidade moderada).
- Quando acontece: no retry do step 4 ao classificar erro transitorio vs definitivo.
- Ponto tecnico: hoje ja existe excecao tipada para timeout (`BootQueryTimeoutException`), mas os outros casos ainda dependem de `message.contains(...)`.
- Solucao sugerida:
  - introduzir excecoes tipadas no contrato REST (ex.: `ExchangeRateLimitException`, `ExchangeTemporaryUnavailableException`);
  - manter fallback por texto apenas como ultima linha de defesa.
- Duvidas em aberto:
  - vamos padronizar erros por adapter agora (MOCK/BINANCE/COINBASE) ou em uma fase dedicada?

4) Step6 bloqueando por DLQ de runs anteriores
- Faz sentido: SIM (comportamento conservador e coerente com seguranca).
- Quando acontece: `step6FinalizeRunnerState` se existir DLQ nao resolvida por runner ou portfolio (runner null).
- Efeito: runner nao conclui reconciliacao e pode ficar em `HALTED`.
- Solucao sugerida:
  - criar fluxo explicito de resolucao de DLQ (use case + endpoint + auditoria de quem resolveu e motivo);
  - opcional futuro: escopo por `bootRunId` para distinguir backlog historico vs incidente atual.
- Duvidas em aberto:
  - qual governanca para resolver DLQ (manual, semi-auto, SLA)?

5) limbo.contains() com dependencia implicita de identidade
- Faz sentido: SIM, mas severidade BAIXA no estado atual.
- Quando acontece: step 1 ao separar `zombies` e `limbo`.
- Estado atual: funciona porque `zombies` sai do mesmo `inFlight` em memoria (mesmas referencias).
- Solucao sugerida (simples e robusta):
  - classificar por ID em vez de referencia:
  - `Set<UUID> zombieIds = zombies.stream().map(Transaction::getId).collect(...)`
  - `limbo = inFlight.stream().filter(tx -> !zombieIds.contains(tx.getId())).toList();`
- Duvidas em aberto:
  - nenhuma critica; melhoria pequena e segura para reduzir risco futuro de refactor.

Resumo rapido
- Todos os 5 pontos fazem sentido.
- Prioridade sugerida:
  1. item 1 (fronteira transacional no boot),
  2. item 4 (fluxo de resolucao DLQ),
  3. item 2 (executor dedicado),
  4. item 3 (erro tipado),
  5. item 5 (ajuste de robustez por ID).
