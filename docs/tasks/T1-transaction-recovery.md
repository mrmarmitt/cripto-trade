# T1 — Transaction Recovery

**Complexidade:** Alta  
**Responsável:** Mateus  
**Dependências:** nenhuma  
**Status:** Concluído

---

## Descrição

Quando a conexão WebSocket cai ou uma mensagem de fill não é entregue, a transação fica presa em estado transitório (`SUBMITTED` ou `PARTIAL`) sem convergir para `FILLED`, `CANCELED` ou `REJECTED`. O boot recovery (fase 3) já trata o caso de restart, mas não cobre drops silenciosos de mensagem durante operação normal.

Esta tarefa implementa o mecanismo de recuperação ativa de transações: um processo periódico que identifica transações em estado transitório há mais tempo do que o esperado e consulta a exchange para obter o estado real.

---

## Escopo técnico

**Use case:** `RecoverTransactionStatusUseCase` (já existe o esqueleto em `runner/`)

**Fluxo:**
1. Buscar transações com status `SUBMITTED` ou `PARTIAL` cujo `updatedAt` seja mais antigo que o threshold configurado
2. Para cada transação, consultar a exchange via `ExchangeOrderQueryPort.queryOrderByClientOrderId`
3. Se encontrada: aplicar o status retornado via `ConciliationOrderUpdate` (mesmo fluxo do WebSocket, garantindo idempotência)
4. Se não encontrada: depende do estado — pode ser candidata a DLQ ou terminal sintético, conforme regras do Blueprint

**Invariantes que devem ser preservadas:**
- Idempotência: se a transação já foi conciliada via WebSocket antes da query chegar, a query não reaplica efeito financeiro (o `ConciliationOrderUpdate` já garante isso)
- Status monotônico: uma transação em estado terminal (`FILLED`, `CANCELED`, etc.) não deve ser reprocessada
- Não criar duplicatas de DLQ para a mesma identidade (`portfolioId + runnerId + clientOrderId + reason`)

**Configuração esperada:**
```yaml
runner:
  recovery:
    transaction:
      enabled: true
      interval-ms: 60000        # frequência de varredura
      stale-threshold-ms: 30000 # tempo mínimo em estado transitório para considerar candidata
      max-per-run: 50           # máximo de transações por varredura (evitar sobrecarga)
```

---

## Critérios de aceitação

1. Transações em `SUBMITTED` ou `PARTIAL` há mais tempo que `stale-threshold-ms` são identificadas na varredura.
2. Para cada transação candidata, a exchange é consultada uma vez por ciclo de varredura.
3. Se a exchange retorna `FILLED`, a conciliação é aplicada sem efeito duplicado (idempotente com o fluxo WebSocket).
4. Se a exchange retorna `CANCELED` ou `REJECTED`, a reserva de capital é liberada corretamente.
5. Se a exchange não encontra a ordem (`Optional.empty()`), a transação é registrada no DLQ sem duplicar entrada existente para a mesma identidade.
6. Transações em estado terminal não são incluídas na varredura.
7. Se a query falhar por erro transitório, a transação é ignorada neste ciclo e tentada no próximo (sem lançar exceção que aborte a varredura inteira).
8. O número máximo de transações por varredura é respeitado (`max-per-run`).
9. A funcionalidade pode ser desabilitada via `runner.recovery.transaction.enabled: false` sem alterar o comportamento de outras partes do sistema.

## Testes de integração obrigatórios

Usar a infraestrutura de integração com MOCK já existente (mesma base de `RunnerBootRecoveryIntegrationTest`).

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Transação `SUBMITTED` presa → MOCK retorna `FILLED` | `transactions.status = FILLED`, `positions` atualizada, `global_balance` com reserva liberada, sem duplicata em `transaction_matches` |
| Transação `PARTIAL` presa → MOCK retorna `FILLED` | Apenas o delta entre `executedQty` anterior e atual é aplicado; `global_balance` coerente |
| Transação `SUBMITTED` presa → MOCK retorna `CANCELED` | Reserva liberada, `transactions.status = CANCELED`, sem entrada em `positions` |
| Transação não encontrada na exchange (`Optional.empty()`) | Entrada criada em `dead_letter_entries`; segunda execução do recovery não duplica a entrada |
| Transação já terminal antes da varredura | Não incluída na varredura; nenhum efeito financeiro adicional |
| Query com falha transitória | Transação ignorada no ciclo atual; varredura continua sem abortar; retentativa no ciclo seguinte |
| Recovery desabilitado (`enabled: false`) | Nenhuma varredura ocorre; fluxo normal de conciliação por WebSocket não é afetado |
