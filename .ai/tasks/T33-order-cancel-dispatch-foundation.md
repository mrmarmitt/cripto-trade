# T33 — Fundação de Cancelamento Outbound (`OrderDispatchPort.cancel`)

**Complexidade:** Baixa  
**Responsável:** Claude  
**Dependências:** Nenhuma  
**Status:** Concluído — verbo `OrderDispatchPort.cancel(OrderCancelCommand)` + impl no adapter (via `orderExecution().cancelOrder`, fire-and-forget). Primeiro consumidor: T32.

---

## Descrição

Entrega **só o mecanismo** de cancelamento como contrato de domínio: o verbo outbound `OrderDispatchPort.cancel`. **Nenhuma política decide *quando* cancelar aqui** — isso é da estratégia (**T32**, primeiro consumidor real). É deliberadamente neutra e mínima.

> Extraída da antiga "Parte 2" da T31. A T31 (recovery/zombie) **não** usa este verbo — ela faz query + conciliação. Quem usa o `cancel` é a T32, para puxar uma ordem **viva** antes do fill.

---

## Solução proposta

Adicionar o cancelamento espelhando o fire-and-forget de `dispatch`:

```java
public interface OrderDispatchPort {
    void dispatch(OrderDispatchCommand command);
    void cancel(OrderCancelCommand command);   // novo
}
```

- `OrderCancelCommand`: record no core (`dto/runner/`) com `clientOrderId`, `runnerId`, `symbol`, `exchangeId`. **Agnóstico** — nenhum DTO de exchange entra no core.
- `OrderDispatchAdapter` implementa `cancel(...)` traduzindo para `SendCancelOrderRequest` (mesmo padrão de `dispatch` → `OrderDispatchCommand`). O transporte já sabe cancelar (`CancelOrderProcessor`, `ExchangeOrderExecutionPort.cancelOrder`).
- **Sem gatilho automático.** É só o canal de saída; o `CANCELED` real chega via stream e é conciliado pelo caminho idempotente (mesma garantia do resto do sistema). Nenhuma marcação terminal otimista.

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/outbound/exchange/OrderDispatchPort.java` | Adicionar `cancel(OrderCancelCommand)` |
| `core/.../dto/runner/OrderCancelCommand.java` | Novo — comando de cancelamento agnóstico |
| `spring-application/.../infrastructure/exchange/OrderDispatchAdapter.java` | Implementar `cancel(...)` → `SendCancelOrderRequest` |
| `spring-application/.../config/core/RunnerConfig.java` | Wiring, se necessário |
| Testes | Adapter traduz `OrderCancelCommand` → `SendCancelOrderRequest` sem vazar tipo de provider |

---

## Fora de escopo (explicitamente)

- **Qualquer gatilho que decida cancelar** (idade/TTL/alpha). O primeiro consumidor é a T32.
- **TTL global cancelando ordem viva.** Anularia estratégias de horizonte longo. Se um dia existir, é **opt-in por runner (default off)**, dentro do escopo da T32 — nunca aqui.
- **Limpeza de reserva órfã (zombie).** É a **T31** (query + conciliação, não usa `cancel`).

---

## Riscos / pontos de atenção

- **Fronteira arquitetural:** toda a tradução para a exchange fica no adapter; nenhum detalhe de provider entra no core via `OrderCancelCommand`.
- **Fire-and-forget:** `cancel` não confirma síncrono; o desfecho (`CANCELED`/race com fill) chega pelo stream e é decidido pela conciliação idempotente.
- **Sem consumidor nesta task:** o verbo entra inerte; cobertura real de ponta a ponta é exercida na T32.

---

## Critérios de aceitação

1. `OrderDispatchPort.cancel(OrderCancelCommand)` existe e é implementado pelo adapter.
2. O adapter traduz `OrderCancelCommand` → `SendCancelOrderRequest` sem vazar tipo de provider para o core.
3. Nenhum gatilho automático aciona o verbo nesta task.
4. Boot, recovery e reconciliação seguem sem regressão.
