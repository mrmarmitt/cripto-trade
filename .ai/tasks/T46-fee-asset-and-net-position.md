# T46 — Ativo da Fee e Quantidade Líquida da Posição

**Complexidade:** Alta (efeito financeiro em posição, PnL e venda)
**Responsável:** Claude
**Dependências:** T36 (devolução do excedente). Independe das demais.
**Status:** Pendente — **decisão de negócio pendente** sobre fee em terceiro ativo (BNB), ver abaixo.

---

## Descrição

A fee de execução é tratada de forma incompleta em dois pontos, e um deles **quebra a venda em
exchange real**:

1. **O ativo da fee é descartado na borda.** O `ExecutionReportProcessor` (adapter-binance) lê o
   valor da comissão (`n`), mas ignora o ativo (`N`). `OrderDataDto` só tem `fee` (`BigDecimal`), sem
   ativo. O core então **assume** que toda fee está no ativo de cotação: o `SellFillHandler` monta
   `Fee.sameBaseCurrency(feeAmount, quoteAsset, ...)`, o que só está certo se a Binance cobrou em USDT.
2. **A fee da BUY é ignorada.** O `BuyFillHandler` não lê `orderData.fee()`. Ela não entra no PnL, em
   `totalFeesPaid` nem na quantidade da posição.

### Por que isso quebra a venda

Na Binance Spot, a comissão de uma BUY é cobrada, por padrão, **no ativo recebido** (base). Comprar
`0.001 BTC` com fee de 0,1% credita `0.000999 BTC` na conta. A posição local registra `0.001`
(quantidade bruta), e a estratégia recebe `availableQuantity = 0.001` como teto de venda. Uma SELL de
`0.001` é **rejeitada pela exchange por saldo insuficiente**, e o lote nunca fecha. Isso não aparece
no MOCK porque ele não modela o ativo da comissão.

---

## Contexto técnico

| Ponto | Situação atual |
|---|---|
| `adapter-binance/.../processor/receive/ExecutionReportProcessor.java` | `fee = n`; `N` (ativo) descartado. `n` é a comissão **deste** trade (por incremento), coerente com o uso atual |
| `adapter-binance/.../trade/BinanceTradeHistoryAdapter.java` | Já lê `commission` **e** `commissionAsset` (reconciliação T18) |
| `core/.../dto/websocket/data/OrderDataDto.java` | `fee` sem ativo |
| `core/.../orderconciliation/BuyFillHandler.java` | Ignora fee; `position.addQuantity(fillIncrement, fillPrice)` com quantidade bruta |
| `core/.../orderconciliation/SellFillHandler.java` | `Fee.sameBaseCurrency(fee, quoteAsset)`, assume cotação |
| `core/.../domain/shared/Fee.java` | Já tem `sameBaseCurrency`, `crossCurrency`, `pendingConversion` |
| `adapter-mock/.../simulator/FeeModel.java` | Calcula fee sobre o valor bruto, sem ativo |

---

## Solução proposta

### 1. Borda (adapter → contrato do core)

- `OrderDataDto` ganha `feeAsset` (`String`, nulo = desconhecido). Mudança de contrato do core:
  atualizar **todos** os produtores (Binance WS e REST, MOCK, Coinbase) e os testes.
- `ExecutionReportProcessor` mapeia `N`; respostas REST/WS API de ordem (`fills[].commissionAsset`)
  também, quando trouxerem fill.

### 2. Regra no core (dono: conciliação)

Classificação da fee por ativo, em um colaborador do pacote `orderconciliation` (ex.: `FeeResolver`),
usada pelos dois handlers:

| Fee em | BUY | SELL |
|---|---|---|
| **Base** (ex.: BTC) | Posição recebe `fillIncrement − fee` (quantidade **líquida**); custo do lote continua sendo o quote pago, então o preço médio efetivo sobe | Reduz o quote recebido no PnL |
| **Cotação** (ex.: USDT) | Custo do lote = executado + fee; a fee sai do capital (ver ponto de capital abaixo) | Comportamento atual (desconta do PnL) |
| **Terceiro ativo** (ex.: BNB) | Ver decisão de negócio | Ver decisão de negócio |
| Desconhecido (`feeAsset` nulo) | Comportamento atual + WARN | Comportamento atual + WARN |

