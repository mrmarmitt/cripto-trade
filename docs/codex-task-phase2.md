# Boot Recovery - Phase 2 Taskboard (Atualizado)

Data de referencia: 2026-03-04

Este documento substitui o plano antigo e reflete o estado real do codigo.

## 1. Status atual da Phase 2 (Portfolio)

### 1.1 Concluido

- Contrato de status por fase (`PASS`, `WARN`, `FAILED`, `SKIPPED`) implementado.
- Modo configuravel (`WARN_ONLY` e `FAIL_FAST`) implementado.
- Sanity check de saldo local x exchange implementado.
- Zombie detection (classificacao) implementado.
- Reservation TTL no boot implementado.
- Cutoff temporal habilitavel implementado.
- Metricas e logs estruturados por fase implementados.
- Alerta de fail-fast via `BootFailFastEvent` implementado.

### 1.2 Parcialmente concluido

- Zombie detection identifica e classifica corretamente, mas ainda nao persiste DLQ da propria Phase 2.

### 1.3 Pendente para fechar a Phase 2

1. Persistir candidatos de zombie em `dead_letter_entries` (na fase portfolio), com `reason` e payload minimo de auditoria.
2. Definir acao por modo:
   - `WARN_ONLY`: persiste + alerta.
   - `FAIL_FAST`: persiste + interrompe boot.
3. Adicionar regra de bloqueio por DLQ pendente antes de liberar conclusao de boot do runner afetado.
4. Fechar testes de integracao mock-first cobrindo:
   - sanity pass/warn/fail,
   - zombie com roteamento para DLQ,
   - reservation TTL expirando apenas o elegivel.

## 2. Status atual do Boot Recovery (Runner)

### 2.1 Concluido

- Orquestracao em fases (`phase1`, `phase2`, `phase3`) com flags de enable.
- `RunnerBootRecoveryUseCase` com passos lineares:
  - snapshot de conta,
  - classificacao in-flight (`zombies`/`limbo`),
  - reconciliacao com exchange query quando disponivel,
  - expiracao sintetica de zumbis sem `exchangeOrderId`,
  - validacao final e persistencia.
- Integracao desacoplada com conciliacao via `ConciliationOrderUpdate`.
- Logs e metricas de observabilidade do boot em nivel de fase.

### 2.2 Parcialmente concluido

- O runner conclui reconciliacao com base em erros do contexto, mas ainda falta gate explicito por DLQ pendente.

### 2.3 Pendente para concluir Boot Recovery

1. Gate de DLQ pendente por runner/portfolio:
   - se houver DLQ aberta relacionada ao runner, nao concluir `reconciliation`.
   - manter runner bloqueado (`HALTED` ou `INITIALIZING`, conforme politica final).
2. Entrada explicita em modo de reconciliacao no inicio do recovery:
   - garantir `isReconciling=true` durante toda a execucao do boot do runner.
3. Endurecer retry/timeout de consultas REST de reconciliacao (step 4):
   - politica dedicada de timeout e retry para boot query.
4. Cobertura de testes de reinicio com base suja:
   - crash entre persist e dispatch,
   - limbo sem retorno da exchange,
   - zumbi expirado por TTL,
   - erro de consulta com degradacao controlada.

## 3. Hardening de eventos de capital (fora da Phase 2, mas relevante)

Concluido nesta etapa:

- Ledger de idempotencia para eventos financeiros (`capital_event_ledger`).
- Retry seletivo e configuravel para `ExecutionConfirmed` e `MarginRelease`.
- Persistencia em DLQ para retries esgotados de eventos de capital (`RETRY_EXHAUSTED`).

Observacao:

- Este bloco melhora a resiliencia geral do sistema, mas nao substitui as pendencias da Phase 2/Boot acima.

## 4. Definicao objetiva de pronto

Phase 2 concluida quando:

1. Zombie detection rotear para DLQ persistida.
2. Comportamento `WARN_ONLY` vs `FAIL_FAST` estiver deterministico e coberto por teste.
3. Nao houver liberacao de runner com DLQ pendente nao resolvida.

Boot recovery concluido quando:

1. Runner sempre entra e sai de reconciliacao de forma explicita e auditavel.
2. Falhas de exchange query tiverem politica de timeout/retry definida.
3. Cenarios de restart com dados sujos estiverem cobertos por testes de integracao.
