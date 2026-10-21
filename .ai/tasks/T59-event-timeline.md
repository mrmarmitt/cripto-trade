# T59 — Linha do Tempo de Eventos e Relatório de Rodada

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T55 (auditoria, estado consolidado, runbook), T53 (registro das rodadas), T25/T43/T44
(eventos automáticos), T57/T58 (conferências usadas no relatório).
**Status:** Concluído (PR #164, 2026-10-21). Validação na testnet pendente.

---

## Histórico do escopo

A primeira versão (commit b097094) propunha um agente agendado verificando o sistema a cada 30 min
durante as rodadas do soak. O mantenedor decidiu em 2026-10-21:

- **Sem monitoramento periódico no soak.** O soak mede o ctrade; a validação acontece **no fim da
  rodada**, com dados suficientes para reconstruir tudo o que aconteceu. Os automatismos do ctrade (Safe
  Mode, proteção do lote, halt por rejeições) já reagem sozinhos durante a rodada.
- **"Monitoramento agêntico" é outra coisa:** um agente que lê o regime de mercado, liga e desliga
  estratégias pela classificação, propõe estratégias novas, valida e aumenta o capital das que
  funcionam. Isso vai para o roadmap `.ai/agentic-strategy-roadmap.md`, fora desta task.
- **Eventos persistidos em tabela, não só em log.** O que já é gravado continua onde está; persistem-se
  só os dois eventos que hoje se perdem (mudança de estado do runner e execução do boot).

## Descrição

No fim de uma rodada de 24h, a pergunta é "o que aconteceu, quando e por quê?". Hoje parte da resposta
só existe no Discord e nos logs do Loki, o que fere o princípio "verificável de fora"
(`.ai/product-direction.md`):

| Evento | Hoje | Onde |
|---|---|---|
| Mudança de Safe Mode (manual e automática, com motivo) | Gravado | `safe_mode_changes` |
| Comando do operador | Gravado | `operator_actions` |
| Entrada na DLQ | Gravado | `dead_letter_entries` |
| Venda pela proteção do lote (T44) | Gravado | transação `SELL` com `reasoning` "protective exit ..." |
| Mudança do catálogo de estratégias | Gravado | `remote_strategy_events` |
| Mudança de estado do runner (inclusive o halt automático por rejeições) | **Perdido** | `strategy_runners` guarda só o estado atual e `status_changed_at` |
| Execução do boot (resultado, fase da falha, duração) | **Perdido no restart** | só em memória (`BootStatusTracker`) |

Além disso, não há consulta única: reconstruir a rodada exige juntar cinco endpoints, os logs e o SQL.

## Solução proposta

### Parte A — Persistir o que se perde

1. **`runner_status_changes`**: `runner_id`, `from_status`, `to_status`, `origin` (`MANUAL`,
   `AUTOMATIC`, `BOOT`; mesmos nomes do Safe Mode), `reason`, `changed_at`. Mesmo formato de `safe_mode_changes`. Gravada no mesmo
   ponto e na mesma transação em que o runner muda de estado. O halt automático por rejeições
   (`ConsecutiveRejectionGuard`) grava o motivo com a contagem e o último código de rejeição.
2. **`boot_runs`**: `run_id`, `mode`, `status` (`SUCCESS`/`FAILED`), `started_at`, `finished_at`,
   `failure_phase`, `failure_message`. Gravada pelo fim do boot (sucesso ou falha). `/api/boot/status`
   continua mostrando o boot corrente em memória; o histórico passa a vir da tabela.
3. A regra do runner fica no `core` (a entidade já sabe de onde veio a transição; o use case grava pelo
   port de repositório). O `spring-application` só implementa o adapter JDBC.

### Parte B — `GET /api/admin/events`

`GET /api/admin/events?since=<instante>&until=<instante>&portfolioId=&limit=` — somente leitura, ordem
cronológica, paginada pelo instante. Cada item: `type` estável, `occurredAt`, alvo (`portfolioId`,
`runnerId`, `transactionId`, `strategyRef` quando houver), `origin`, `reason`, `detail`.

| `type` | Fonte |
|---|---|
| `SAFE_MODE_CHANGED` | `safe_mode_changes` |
| `RUNNER_STATUS_CHANGED` | `runner_status_changes` (nova) |
| `PROTECTIVE_EXIT` | transações de saída protetiva |
| `DLQ_ENTRY` | `dead_letter_entries` |
| `OPERATOR_ACTION` | `operator_actions` |
| `STRATEGY_CATALOG_CHANGED` | `remote_strategy_events` |
| `BOOT_COMPLETED` / `BOOT_FAILED` | `boot_runs` (nova) |

- **Não duplica dado:** lê de cada tabela de origem. Uma consulta de leitura no `core` (use case + port)
  compõe as fontes; o adapter JDBC faz a união ordenada.
- **Não é log em banco:** só mudanças de estado com significado de negócio (dezenas por dia). Tick,
  avaliação e chamada remota continuam só no log.

### Parte C — Relatório de fim de rodada (procedimento, sem código)

`docs/monitoring/round-report.md`: procedimento para o agente no fim de cada rodada da T53, que produz
a entrada do "Registro de execuções":

1. Linha do tempo da rodada: `GET /api/admin/events?since=<início>&until=<fim>`.
2. `GET /api/admin/operational-state?includeExchange=true` no fim.
3. Logs `ERROR` e `WARN` no Loki do período, agrupados por mensagem; cada `ERROR` classificado (ctrade
   ou testnet).
4. Tendências no Prometheus (memória e threads da JVM, latência de avaliação e de conciliação) entre o
   início e o fim (critério 7 da T53).
5. Conferência dos 8 critérios da T53, com evidência de cada um.
6. Resumo: aprovada ou não, achados novos (com F-número), ações tomadas.

## Decisões registradas (2026-10-21)

1. **Duas tabelas específicas** (`runner_status_changes`, `boot_runs`), como `safe_mode_changes`:
   campos tipados, constraints de domínio, sem coluna `payload` livre. A linha do tempo unifica a
   leitura.
2. **Sem expurgo** por enquanto (dezenas de linhas por dia).
3. **Parte C junto com A e B**, exercitada na próxima rodada local (fase 1 da T53).

## Fora do escopo

- Verificação periódica durante a rodada (descartada; ver "Histórico do escopo").
- Gestão agêntica de estratégias (`.ai/agentic-strategy-roadmap.md`).
- Histórico de transições de transação ou de ordem (já ficam em `transactions` e na conciliação).

## Riscos

- **Transição de runner sem registro:** todo caminho que muda o estado precisa gravar. Mitigação: a
  gravação fica num único ponto do `core` (um fluxo, um caminho) e o teste de integração percorre os
  caminhos (`start`, `halt` manual, halt automático, `resume`, `terminate`, boot).
- **Falha do boot antes do banco:** se o boot falha por falta de banco, `boot_runs` não grava. Aceito:
  o log continua sendo a fonte nesse caso.
- **Consulta lenta com muitas fontes:** índice por instante em cada tabela de origem; `limit` máximo.

## Critérios de aceitação

1. Toda mudança de estado do runner, manual ou automática, aparece em `runner_status_changes` com
   origem e motivo.
2. Um boot com sucesso e um com falha aparecem em `boot_runs` e sobrevivem ao restart.
3. Um HALT automático seguido de retomada manual, uma saída protetiva e um halt de runner por rejeições
   aparecem em `/api/admin/events` na ordem certa, com os tipos da tabela.
4. `since`/`until`/`limit` funcionam e a resposta segue o padrão da T55 (`X-Admin-Key`, OpenAPI).
5. O relatório de uma rodada da T53 é produzido só com a API, o Loki e o Prometheus, sem SQL.

## Validação

- Unitários: gravação da transição em cada caminho do runner; use case da linha do tempo (cada fonte,
  ordem, filtros, paginação).
- Integração com Postgres: o cenário do critério 3; boot gravado.
- Testnet: próxima rodada local com o relatório da Parte C.

## Entrega (2026-10-21)

- **Domínio:** toda transição do `StrategyRunner` exige `RunnerStatusCause` (origem + motivo) e gera um
  `RunnerStatusChange`; a entidade guarda as transições ainda não gravadas e
  `StrategyRunnerRepositoryPort.save` as grava com o runner, na mesma transação (`drainStatusChanges`).
  Um caminho só: nenhum chamador grava histórico à parte.
- **Causas:** comandos do operador (`start`, `halt`, `resume`, `terminate`) usam o `X-Reason` como motivo
  (`MANUAL`); rejeições seguidas e falha crítica de conexão são `AUTOMATIC` com contagem/canal no motivo;
  arquivamento do runner zerado é `AUTOMATIC`; a reconciliação usa a causa de quem a pediu (`BOOT` no
  boot, `MANUAL` no start/resume), e o halt por erro de reconciliação leva o primeiro erro no motivo.
- **Boot:** `RunBootSequenceUseCase` grava `BootRun` em `boot_runs` ao terminar (sucesso ou falha, com a
  fase que falhou); falha ao gravar só loga.
- **Linha do tempo:** `GET /api/admin/events` → `QueryEventTimelinePort` (core: intervalo padrão de 24h,
  `limit` padrão 200 e máximo 1000, `truncated`/`nextSince`) → `JdbcEventTimelineRepositoryAdapter`
  (`UNION ALL` das sete fontes). A saída protetiva é reconhecida por
  `PositionProtection.EXIT_REASONING_PREFIX`, o mesmo prefixo que a proteção grava.
- **Migration V19:** `runner_status_changes`, `boot_runs` e índices por instante em `safe_mode_changes`,
  `dead_letter_entries` e `remote_strategy_events`. `reset_testnet_database.sql` limpa as tabelas novas.
- **Parte C:** `docs/monitoring/round-report.md`; o fechamento da T53 aponta para ele.
- **Testes:** `StrategyRunnerInvariantsTest` (+2: registro com causa e esvaziamento; transição recusada
  não registra), `QueryEventTimelineUseCaseTest` (3), `ConsecutiveRejectionGuardTest` e
  `CriticalConnectionFailureHandlerTest` (causa `AUTOMATIC`), `BootOrchestratorBootMinimumTest` (boot
  gravado no sucesso e na falha), `EventTimelineIntegrationTest` (Postgres: start, halt automático, HALT
  automático e retomada manual do Safe Mode, resume, saída protetiva e DLQ em ordem; filtro de portfolio;
  paginação; boot).

## Fontes

- `.ai/tasks/T53-testnet-soak.md` (decisão 7, revista), `.ai/tasks/T55-ai-operability.md`
- `.ai/product-direction.md` (princípios 4 e 7)
- `docs/OPERATIONS_RUNBOOK.md`
