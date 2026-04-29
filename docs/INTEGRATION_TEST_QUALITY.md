# Análise de Qualidade — Testes de Integração

Avaliação de assertividade, importância e qualidade de código de cada teste de integração da suíte atual.  
Base de análise: leitura completa de cada classe, asserções verificadas e invariantes de domínio cobertas.

**Escala de assertividade:**
- **Alta** — verifica valores exatos (saldos, quantidades, counts), múltiplos campos e invariantes financeiras com precisão
- **Média** — verifica estado correto mas com tolerâncias ou assertions indiretas (ex: `version >= 3`, `> 0`)
- **Baixa** — verifica apenas presença/ausência ou estado superficial sem validar consequências financeiras

**Escala de importância (0–10):**
- 10 — essencial: protege invariante financeira crítica; falha aqui = risco de perda de capital em produção
- 7–9 — alta: cobre cenário relevante do caminho de produção
- 4–6 — média: cobre edge case real, mas de menor frequência ou impacto
- 1–3 — baixa: cobertura residual ou redundante com outro teste
- 0 — sem valor agregado

**Escala de qualidade de código:**
- **Alta** — AAA legível, constantes nomeadas, helpers bem extraídos, mensagens de asserção, sem nomes FQ desnecessários
- **Média** — um ou dois problemas consistentes (nomes FQ inline, SQL não extraído, mensagens ausentes) mas código legível
- **Baixa** — múltiplos problemas estruturais, magic values, duplicação pesada, sem mensagens de asserção

---

## OrderLifecycleMockIntegrationTest

| Teste                                                           | O que testa                                                                                                                               | Assertividade  | Importância  | Qualidade de Código |
|-----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `buyThenSellShouldPersistMatchesClosePositionAndRealizeBalance` | Ciclo completo BUY → SELL end-to-end via sinal de mercado: transaction fill, posição aberta, posição fechada, match criado, PnL realizado | Média          | 9            | Média               |

**Observações (domínio):**
- Único teste da classe para o fluxo mais fundamental do sistema — merece expansão.
- Aceita resíduo de `reserved_balance` até 2.00 (`MAX_ACCEPTABLE_RESERVED_RESIDUAL`). O comentário no código chama isso de "sentinel de regressão", indicando que a liberação de reserva pós-SELL ainda tem uma inconsistência conhecida e não corrigida. Isso reduz a assertividade: o teste passa mesmo com capital não liberado completamente.
- Não valida o campo `realized_balance` com valor exato, apenas que seja `> 0`.
- Não cobre REJECTED, CANCELED ou EXPIRED no fluxo de sinal — apenas o happy path.

**Observações (qualidade de código):**
- `WAIT_TIMEOUT = Duration.ofSeconds(25)` — maior timeout da suíte; o loop de ciclo de vida nunca sai cedo, contribuindo desproporcionalmente para o tempo total.
- `MAX_ACCEPTABLE_RESERVED_RESIDUAL` documenta um bug conhecido via constante em vez de rastrear o issue separadamente — melhora a legibilidade mas mascara o defeito subjacente.
- Boa extração de helpers privados (`publishBuyTriggerTicks`, `awaitTransactionLifecycle`, etc.) e uso de records para snapshots (`TransactionSnapshot`, `PositionSnapshot`).
- `awaitTransactionLifecycle` usa `fail()` para status terminais — padrão correto para detectar desvios do happy path.

---

## ProcessTradeSignalMockIntegrationTest

| Teste                                                    | O que testa                                                               | Assertividade  | Importância  | Qualidade de Código |
|----------------------------------------------------------|---------------------------------------------------------------------------|----------------|--------------|---------------------|
| `buySignalShouldCreateAndFillTransactionAndOpenPosition` | Sinal de compra cria transação, preenche via MOCK e abre posição no banco | Média          | 9            | Média               |
| `buyOrderShouldPassThroughPartialAndThenFill`            | Transição de ciclo de vida SUBMITTED → PARTIAL → FILLED foi observada     | Baixa          | 5            | Média               |

