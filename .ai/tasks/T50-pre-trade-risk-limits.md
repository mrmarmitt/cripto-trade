# T50 — Limites de Risco Pré-Trade

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** nenhuma; ganha urgência com a T41 (estratégia remota escolhendo preço e quantidade).
**Status:** Concluído (2026-10-19) — ver "Entrega".

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

---

## Entrega (2026-10-19)

### Decisões de negócio (usuário, 2026-10-19)

- **Desvio máximo de preço: 10%, BUY e SELL**, fixo no core (`PreTradeRiskPolicy.MAX_PRICE_DEVIATION`).
- **Teto de utilização global: descartado.** `maxAllocationPercent` por runner + reserva atômica no
  `GlobalBalance` já impedem passar do capital; perda fica com o drawdown da T43.

### Decisões de implementação

- **Um ponto de decisão:** `PreTradeRiskPolicy`, chamado em `processTradeSignal` logo depois da guarda
  de execução (`RunnerSignalPolicy`) e antes da normalização/persistência. Nada é gravado nem
  reservado quando recusa.
- **Contagens no snapshot existente:** `RunnerExposureService.ExposureSnapshot` passou a trazer
  `openPositions` e `inFlightOrders` (`hasOpenOrInFlight` virou derivado). O snapshot é carregado para
  todo BUY (antes só SINGLE), o que também entrega a exposição em voo pré-calculada à reserva.
- **`maxOpenPositions`/`maxPendingOrders` só no BUY** (IG §7.1 passo 4 os coloca na reserva). SELL só
  reduz exposição: nunca é barrada por contagem, para não travar stop do lote, liquidação ou saída da
  estratégia. Com a política SINGLE (padrão de todo runner), a guarda SINGLE responde antes
  (`REJECTED_POLICY`); os limites passam a valer para políticas que permitem mais de uma ordem.
- **Sanidade de preço só com `limitPrice`:** sinal sem preço usa o tick; as saídas do core (MARKET)
  nunca trazem preço.
- **Desfechos próprios:** `REJECTED_PRICE_DEVIATION`, `REJECTED_MAX_POSITIONS`, `REJECTED_MAX_PENDING`
  em `signal.evaluated.total`, com log `preTradeRisk: ...`.
- **Estratégias de cenário:** `restingOffset` padrão caiu de 50% para 5% — com 50% toda ordem que
  "descansa" seria descartada pelo desvio. 5% ainda descansa no MOCK e na testnet.

### Validação

- `./scripts/gradle-run.ps1 test` verde (core 219, spring-application 208, strategy 23; demais módulos
  inalterados).
- `PreTradeRiskPolicyTest`: limites de ±10% exatos passam, além disso recusa (BUY e SELL), sem
  `limitPrice` passa, limites de posições/ordens no BUY, SELL nunca barrada por contagem.
- `PreTradeRiskE2ETest` (MOCK): BUY com casa decimal trocada não cria transação nem reserva e conta
  `REJECTED_PRICE_DEVIATION` (critério 2).
- E2E dos cenários (T38), Safe Mode (T45) e proteção do lote (T44) verdes com o novo offset.
- Critério 3: nenhum cálculo de exposição duplicado — as contagens saem do mesmo snapshot.

