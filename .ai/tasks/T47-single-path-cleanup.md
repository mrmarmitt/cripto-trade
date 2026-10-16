# T47 — Um caminho por operação (limpeza pós-T46)

**Complexidade:** Baixa (refactor sem mudança de comportamento)
**Responsável:** Claude
**Dependências:** T46.
**Status:** Concluído (2026-10-16)

---

## Descrição

A T46 deixou caminhos paralelos para a mesma operação, contrariando a regra "um fluxo, um caminho"
(`/.ai/development-manual.md`, seção 1.3):

| Onde | Problema |
|---|---|
| `Position` | Construtor público por preço médio (só testes) ao lado de `openAtCost` (produção) |
| `Position` | `addQuantity(qty, price)` sem chamador ao lado de `addQuantityAtCost(qty, cost)` |
| `OrderDataDto` | Construtor de 13 argumentos que preenchia `feeAsset = null` sem o produtor declarar |
| `adapter-mock` | `FeeMode.SAME_CURRENCY` sem cenário consumidor; com ele, `estimatedBuyQuoteFee` e o cálculo da reserva de quote repetido em `OrderValidator` e no simulador |

## Entrega

- `Position.open(runnerId, symbol, quantity, cost)` é a única forma de abrir posição (construtor
  privado); `addQuantity(quantity, cost)` é a única forma de aumentar. Preço médio sempre derivado do custo.
- `OrderDataDto` só tem o construtor canônico: todo produtor passa `feeAsset` (nulo explícito em
  eventos sem fee, rejeições e consultas que não informam o ativo).
- Mock: `FeeMode` = `NONE` | `RECEIVED_ASSET`. `FeeModel` com um cálculo (`calculateFee`) e um
  ativo (`feeAsset`); reserva/liberação de quote da BUY por `OrderValidator.buyReservation`.

## Validação

`:core:test` 150, `:adapter-binance:test` 73, `:adapter-mock:test` 24 (saiu o teste do modo
removido), `:spring-application:test` 178/178. Sem mudança de comportamento.