**Observações (domínio):**
- O segundo teste usa `version >= 3` e verificação de que o lifecycle observou "PARTIAL" como prova indireta de que a transição ocorreu. Não valida o estado intermediário no banco — apenas que o evento foi visto. Uma refatoração poderia tornar isso desnecessário se os estados forem capturados explicitamente.
- Nenhum dos dois testa o efeito no `global_balance` após o BUY. A reserva de capital não é verificada.
- O primeiro teste seria mais assertivo se validasse `reserved_balance == custo_da_ordem` após o fill e `available_balance == capital_inicial - custo`.

**Observações (qualidade de código):**
- `runner.boot.orchestrator-enabled=false` configurado em duplicata: em `@SpringBootTest(properties=...)` E em `@DynamicPropertySource` — configuração redundante.
- `WAIT_TIMEOUT = Duration.ofSeconds(20)` — alto e sem saída antecipada; o polling loop cobre todo o ciclo de vida mesmo que o fill chegue em 120ms.
- `latestBuyTransaction` é funcionalmente idêntica a `latestTransactionByType` de `OrderLifecycleMockIntegrationTest` — duplicação entre classes standalone sem infraestrutura compartilhada.
- Bom uso de records para snapshots (`TransactionSnapshot`, `BuyLifecycleSnapshot`) e helpers extraídos.

---

## OrderTerminationConciliationIntegrationTest

| Teste                                                          | O que testa                                                                                          | Assertividade  | Importância  | Qualidade de Código |
|----------------------------------------------------------------|------------------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `rejectedBuyShouldReleaseFullReservedBalance`                  | REJECTED libera reserva completa, transação em REJECTED, saldo restaurado ao inicial                 | Alta           | 10           | Alta                |
| `expiredSubmittedBuyShouldReleaseFullReservedBalance`          | EXPIRED libera reserva completa, `exchangeOrderId` preservado, saldo restaurado                      | Alta           | 9            | Alta                |
| `canceledPartialBuyShouldReleaseOnlyOutstandingReservedAmount` | CANCELED em BUY parcial libera apenas o valor pendente (não o já executado); posição parcial mantida | Alta           | 10           | Alta                |
| `canceledSellShouldUnlockTargetPositionAndPreserveBalance`     | SELL CANCELED desbloqueia posição (`OPEN`, `lockedByTransactionId = null`), capital preservado       | Alta           | 10           | Alta                |

**Observações (domínio):**
- Classe com assertividade mais alta da suíte. Verifica valores exatos de saldo em todos os cenários.
- O teste de CANCELED parcial valida a matemática de liquidação (libera apenas o outstanding) — protege diretamente contra vazamento de capital.
- O teste de SELL CANCELED verifica campos de banco específicos (`locked_by_transaction_id`, `locked_quantity`) — alta precisão.
- Gap: não há teste de EXPIRED em BUY PARTIAL (apenas em SUBMITTED). O comportamento de liberação parcial no EXPIRED não está coberto.

**Observações (qualidade de código):**
- `newTransaction` helper extraído com parâmetros explícitos — boa prática; evita duplicação do construtor.
- `assertNotNull(balance)` ao final dos testes 1 e 2 é redundante — `awaitBalance` já lança `AssertionError` se não encontrar o saldo esperado.
- `canceledSellShouldUnlockTargetPositionAndPreserveBalance` usa `jdbcTemplate.update("UPDATE transactions SET target_lot_id = ? ...")` diretamente pois o domínio não expõe esse campo no construtor — sinal de lacuna no domain model, mas a solução no teste é pragmática e não prejudica a legibilidade.
- `awaitBalance`, `awaitTransactionStatus`, `awaitPositionUnlocked` são helpers async bem estruturados com mensagens de erro contextuais.

---

## RunnerBootRecoveryIntegrationTest

