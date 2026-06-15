# T6 — Exchange Symbol Filters

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T5  
**Status:** Concluído

---

## Descrição

A Binance rejeita ordens que violam regras de precisão e tamanho mínimo do símbolo. Essas regras variam por símbolo e são publicadas pelo endpoint `GET /api/v3/exchangeInfo`.

Se uma ordem for enviada com quantidade ou preço fora das regras, a Binance retorna HTTP 400 com código `-1111` (bad precision) ou `-1013` (filter failure). Isso desperdiça o clientOrderId, pode gerar estado inconsistente e polui logs de staging.

Esta tarefa implementa a camada de validação local que aplica esses filtros antes de qualquer envio, rejeitando a ordem com erro claro no domínio.

---

## Escopo técnico

**Filtros a implementar:**

| Filtro Binance   | O que valida                                      | Ação                              |
|------------------|---------------------------------------------------|-----------------------------------|
| `LOT_SIZE`       | `qty` múltiplo de `stepSize`, entre `minQty` e `maxQty` | Arredondar para baixo (floor) ou rejeitar se abaixo do mínimo |
| `PRICE_FILTER`   | `price` múltiplo de `tickSize`                    | Arredondar para o tick mais próximo |
| `MIN_NOTIONAL` / `NOTIONAL` | `price × qty >= minNotional`         | Rejeitar com erro claro            |

**Fonte dos filtros:**
- `GET /api/v3/exchangeInfo?symbol=BTCUSDT` retorna a lista de filtros por símbolo
- Os filtros devem ser carregados uma vez na inicialização (ou no primeiro uso do símbolo) e cacheados em memória
- Cache deve ser invalidável manualmente via endpoint administrativo para evitar restart em mudanças de filtros

**Arquivos a criar/modificar:**
- `adapter-binance/src/main/java/com/marmitt/binance/filters/SymbolFilterCache.java` — novo, carrega e armazena filtros por símbolo
- `adapter-binance/src/main/java/com/marmitt/binance/filters/OrderFilterValidator.java` — novo, aplica filtros e ajusta quantidade/preço
- `spring-application/.../config/exchange/BinanceExchangeAdapter.java` — chamar `OrderFilterValidator` antes de `submitOrder`
- `spring-application/.../controller/AdminController.java` (ou novo) — endpoint para invalidar cache de filtros

**Comportamento esperado na validação:**

```
quantidade recebida: 0.001234 BTC
stepSize: 0.001
resultado após floor: 0.001 BTC

se resultado < minQty → rejeitar localmente com mensagem: 
"Quantity 0.001234 rounds down to 0.001, below minQty 0.01 for BTCUSDT"

price: 43521.7 USDT
tickSize: 0.01
resultado após ajuste: 43521.70 USDT

price × qty = 43.52 USDT
minNotional: 10.00 USDT
→ aprovado
```

**Restrições de implementação:**
- Arredondamento deve usar `BigDecimal.ROUND_DOWN` (floor) para quantidade — nunca arredondar para cima (evita enviar mais do que o capital reservado)
- Preço pode usar arredondamento convencional (meio tick para cima)
- Não usar `double` em nenhuma operação financeira — somente `BigDecimal`

---

## Critérios de aceitação

1. Ao iniciar com `BINANCE` como exchange, os filtros de todos os símbolos configurados são carregados do `exchangeInfo` e logados.
2. Uma ordem com `quantity` não múltiplo de `stepSize` tem a quantidade ajustada por floor antes do envio.
3. Uma ordem com `quantity` abaixo de `minQty` (após floor) é rejeitada localmente sem chegar na Binance, com mensagem de erro que inclui o símbolo, a quantidade recebida e o `minQty` exigido.
4. Uma ordem com `price × quantity < minNotional` é rejeitada localmente com mensagem clara.
5. O cache de filtros pode ser invalidado via `POST /api/admin/filters/refresh` sem restart da aplicação.
6. Nenhum `double` ou `float` é usado em cálculos de filtro — somente `BigDecimal`.
7. Se `exchangeInfo` retornar erro na inicialização, a aplicação falha no startup com mensagem clara indicando que os filtros não puderam ser carregados.

## Testes de integração obrigatórios

Usar MockWebServer para simular o endpoint `exchangeInfo` e a infraestrutura de integração com MOCK para exercitar o fluxo completo de envio de ordem.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Quantidade válida (múltiplo de stepSize, acima de minQty, notional suficiente) | Ordem enviada ao MOCK sem modificação de quantidade; nenhum erro lançado |
| Quantidade não múltipla de stepSize | Quantidade ajustada por floor antes do envio; valor ajustado logado |
| Quantidade abaixo de minQty após floor | Ordem rejeitada localmente; nenhuma chamada ao MOCK/exchange; mensagem de erro contém símbolo, quantidade recebida e minQty |
| `price × quantity < minNotional` | Ordem rejeitada localmente com mensagem clara; nenhuma chamada ao exchange |
| `POST /api/admin/filters/refresh` após alteração de filtros | Cache invalidado; próxima ordem usa filtros atualizados sem restart |
| `exchangeInfo` indisponível no startup | Aplicação falha no startup com mensagem de erro clara |