**Ponto de capital (fee de BUY em cotação):** após a T36, `reserved` de um lote aberto é exatamente o
custo executado. Se a fee de BUY sair em quote, ela precisa ser debitada do capital do portfolio, e o
custo que a venda baixa de `reserved` precisa ser coerente. Definir na implementação se a fee entra no
custo do lote (e é baixada na venda) ou se é debitada de `available` no fill. Em qualquer caso, a
invariante `reserved ≥ custo a baixar` precisa continuar valendo.

### 3. Quantidade vendável

O teto de SELL (`availableQuantity` do lote, `OpenLotDto`) passa a ser a quantidade líquida.
Considerar a normalização por `stepSize`: a quantidade líquida pode deixar resíduo abaixo do
`stepSize` (dust). Esse resíduo não pode impedir o lote de fechar: definir se o lote fecha com dust
registrado (`DustAccount` já existe no domínio) ou se a venda arredonda para baixo e o resto é baixado
como dust.

### 4. MOCK

`FeeModel` passa a declarar o ativo (configurável: base ou cotação) para que os e2e cubram os dois
casos. Default backward-compatible.

---

## Decisão de negócio necessária

**Fee em terceiro ativo (BNB).** Com "pagar taxas com BNB" ligado na conta, a Binance cobra em BNB.
Contabilizar exige converter BNB para a moeda base do portfolio (preço de mercado no momento do fill)
e manter saldo de BNB fora do `GlobalBalance`. Opções:

- **A (recomendada para v1):** não suportar. Exigir a opção BNB **desligada** na conta (testnet e
  produção), documentar no runbook e, se chegar fee em ativo que não seja base nem cotação, registrar
  `Fee.pendingConversion`, logar ERROR e alertar (Discord), sem quebrar a conciliação.
- **B:** suportar conversão via preço de mercado. Maior escopo, exige fonte de preço BNB/quote.

---

## Riscos / pontos de atenção

- Área de alto risco: posição, PnL, `transaction_match` e capital. Seguir `docs/PHASE0_INVARIANTS.md`.
- Mudança de contrato do `OrderDataDto` toca todos os adapters; compilar todos os consumidores.
- Idempotência: a fee é por incremento; FILLED/PARTIAL duplicado já é descartado antes do fill. Não
  aplicar fee em evento descartado.
- O `FillCalculator` usa quantidade cumulativa da exchange (bruta); a quantidade líquida é só da
  posição, não da transação (a transação continua refletindo o que a exchange executou).
- Reconciliação T18 (`binanceOnly`/`localOnly`) compara trades; confirmar que a quantidade líquida não
  gera divergência falsa.

---

## Critérios de aceitação

1. `OrderDataDto` carrega o ativo da fee, preenchido pela Binance (WS e REST) e pelo MOCK.
2. BUY com fee em base abre lote com quantidade líquida; a SELL do lote inteiro usa essa quantidade e
   é aceita (e2e no MOCK com fee em base).
3. BUY com fee em cotação contabiliza a fee no capital sem violar `reserved ≥ custo a baixar`;
   round trip fechado mantém `reserved = 0` (e2e da T36 continua verde).
4. Fee de SELL respeita o ativo informado.
5. Fee em ativo não suportado segue a opção decidida, sem quebrar a conciliação.
6. Dust resultante não impede o fechamento do lote.
7. `:core:test`, `:adapter-binance:test`, `:adapter-mock:test` e `:spring-application:test` verdes;
   flow maps `order-conciliation.md` e `portfolio-capital.md` atualizados.

---

## Fontes

- `.ai/tasks/T36-capital-reservation-safety-buffer.md` (seção "Achado novo").
- `adapter-binance/src/main/java/com/marmitt/binance/processor/receive/ExecutionReportProcessor.java`.
- `adapter-binance/src/main/java/com/marmitt/binance/trade/BinanceTradeHistoryAdapter.java`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/orderconciliation/BuyFillHandler.java`,
  `SellFillHandler.java`.
- `core/src/main/java/com/marmitt/core/domain/shared/Fee.java`, `core/src/main/java/com/marmitt/core/domain/portfolio/DustAccount.java`.
- `adapter-mock/src/main/java/com/marmitt/mock/simulator/FeeModel.java`.
- `docs/PHASE0_INVARIANTS.md`.