| Teste                                                                              | O que testa                                                                                       | Assertividade  | Importância  | Qualidade de Código |
|------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `recoveryShouldExpireZombiePendingBuyAndRestoreBalance`                            | Zombie PENDING fora do TTL expira, capital liberado, runner permanece ACTIVE                      | Alta           | 10           | Alta                |
| `recoveryShouldKeepZombiePendingWithinTtlAndPreserveReservedBalance`               | Zombie PENDING dentro do TTL não expira, capital preservado                                       | Alta           | 10           | Alta                |
| `recoveryShouldBeIdempotentWhenExpiringTheSameZombieTwice`                         | Segunda execução do recovery sobre zombie já expirado não altera estado                           | Alta           | 10           | Alta                |
| `recoveryShouldExpireSubmittedOrderNotFoundOnExchangeAndReleaseBalance`            | SUBMITTED não encontrado na exchange → EXPIRED, capital liberado, idempotente                     | Alta           | 10           | Alta                |
| `recoveryShouldCancelPartialBuyNotFoundOnExchangeAndReleaseOutstandingReserveOnly` | PARTIAL não encontrado na exchange → CANCELED, reserva pendente liberada, parte executada mantida | Alta           | 10           | Alta                |
| `recoveryShouldHaltRunnerWhenLimboExistsButQueryCapabilityIsUnavailable`           | Query não suportada pela exchange → runner vai para HALTED, capital preso em reserved             | Alta           | 9            | Alta                |
| `recoveryShouldRetryTransientQueryFailureAndReconcileOnNextAttempt`                | Falha transitória na query → retry bem-sucedido → FILLED reconciliado                             | Alta           | 9            | Alta                |
| `recoveryShouldHaltRunnerWhenTransientQueryFailuresExhaustRetries`                 | Retries esgotados → runner vai para HALTED, posição não criada, capital preso                     | Alta           | 10           | Alta                |
| `recoveryShouldReconcileSubmittedBuyFoundAsFilledOnExchange`                       | SUBMITTED encontrado como FILLED na exchange → reconciliado corretamente                          | Alta           | 10           | Alta                |
| `recoveryShouldReconcilePartialBuyFoundAsFilledOnExchangeWithoutQuantityDrift`     | PARTIAL encontrado como FILLED total → convergência sem drift de quantidade, idempotente          | Alta           | 10           | Alta                |
| `recoveryShouldHaltActiveRunnerWhenUnresolvedDlqExists`                            | Runner com DLQ irresolvido vai para HALTED durante recovery                                       | Alta           | 9            | Alta                |

**Observações (domínio):**
- Classe mais completa e importante da suíte. Cobre todos os cenários críticos de boot recovery com alta assertividade.
- Todos os testes verificam estado final no banco E comportamento do runner (ACTIVE/HALTED, isReconciling).
- Idempotência verificada explicitamente em 3 cenários — prática correta para um use case que pode ser re-executado.
- Gap: nenhum teste cobre o caso de SELL em SUBMITTED/PARTIAL durante boot recovery — apenas BUY. O comportamento de SELL limbo não está explicitamente testado aqui.

**Observações (qualidade de código):**
- `@DynamicPropertySource` com configurações de retry (timeout, max-attempts, backoff) mostra cuidado em controlar o comportamento temporizado — sem isso os testes de retry seriam lentos ou não-determinísticos.
- `ZOMBIE_TTL_MS = 300_000L` como constante nomeada (não magic value) com uso explícito no `@DynamicPropertySource`.
- Uso direto de `RunnerBootRecoveryUseCase` via `@Autowired` em vez de passar pelo boot completo — correto para isolar o use case sem o overhead do orchestrator.
- `MockExchangeAdapter` configurado com overrides inline — padrão consistente com as demais classes de integração.

---

## MockBuyOrderOverrideIntegrationTest

