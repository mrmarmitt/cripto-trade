# T11 — Order Quantity Normalization Before Persist

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T6  
**Status:** Concluído

---

## Descrição

Quando `OrderFilterValidator` aplica floor em uma quantidade não alinhada ao `stepSize` (ex: `0.001234` → `0.00123`), a `Transaction` já foi persistida e o capital já foi reservado com a quantidade original. O fill da Binance confirma `0.00123`, mas a reserva foi feita para `0.001234`.

Resultado: over-reservation da diferença (`qty % stepSize`) até a próxima reconciliação de saldo — o portfolio pensa que tem menos capital do que de fato tem. O erro é conservador (não causa perda real) mas afeta a precisão da contabilidade de capital e deve ser corrigido antes de produção.

**Raiz do problema:** a normalização de quantidade ocorre em `OrderDispatchAdapter`, depois que `BuySignalHandler.persistenceAction.persist()` e a reserva de capital já aconteceram. O core não conhece regras de filter da exchange.

---

## Escopo técnico

### Port novo no core

```java
// core/.../ports/outbound/exchange/rest/OrderQuantityNormalizerPort.java
public interface OrderQuantityNormalizerPort {
    String getExchangeName();
    BigDecimal normalizeQuantity(String symbol, BigDecimal quantity);
    BigDecimal normalizePrice(String symbol, BigDecimal price);
}
```

### Implementação no adapter-binance

`BinanceOrderNormalizer` implementa o port usando `SymbolFilterCache` — mesma lógica de floor/round de `OrderFilterValidator`, mas exposta como port outbound.

### Injeção no use case de signal

`ProcessTradeSignalUseCase` recebe uma lista de `OrderQuantityNormalizerPort` (um por exchange) e normaliza quantidade e preço **antes** de criar o `BuyExecutionContext` / `SellExecutionContext`. A quantidade que entra no persist, na reserva de capital e no dispatch é sempre a quantidade já normalizada.

### Remoção da normalização do dispatch

Após a normalização ser movida para antes do persist, `OrderFilterValidator.validate()` mantém apenas as checagens de rejeição (`minQty`, `maxQty`, `minNotional`) — sem mais floor/round. Se a quantidade não estiver alinhada ao chegar no dispatch, lança `OrderFilterViolationException` (bug do chamador).

---

## Critérios de aceitação

1. A quantidade e o preço persistidos na `Transaction` são idênticos aos enviados para a exchange.
2. A reserva de capital (`BuySignalHandler`) usa a quantidade pós-normalização.
3. O lock de posição SELL (`SellSignalHandler`) usa a quantidade pós-normalização.
4. Se a exchange não tiver normalizador registrado (ex: MOCK), o use case usa a quantidade original sem modificação.
5. Testes de integração verificam que `transaction.quantity == order.sentQuantity` após fill.

## Observação

O caminho de **rejeição** (qty < minQty, notional < minNotional) já está correto desde T6/`5bd53f7`: `OrderFilterViolationException` é capturada em `OrderDispatchAdapter.dispatch()` e o domínio é desfeito via `orderConciliation.execute(REJECTED)`. Esta tarefa cobre apenas o caminho de **ajuste** (floor de quantidade válida).
