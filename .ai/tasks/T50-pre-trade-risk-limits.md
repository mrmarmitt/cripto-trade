# T50 — Limites de Risco Pré-Trade

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** nenhuma; ganha urgência com a T41 (estratégia remota escolhendo preço e quantidade).
**Status:** Pendente — **decisão de negócio pendente** (valores padrão).

---

## Descrição

Achado da T39: o design define limites que o código não aplica.

| Origem | Limite | Estado |
|---|---|---|
| IG §7.1 passo 4, §7.4.1 | `maxOpenPositions` por runner (`MAX_POSITIONS_REACHED`) | Campo persistido, sem validação |
| IG §7.1 passo 4, §7.4.1 | `maxPendingOrders` por runner (`MAX_PENDING_REACHED`) | Campo persistido, sem validação |
| IG §7.3 V5 | Sanidade de preço: ordem com desvio > X% do último preço é descartada | Inexistente |
| BLUEPRINT §11.2.D | Gate de utilização global de margem (ex.: 90% do total) | Inexistente |

Hoje só a política SINGLE e o `maxAllocationPercent` limitam o runner. Com a estratégia fora do
processo (T41), um `limitPrice` errado (ex.: casas decimais trocadas) vira ordem real.

## Solução proposta

- Validações no core, no caminho do sinal, **antes** do persist (mesmo ponto das guardas atuais):
  contagem de posições e ordens em voo (reaproveitar `RunnerExposureService`), desvio do preço da
  ordem contra o preço do tick, utilização global contra o `GlobalBalance`.
- Cada rejeição com motivo próprio em `SignalDecision`/`RejectionReason` e métrica.

## Decisões pendentes

- Desvio máximo de preço (sugestão do IG: 10%) e se vale para BUY e SELL.
- Teto de utilização global (sugestão do Blueprint: 90%) — ou descartar.

## Critérios de aceitação

1. Runner com `maxOpenPositions`/`maxPendingOrders` atingido não abre nova ordem.
2. Sinal com preço fora do desvio é descartado com motivo e métrica.
3. Nenhuma regra nova duplica o cálculo de exposição existente.