| Teste                                                                | O que testa                                                                    | Assertividade  | Importância  | Qualidade de Código |
|----------------------------------------------------------------------|--------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `shouldNotDoubleApplyQuantityWhenDuplicatePartialEventsOccur`        | PARTIAL duplicado não soma quantidade; posição e executedQty corretos          | Alta           | 10           | Média               |
| `nearSimultaneousPartialAndFilledShouldConvergeWithoutQuantityDrift` | PARTIAL + FILLED quase simultâneos convergem para quantidade correta sem drift | Alta           | 9            | Média               |
| `duplicateFilledShouldNotDoubleApplyEconomicEffects`                 | FILLED duplicado não duplica quantidade nem posição                            | Alta           | 10           | Média               |
| `reorderedBuyFilledBeforePartialShouldIgnoreLatePartial`             | FILLED que chega antes do PARTIAL ignora o PARTIAL atrasado                    | Alta           | 9            | Média               |

**Observações (domínio):**
- Cobre os cenários de idempotência e reordenação de eventos com alta precisão.
- Todos verificam `effectiveExecutedQuantity`, contagem de posições e matches — o conjunto de campos correto.
- Gap: nenhum desses testes verifica o `global_balance` após o BUY fill. A reserva não é verificada em nenhum cenário desta classe. Uma duplicação de quantidade que não fosse detectada em `executedQty` poderia vazar sem ser pega aqui.

**Observações (qualidade de código):**
- Nomes FQ de `com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus` usados inline em todos os 4 testes em vez de import estático ou import regular — gera ruído visual nas chamadas `new MockOrderScenarioOverride.PlannedEvent(...)`.
- O bloco de construção `new Transaction(runnerId, clientOrderId, TransactionType.BUY, ...)` é repetido nos 4 testes com apenas o nome do cenário diferindo — candidato a helper `newBuyTransaction(runner, quantity, price, scenario)`.
- Queries JDBC inline no corpo do teste (`jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transaction_matches...")`) poderiam estar em helpers de leitura da support class.
- SCENARIO_* constants bem usados; assertions têm mensagens na maioria dos campos críticos.

---

## MockSellOrderOverrideIntegrationTest

| Teste                                                     | O que testa                                                                     | Assertividade  | Importância  | Qualidade de Código |
|-----------------------------------------------------------|---------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `duplicateSellFilledShouldNotDoubleApplyEconomicEffects`  | SELL FILLED duplicado não duplica match, PnL, nem saldo disponível              | Alta           | 10           | Média               |
| `reorderedSellFilledBeforePartialShouldIgnoreLatePartial` | FILLED antes do PARTIAL no SELL ignora o PARTIAL atrasado; PnL e saldo corretos | Alta           | 9            | Alta                |

**Observações (domínio):**
- Verifica o conjunto mais completo de campos de qualquer classe da suíte: `executedQuantity`, `matchCount`, `matchedQuantity`, `pnlRealized`, `realizedBalance`, `availableBalance`, `reservedBalance`, `positionStatus` — altamente assertivos.
- Gap: apenas 2 testes para o SELL. Não há teste de SELL PARTIAL + FILLED near-simultaneous no lado do SELL (apenas do BUY existe). Cenário assimétrico.

**Observações (qualidade de código):**
- Primeiro teste ainda usa construção manual de `Transaction` para o BUY de setup (antes de criar o SELL) — inconsistente com o segundo teste que usa `createFilledBuyAndLockedSell`.
- Primeiro teste usa `com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.FILLED` com FQ name; segundo usa `OrderDataDto.OrderStatus` via import — inconsistência dentro da mesma classe.
- Segundo teste é significativamente mais limpo: uso de `createFilledBuyAndLockedSell` elimina o boilerplate de setup de BUY. Diferença de qualidade entre os dois testes é visível.
- Algumas asserções no final sem mensagem: `assertEquals("CLOSED", stable.positionStatus())` e `assertEquals(0, stable.positionQuantity().compareTo(BigDecimal.ZERO))`.

---

## MockTerminalOrderOverrideIntegrationTest

