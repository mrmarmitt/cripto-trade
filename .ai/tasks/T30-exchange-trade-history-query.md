# T30 — Implementar consulta de histórico de trades na exchange

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T28 (camada agnóstica)  
**Status:** Concluído

---

## Descrição

Implementar a **consulta de histórico de trades** real através da camada agnóstica criada em T28, expondo a capacidade ao use case (e, se decidido, a um endpoint). A primeira implementação concreta é no `adapter-binance`, reusando a infraestrutura já existente; os demais adapters permanecem como stub (`UnsupportedOperationException`).

---

## Contexto técnico

A capacidade já existe parcialmente: `TradeHistoryQueryPort.fetchTrades(symbol, from, to)` retorna `List<TradeExecutionDto>`, implementada em `adapter-binance` (`BinanceTradeHistoryAdapter`) e consumida hoje por `ReconcileTradesUseCase`. Porém:

- o port é **standalone** (fora do `ExchangeAdapterDescriptor`, com `getExchangeName()` próprio);
- só é alcançável pelo fluxo de reconciliação, não por um caminho de use case genérico de "consultar histórico".

Em T28 o histórico passa a participar do descriptor (`hasTradeHistory()/tradeHistory()`). Esta task liga o use case agnóstico de histórico a essa capacidade, reusando `BinanceTradeHistoryAdapter`.

---

## Solução proposta

1. **Use case agnóstico** (`QueryTradeHistoryUseCase`) consome o histórico via `ExchangeAdapterDescriptor.tradeHistory()`, sem conhecer Binance.
2. **Reuso**: `adapter-binance` liga o accessor `tradeHistory()` ao `BinanceTradeHistoryAdapter` já existente — sem nova rota REST.
3. **Compatibilidade com reconciliação**: `ReconcileTradesUseCase` continua funcionando; avaliar se ele passa a resolver o histórico via descriptor também (consistência) ou se mantém o acesso atual. Não regredir o relatório de reconciliação.
4. **Parâmetros de consulta**: `symbol`, janela `[from, to]`. Validar `from <= to` e limites de janela máxima (paginação/limites da exchange) na borda.
5. **Contrato de saída**: reutilizar `TradeExecutionDto` (já é DTO de core, usado na reconciliação) — não criar duplicata, salvo se o caso de uso pedir um shape diferente.

---

## Arquivos a criar/modificar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/inbound/exchange/QueryTradeHistoryPort.java` | Assinatura final (symbol, from, to) |
| `core/.../application/usecase/exchange/QueryTradeHistoryUseCase.java` | Lógica agnóstica via descriptor |
| `adapter-binance` | Ligar `tradeHistory()` ao `BinanceTradeHistoryAdapter` existente |
| `spring-application/.../controller` (opcional, se houver endpoint) | Endpoint de histórico seguindo o padrão de retorno (T27) |
| Testes (`core`, `adapter-binance`) | Histórico agnóstico + validação de janela + não-regressão da reconciliação |

---

## Critérios de aceitação

1. Consulta de histórico retorna fills reais via `adapter-binance`, reusando `BinanceTradeHistoryAdapter` (sem rota REST duplicada).
2. O use case resolve o histórico via `ExchangeAdapterDescriptor`, agnóstico ao adapter.
3. `ReconcileTradesUseCase` e o relatório de reconciliação seguem funcionando sem regressão.
4. Validação de intervalo (`from <= to`, janela máxima) aplicada na borda.
5. Adapters sem suporte continuam lançando `UnsupportedOperationException`, tratado pelo use case.
6. Se houver endpoint, ele segue o padrão de retorno de controllers (T27).

---

## Impacto documental

- Atualizar `/.ai` (Ports/Use Cases Reference) e flow de reconciliação se o acesso ao histórico mudar.
- Se houver endpoint, documentar contrato de API.
