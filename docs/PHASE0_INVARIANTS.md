# Fase 0 — Invariantes de Domínio e Matriz de Cenários

## Objetivo

Formalizar os contratos de domínio que os testes devem proteger, alinhados ao Blueprint e ao IG.

## Invariantes Críticas

### 1) Capital
- `available_balance` nunca pode ficar negativo.
- `reserved_balance` nunca pode ficar negativo.
- Reserva de BUY é aplicada no máximo uma vez por intenção válida.
- Liberação de margem em estado terminal não pode aplicar efeito financeiro duplicado.

### 2) Transação
- Transições de status são monotônicas (não voltam de terminal para transitório).
- Evento repetido não pode reaplicar alteração financeira.
- Transação terminal não volta para `PENDING`, `SUBMITTED` ou `PARTIAL`.

### 3) Position/Lot
- `opened_by_transaction_id` é obrigatório para posição ativa (`OPEN`/`CLOSING`).
- SELL nunca pode exceder `availableQuantity` da lot.
- Lock de SELL deve impedir lock concorrente no mesmo alvo.

### 4) Match e PnL
- `transaction_match` não pode duplicar efeito econômico.
- Soma de execução conciliada não pode exceder execução total da transação.
- `pnl_realized` deve ser coerente com preço de entrada/saída e fees.

### 5) Idempotência
- Reprocessar mesma atualização de ordem (`status`, `executedQty`, ids) não altera estado duas vezes.
- Fluxo `PARTIAL -> FILLED` aplica apenas o delta entre eventos.

### 6) Boot mínimo
- `WARN_ONLY` não deve bloquear runner apenas por DETECTED.
- `FAIL_FAST` deve registrar fase real de falha no status do boot.

## Matriz Inicial (Cenário x Resultado Esperado)

| Cenário | Resultado esperado |
| --- | --- |
| BUY happy path (`PENDING -> SUBMITTED -> FILLED`) | transação terminal coerente, reserva aplicada/consumida corretamente, posição/lot consistente |
| BUY com `PARTIAL -> FILLED` | efeitos financeiros incrementais sem duplicidade, estado final convergente |
| SELL happy path (`PENDING -> SUBMITTED -> FILLED`) | lock coerente, fechamento/atualização da lot, `transaction_match` consistente |
| Evento FILLED duplicado | sem dupla atualização de saldo/posição/match |
| Evento PARTIAL duplicado | sem dupla atualização incremental |
| REJECTED de BUY | liberação de reserva correta e sem efeito residual |
| REJECTED de SELL | sem corromper posição/lote e sem efeito financeiro indevido |
| Boot com zombie dentro TTL | permanece pendente sem expiração indevida |
| Boot com zombie fora TTL | expira/reconcilia e converge estado sem duplicidade |
| Boot limbo com ordem encontrada na exchange | conciliação para estado correto |
| Boot limbo com ordem não encontrada | fallback terminal previsto pelo domínio |
| Boot `WARN_ONLY` com DETECTED | sinaliza anomalia sem bloqueio indevido |
| Boot `FAIL_FAST` com erro crítico | run falha com fase/código corretos |

## Critério de Pronto do Item 1

- Invariantes e matriz aprovadas como referência da Fase 0.
- Cada cenário da matriz possui pelo menos uma assertiva de estado final definida para implementação nos testes.