| Teste                                                            | O que testa                                                                | Assertividade  | Importância  | Qualidade de Código |
|------------------------------------------------------------------|----------------------------------------------------------------------------|----------------|--------------|---------------------|
| `lateCanceledAfterBuyFilledShouldNotRegressFilledState`          | CANCELED que chega depois de FILLED não regride o estado da transação      | Alta           | 10           | Média               |
| `rejectedBuyShouldReleaseReservedBalanceWithoutCreatingPosition` | BUY REJECTED não cria posição, libera capital                              | Alta           | 10           | Média               |
| `expiredBuyShouldReleaseReservedBalanceWithoutCreatingPosition`  | BUY EXPIRED não cria posição, libera capital, executedQty == 0             | Alta           | 9            | Média               |
| `lateCanceledAfterSellFilledShouldNotRegressClosedState`         | CANCELED que chega depois de SELL FILLED não reabre posição nem altera PnL | Alta           | 10           | Alta                |

**Observações (domínio):**
- Protege diretamente a invariante de monotonicidade de status — fundamental para não regredir estado de posição.
- O teste de SELL com late CANCELED verifica explicitamente `pnlRealized`, `realizedBalance`, `availableBalance` e `positionStatus` — alta cobertura de campos.
- Gap: não há teste de late EXPIRED após FILLED (apenas late CANCELED). O comportamento poderia ser diferente se o tratamento de EXPIRED não seguir a mesma lógica.

**Observações (qualidade de código):**
- Mesma questão dos FQ names em todos os testes com `PlannedEvent` — `com.marmitt.core.dto.websocket.data.OrderDataDto.OrderStatus.*` usado inline.
- Testes 1, 2 e 3 repetem o bloco `new Transaction(runnerId, clientOrderId, TransactionType.BUY, ...)` com apenas preço e cenário diferentes; teste 4 usa `createFilledBuyAndLockedSell` — inconsistência entre testes da mesma classe.
- `lateCanceledAfterBuyFilledShouldNotRegressFilledState` não faz `reserveAtomic` antes do test — diferente dos demais testes de terminal. Isso é intencional (o cenário não verifica saldo), mas cria inconsistência de pré-condição entre testes.
- Uso correto de `awaitCondition` para verificar que o estado permanece estável após o evento atrasado.

---

## MockTerminalMonotonicityStabilityIntegrationTest

| Teste                                                              | O que testa                                                                              | Assertividade  | Importância  | Qualidade de Código |
|--------------------------------------------------------------------|------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `reorderedFilledBeforePartialShouldRemainStableAcrossRepeats` (3x) | Cenário de FILLED antes de PARTIAL produz resultado idêntico em 3 execuções consecutivas | Alta           | 4            | Média               |
| `lateCanceledAfterFilledShouldRemainMonotonicAcrossRepeats` (3x)   | Cenário de late CANCELED após FILLED permanece estável em 3 execuções                    | Alta           | 4            | Média               |

**Observações (domínio):**
- **Alto custo, baixo valor incremental.** Os cenários já estão cobertos por `MockBuyOrderOverrideIntegrationTest` e `MockTerminalOrderOverrideIntegrationTest`. A única diferença é a repetição 3x para detectar flakiness.
- A detecção de flakiness por repetição de testes é valiosa durante o desenvolvimento, mas em uma suíte estável tem retorno marginal. A mesma confiança poderia ser obtida com a suíte de CI rodando multiple times ocasionalmente.
- O custo real é alto: cada `@RepeatedTest(3)` executa 3x o setup completo do ciclo BUY + SELL, resultando em 6 execuções longas (cada uma com `awaitStableFilledState` + `awaitStableSellState` rodando até o deadline).
- **Recomendação:** mover para uma categoria de testes de estabilidade opcional, não executada no CI padrão. Ou substituir por uma única repetição se o problema de flakiness que motivou a criação já foi resolvido.

