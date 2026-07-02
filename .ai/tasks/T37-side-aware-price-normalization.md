# T37 — Normalização de Preço Side-Aware (arredondamento por lado da ordem)

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T34 (introduziu `limitPrice` dirigido pela estratégia, que dá relevância prática ao arredondamento)
**Status:** Pendente

---

## Descrição

O alinhamento de preço ao `tickSize` é hoje **side-agnostic**: `BinanceOrderNormalizer.normalizePrice` arredonda com `RoundingMode.HALF_UP`. Isso pode deslocar o preço da ordem em até **meio tick** numa direção desfavorável ao trader:

- **BUY**: um preço não alinhado é arredondado para cima → paga-se **mais** que o pretendido.
- **SELL**: é arredondado para baixo → recebe-se **menos** que o pretendido.

O correto é arredondamento **conservador por lado**, espelhando o que a quantidade já faz (`normalizeQuantity` usa `FLOOR`):

- **BUY** → arredonda o preço para **baixo** (`FLOOR` ao tick) — nunca acima do teto pretendido.
- **SELL** → arredonda o preço para **cima** (`CEILING` ao tick) — nunca abaixo do piso pretendido.

## Origem

Achado do review automatizado (Codex) no **PR #132 (T34)**, comentário P2 em
`ProcessTradeSignalUseCase.java:319`.

**Importante — não é regressão da T34:** o arredondamento HALF_UP já existia; antes da T34 o
`currentPrice` de mercado passava pelo mesmo normalizador. A T34 só mudou a *origem* do preço.
A invariante da T34 (**preço persistido = reservado = enviado**) permanece intacta — os três usam
o mesmo valor normalizado. O que a T37 corrige é a *direção* do arredondamento em relação à
intenção da estratégia, não uma divergência entre as três pontas.

**Impacto prático hoje é baixo:** as estratégias de cenário da T35 usam offsets percentuais longe
do mercado, onde meio tick é ruído. A correção é sobre **semântica correta** do contrato de preço,
valiosa quando estratégias operarem com preços-limite próximos de bandas sensíveis.

---

## Contexto técnico

O `normalizePrice` não conhece o lado da ordem, então tornar o arredondamento side-aware exige
**propagar o lado (BUY/SELL) até o normalizador** — mudança de contrato no port:

- `core/.../ports/outbound/exchange/rest/OrderQuantityNormalizerPort.java`
  `normalizePrice(String symbol, BigDecimal price)` → adicionar o lado (ex.: `TransactionType`/enum de lado).
- `core/.../usecase/runner/processsignal/OrderNormalizer.java`
  `normalize(exchangeName, symbol, quantity, price)` → receber e repassar o lado.
- `core/.../usecase/runner/processsignal/ProcessTradeSignalUseCase.java`
  já conhece o lado via `signal.decision()`/`transaction.getType()` — passar para o `OrderNormalizer`.
- `adapter-binance/.../filters/BinanceOrderNormalizer.java`
  aplicar `FLOOR` (BUY) / `CEILING` (SELL) no lugar de `HALF_UP`. `normalizeQuantity` permanece `FLOOR`.
- Exchanges sem normalizador (MOCK) continuam usando o valor original — sem efeito.

Como só há um implementador do port (`BinanceOrderNormalizer`), o ripple é contido, mas **é mudança
de fronteira compartilhada** (port do core consumido pelo adapter) e afeta **todas as ordens**,
inclusive a `SimpleMovingAverageStrategy` a preço de mercado — por isso não coube na T34.

---

## Riscos / pontos de atenção

- **Área financeira:** o preço alimenta reserva (`quantity × price`) e dispatch. Cobrir com teste que
  o valor normalizado nunca ultrapassa (BUY) / fica abaixo (SELL) do preço pretendido.
- **Preservar a invariante T34:** persistido = reservado = enviado — o novo arredondamento acontece
  no mesmo ponto único (antes do persist), então a igualdade das três pontas se mantém.
- **`OrderFilterValidator` no dispatch:** continua apenas validando alinhamento ao tick; não deve
  reajustar. Confirmar que preços FLOOR/CEILING passam na validação.
- **Compatibilidade do port:** mudar a assinatura de `normalizePrice` obriga revisar todos os
  chamadores e implementadores (hoje: `OrderNormalizer` e `BinanceOrderNormalizer`).

---

## Critérios de aceitação

1. `normalizePrice` passa a arredondar por lado: BUY → `FLOOR` ao tick, SELL → `CEILING` ao tick.
2. `normalizeQuantity` permanece `FLOOR` (inalterado).
3. O lado é propagado de `ProcessTradeSignalUseCase` → `OrderNormalizer` → `normalizePrice`.
4. Invariante preço persistido = reservado = enviado preservada; cobertura de teste para BUY e SELL
   com preços não alinhados ao tick.
5. Exchanges sem normalizador (MOCK) inalteradas.
6. `SimpleMovingAverageStrategy` (preço de mercado) continua funcionando; o arredondamento agora
   conservador não quebra seus fluxos.

---

## Fontes

- PR #132 (T34), comentário de review Codex (P2) em `ProcessTradeSignalUseCase.java:319`.
- `adapter-binance/.../filters/BinanceOrderNormalizer.java` (`normalizePrice` HALF_UP; `normalizeQuantity` FLOOR).
- `core/.../ports/outbound/exchange/rest/OrderQuantityNormalizerPort.java`.
- `core/.../usecase/runner/processsignal/OrderNormalizer.java`.
- `.ai/flows/runner-signal-processing.md` (invariante de normalização antes do persist).
- `.ai/tasks/T11-order-quantity-normalization.md` (fundação da normalização).