**Observações (qualidade de código):**
- Métodos públicos são muito limpos — apenas delegam para privados com `RepetitionInfo`.
- `@RepeatedTest` com `name` descritivo incluindo `[{currentRepetition}/{totalRepetitions}]` — boa prática para distinguir falhas.
- FQ names ausentes nesta classe (importa `OrderDataDto.OrderStatus` corretamente) — contrastando com as classes irmãs.
- Os 4 métodos privados duplicam substancialmente o código de `MockBuyOrderOverrideIntegrationTest` e `MockTerminalOrderOverrideIntegrationTest` — a duplicação está no nível inter-classe, não visível dentro da própria classe.
- Asserts nos privados têm algumas mensagens ausentes (e.g., `assertEquals(OrderDataDto.OrderStatus.PARTIALLY_FILLED, latePartial.status())` sem mensagem).

---

## ConcurrentSellLockIntegrationTest

| Teste                                                        | O que testa                                                                             | Assertividade  | Importância  | Qualidade de Código |
|--------------------------------------------------------------|-----------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `concurrentSellLocksShouldAllowOnlyOneWinnerForSamePosition` | Duas threads simultâneas tentando lock na mesma posição — apenas 1 consegue             | Alta           | 10           | Alta                |
| `sellLockRetryForSameTransactionShouldBeIdempotent`          | Retry do lock pela mesma transação sempre sucede; transação concorrente não toma o lock | Alta           | 9            | Alta                |

**Observações (domínio):**
- Único ponto da suíte que testa concorrência real com `CountDownLatch`. Protege contra double-sell, que seria uma perda direta de capital.
- O segundo teste valida normalização de precisão de quantidade (`0.002000004 → 0.002`) como efeito colateral — isso cobre uma edge case real de arredondamento em banco.
- Gap: não há teste de 3+ threads simultâneas. Dois é o mínimo para provar exclusão mútua, mas em cenários de alta carga poderia haver comportamento diferente.

**Observações (qualidade de código):**
- Melhor qualidade de código entre as classes standalone da suíte.
- `LockResult(UUID transactionId, boolean locked)` como record — evita array/map para capturar resultados de threads.
- `attemptLockConcurrently`, `awaitLatch`, `shutdownExecutor` bem extraídos; lógica de concorrência não vaza para o corpo do teste.
- Comentário inline (L109) explica o design choice não-óbvio de pré-criar transações antes da corrida — comentário justificado.
- Constantes `HIGH_PRECISION_LOCK_QUANTITY` e `NORMALIZED_HIGH_PRECISION_LOCK_QUANTITY` nomeadas para deixar explícita a expectativa de normalização.
- Duplica `createPortfolio` e `createAndActivateRunner` de outras classes por não herdar de uma base comum — problema de T10, não design da classe.

---

## CapitalDeadLetterReplayIntegrationTest

| Teste                                                                             | O que testa                                                                                                   | Assertividade  | Importância  | Qualidade de Código |
|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `executionConfirmedDlqReplayShouldApplyEffectOnceAndResolveDuplicateReplaySafely` | Replay de EXECUTION_CONFIRMED aplica PnL uma vez; duplicata é detectada e silenciosamente resolvida sem drift | Alta           | 9            | Alta                |
| `marginReleaseDlqReplayShouldApplyEffectAndKeepDuplicateReplayOpenWithoutDrift`   | Replay de MARGIN_RELEASE libera capital uma vez; duplicata retorna 409 CONFLICT e permanece como unresolved   | Alta           | 9            | Alta                |

**Observações (domínio):**
- Únicos testes que exercitam o ciclo completo de DLQ: criação → reprocessamento via REST → validação de idempotência.
- O segundo teste verifica comportamento assimétrico: EXECUTION_CONFIRMED duplicado é resolvido automaticamente (409 não levantado), MARGIN_RELEASE duplicado levanta conflito e exige revisão manual. Essa assimetria é uma decisão de design importante que os testes documentam.
- Assertividade alta: verificam `capital_event_ledger` (contagem de entradas), `global_balance` e status da DLQ entry.

**Observações (qualidade de código):**
- `executionConfirmedEvent` e `marginReleaseEvent` como métodos factory privados — evitam construção inline de objetos complexos no corpo do teste.
- `assertBalance` combina await + verify em um único método com mensagem de erro contextual — padrão superior ao verificar e aguardar separadamente.
- `awaitUnresolvedEntry` é bem parametrizado e usa filtros por `eventTypeFragment` e `transactionIdFragment` para precisão.
- `assertCapitalLedgerCount` usa SQL direto via `jdbcTemplate` — extração adequada para uma query de verificação pontual.
- Em `createAndActivateRunner`, o runner é carregado duas vezes via `findById` (antes e depois do `save`) — a segunda carga é redundante; o runner salvo é suficiente.
- Text blocks para JSON de request no MockMvc — boa legibilidade.

---

## MockTransientOrderFailureIntegrationTest

| Teste                                                                              | O que testa                                                                                         | Assertividade  | Importância  | Qualidade de Código |
|------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|----------------|--------------|---------------------|
| `exchangeRedeliveredSellFilledAfterTransientFailureShouldApplyEconomicEffectsOnce` | Falha na primeira entrega do FILLED, redelivery bem-sucedida aplica efeitos uma vez, sem DLQ criado | Alta           | 9            | Alta                |

**Observações (domínio):**
- Único teste que cobre falha de processamento local com redelivery da exchange — cenário real e frequente.
- Verifica que `dead_letter_entries.count == 0` após recuperação bem-sucedida — importante para garantir que DLQ não acumula entradas desnecessárias.
- Gap: não há teste do caminho oposto — falha persistente que esgota redeliveries e criaria DLQ. Esse cenário está implicitamente coberto por `CapitalDeadLetterReplayIntegrationTest`, mas não para o fluxo de SELL FILLED especificamente.

**Observações (qualidade de código):**
- `@ContextConfiguration(classes = TransientFailureTestConfig.class)` para substituir um bean específico — padrão correto para injeção de falha sem afetar outros beans.
- `PlannedOrderConciliationFailurePlan` com `ConcurrentHashMap.newKeySet()` e `AtomicInteger` — thread-safe por design, necessário dado que o executor roda em thread diferente.
- `FailureKey` como record — evita `Pair` genérico ou `String` concatenado como chave de mapa.
- `failurePlan.failureCount(...)` como asserção de pré-condição (prova que o primeiro delivery falhou) — garante que o teste cobre o caminho intencionado, não apenas o happy path.
- Comentário inline (L69-72) explica o `1` (número de duplicações) como simulação de redelivery — esse comentário é necessário porque o valor sem contexto parece arbitrário.
- `FailingOnceConciliationOrderUpdateExecutor.submitTransaction` e outros métodos delegam diretamente — verboso mas correto dado que `ConciliationOrderUpdateExecutor` é uma interface com múltiplos métodos.

---

## Visão consolidada

| Classe                                           | Testes  | Assertividade média | Importância média   | Qualidade de Código | Observação principal                                                   |
|--------------------------------------------------|---------|---------------------|---------------------|---------------------|------------------------------------------------------------------------|
| OrderTerminationConciliationIntegrationTest      | 4       | Alta                | 9.8                 | Alta                | Melhor relação cobertura/precisão da suíte                             |
| RunnerBootRecoveryIntegrationTest                | 11      | Alta                | 9.7                 | Alta                | Mais completa; cobre todos os modos de boot recovery                   |
| ConcurrentSellLockIntegrationTest                | 2       | Alta                | 9.5                 | Alta                | Único ponto com teste de concorrência real; melhor código standalone   |
| MockBuyOrderOverrideIntegrationTest              | 4       | Alta                | 9.5                 | Média               | Idempotência de BUY; FQ names e setup duplicado nos 4 testes           |
| MockTerminalOrderOverrideIntegrationTest         | 4       | Alta                | 9.8                 | Média               | Protege monotonicidade; mesmos problemas de FQ names                   |
| MockSellOrderOverrideIntegrationTest             | 2       | Alta                | 9.5                 | Média               | Assertividade mais alta de campos; qualidade desigual entre testes     |
| CapitalDeadLetterReplayIntegrationTest           | 2       | Alta                | 9.0                 | Alta                | Ciclo completo de DLQ com REST; boa extração de helpers                |
| MockTransientOrderFailureIntegrationTest         | 1       | Alta                | 9.0                 | Alta                | Design sofisticado; decorator pattern para injeção de falha            |
| OrderLifecycleMockIntegrationTest                | 1       | Média               | 9.0                 | Média               | Fundamental mas superficial; sentinel de reserva residual              |
| ProcessTradeSignalMockIntegrationTest            | 2       | Média               | 7.0                 | Média               | Assertions indiretas; duplicação com OrderLifecycleMockIntegrationTest |
| MockTerminalMonotonicityStabilityIntegrationTest | 2×3     | Alta                | 4.0                 | Média               | Redundante com outras classes; duplicação inter-classe                 |

---

## Gaps identificados

| Gap                                                                                            | Risco                                                                   | Prioridade |
|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|------------|
| `OrderLifecycleMockIntegrationTest` aceita resíduo de até 2.00 em `reserved_balance` após SELL | Possível vazamento de capital não detectado                             | Alta       |
| `ProcessTradeSignalMockIntegrationTest` não verifica `global_balance` após BUY                 | Reserva incorreta passaria nos testes                                   | Alta       |
| `MockBuyOrderOverrideIntegrationTest` não verifica `global_balance` em nenhum cenário          | Duplicação de quantidade sem efeito em saldo não seria detectada        | Alta       |
| Sem teste de SELL limbo no boot recovery (apenas BUY)                                          | Comportamento de SELL SUBMITTED/PARTIAL no restart não está coberto     | Média      |
| Sem teste de EXPIRED em BUY PARTIAL (apenas em SUBMITTED)                                      | Liberação parcial de reserva no EXPIRED não está validada               | Média      |
| Sem teste de falha persistente de redelivery gerando DLQ no fluxo de SELL                      | Caminho de DLQ por esgotamento de redelivery não está coberto para SELL | Média      |
| `MockTerminalMonotonicityStabilityIntegrationTest` duplica cobertura existente com custo alto  | Custo de CI sem retorno proporcional                                    | Baixa      |
| Sem teste de `ExecutionPolicy.SINGLE` bloqueando segundo sinal enquanto há posição aberta      | Política de execução não está explicitamente testada                    | Média      |

---

## Problemas de qualidade transversais

Estes problemas aparecem em múltiplas classes e devem ser endereçados sistematicamente:

| Problema                                                                          | Classes afetadas                                                                                                      | Impacto           |
|-----------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-------------------|
| `OrderDataDto.OrderStatus` com FQ name em `PlannedEvent`                          | MockBuyOrderOverride, MockSellOrderOverride (test 1), MockTerminalOrderOverride                                       | Legibilidade      |
| Construtor `new Transaction(...)` repetido em cada teste sem helper               | MockBuyOrderOverride, MockTerminalOrderOverride (testes 1–3), Monotonicity                                            | Manutenibilidade  |
| `createPortfolio` e `createAndActivateRunner` duplicados entre classes standalone | OrderLifecycleMock, ProcessTradeSignalMock, OrderTerminationConciliation, ConcurrentSellLock, CapitalDeadLetterReplay | DRY               |
| `awaitBalance` / `awaitTransactionStatus` reimplementados em múltiplas classes    | OrderTerminationConciliation, CapitalDeadLetterReplay, OrderLifecycleMock                                             | DRY               |
| `@DirtiesContext` + container próprio em classes standalone                       | Todas as standalone (não herdam MockOrderOverrideIntegrationTestSupport)                                              | Performance (T10) |
