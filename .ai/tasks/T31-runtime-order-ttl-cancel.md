# T31 — Limpeza de Reserva Órfã em Runtime + Fundação de Cancelamento de Ordem

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** Nenhuma (estabelece o caminho outbound de cancelamento reusado por T32)  
**Status:** Pendente

---

## Descrição

Esta task resolve **um único caso de capital genuinamente preso** — a reserva órfã — e entrega o **mecanismo de cancelamento** que a T32 vai reusar. Ela é deliberadamente **neutra em relação à estratégia**: nada aqui cancela uma ordem que a estratégia mandou abrir.

> **Escopo corrigido.** Uma versão anterior desta spec previa um scheduler global que cancelava ordens limite abertas após um TTL externo. Isso estava errado: anularia a intenção de estratégias de horizonte longo (compra limite esperando um dip por dias, take-profit mantido por semanas) e partia de uma premissa falsa sobre capital "preso". Ver "Premissa corrigida" abaixo. A decisão de cancelar uma ordem **viva** passa a ser responsabilidade exclusiva da estratégia (T32).

---

## Premissa corrigida: "capital preso" vs "capital comprometido"

Há dois casos que parecem o mesmo e não são:

| Caso | Situação | Capital | É problema? |
|---|---|---|---|
| **A — Reserva órfã** | `PENDING` que reservou capital mas **nunca chegou à exchange** (sem `exchangeOrderId`) — envio falhou ou processo caiu no meio | Comprometido com **nada** | **Sim.** Lixo. Nenhuma estratégia quer isso. |
| **B — Ordem viva** | Ordem limite aberta na exchange (`SUBMITTED`/`PARTIAL`), legitimamente esperando o preço | **Corretamente comprometido** com a ordem | **Não.** É a estratégia operando. |

Para o **Caso B**, o capital reservado **não está preso — está comprometido**. Se a estratégia quer manter uma compra limite aberta por semanas, esse capital *precisa* ficar reservado por semanas: não dá para ter a ordem aberta **e** usar o mesmo capital em outra oportunidade (seria gastar duas vezes). Logo, "capital comprometido por muito tempo" é consequência legítima da estratégia, não um vazamento.

**Esta task só atua no Caso A.** O Caso B só pode ser encerrado por decisão de quem tem a tese — a estratégia (T32) — ou, se um dia existir um TTL de execução, por um parâmetro **opt-in por runner, default desligado/infinito** (fora do escopo aqui).

---

## Contexto técnico

### O que já existe

| Artefato | Situação |
|---|---|
| `PortfolioReservationTtlUseCase` (boot, phase2) | Já expira reserva órfã (`PENDING` sem `exchangeOrderId`) — mas **só no boot**. Em runtime, uma reserva órfã criada após o boot fica até o próximo restart. |
| `RecoverStaleTransactionsUseCase` (watchdog runtime, T1) | Cobre `SUBMITTED`/`PARTIAL` reconciliando com a exchange. **Não** cobre `PENDING` órfão. |
| `ExchangeOrderExecutionPort.cancelOrder(SendCancelOrderRequest)` | Capacidade de cancelamento já existe no transporte. `CancelOrderProcessor` (Binance/Coinbase) monta `order.cancel`. Hoje só é acionada por cancelamento **manual** (`OrderManagementService.unsubscribe`). |
| `OrderDispatchPort.dispatch(...)` | Porta outbound de **envio** (submit). Não tem verbo de cancelamento. |

### A lacuna que esta task fecha

1. **Reserva órfã em runtime:** o caso A só é limpo no boot. Falta um tick de runtime.
2. **Verbo de cancelamento no domínio:** o transporte sabe cancelar, mas não há porta de saída agnóstica no core para a T32 (e futuras políticas) dispararem cancelamento sem conhecer a exchange.

---

## Solução proposta

### Parte 1 — Faxina de reserva órfã em runtime

Levar a lógica do `PortfolioReservationTtlUseCase` (hoje só boot) para runtime, **sem mudar a semântica**: expira apenas `PENDING` **sem `exchangeOrderId`** mais velhos que o cutoff.

- Criar `RuntimeOrphanReservationWatchdog` no Spring, espelhando `RunnerTransactionRecoveryWatchdog` (`@Scheduled`, `fixedDelayString`).
- Reusar a expiração sintética existente (`buildSyntheticExpired`) — idealmente **extrair para um helper compartilhado** entre o TTL de boot e o de runtime, evitando duplicação.
- A expiração passa pelo `ConciliationOrderUpdateExecutor` idempotente, que libera a reserva via `MarginReleaseBuilder` pelo caminho canônico.
- **Não toca** em ordens com `exchangeOrderId` (Caso B). Essas seguem sob responsabilidade do recovery watchdog (limbo técnico) e da estratégia (decisão de negócio).

> Por que isso é seguro e universal: uma reserva sem ordem na exchange não representa nenhuma intenção de mercado viva — é capital comprometido com algo que não existe. Expirá-la nunca contraria uma estratégia.

### Parte 2 — Verbo de cancelamento no `OrderDispatchPort` (fundação da T32)

Adicionar a capacidade de cancelamento como contrato de domínio, espelhando o estilo fire-and-forget de `dispatch`:

```java
public interface OrderDispatchPort {
    void dispatch(OrderDispatchCommand command);
    void cancel(OrderCancelCommand command);   // novo
}
```

- `OrderCancelCommand`: record no core (`dto/runner/`) com `clientOrderId`, `runnerId`, `symbol`, `exchangeId`. Agnóstico — nenhum DTO de exchange entra no core.
- O adapter Spring (`OrderDispatchAdapter`) implementa `cancel(...)` traduzindo para `SendCancelOrderRequest` (mesmo padrão de `dispatch` → `OrderDispatchCommand`).
- **Nesta task não há nenhum gatilho automático ligado a esse verbo para ordens vivas.** Ele é entregue como mecanismo; o primeiro consumidor real é a T32. (A faxina da Parte 1 não usa `cancel` — ela expira localmente reserva sem ordem na exchange.)

---

## Configuração (application.yml)

```yaml
runner:
  orphan-reservation:
    enabled: true
    interval-ms: 60000        # frequência do watchdog de runtime
    ttl-ms: 900000            # idade máxima de uma reserva PENDING sem ordem na exchange
    max-per-run: 50
```

> Observação: este TTL governa **apenas reserva órfã** (Caso A). Não existe configuração global que cancele ordem viva — isso é intencional.

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/outbound/exchange/OrderDispatchPort.java` | Adicionar `cancel(OrderCancelCommand)` |
| `core/.../dto/runner/OrderCancelCommand.java` | Novo — comando de cancelamento agnóstico |
| `core/.../usecase/.../OrphanReservationCleanup*.java` | Extrair/compartilhar a expiração sintética entre boot TTL e runtime |
| `core/.../usecase/boot/phase2/PortfolioReservationTtlUseCase.java` | Reusar o helper compartilhado (sem mudar comportamento de boot) |
| `spring-application/.../infrastructure/exchange/OrderDispatchAdapter.java` | Implementar `cancel(...)` → `SendCancelOrderRequest` |
| `spring-application/.../bootstrap/RuntimeOrphanReservationWatchdog.java` | Novo — scheduler de runtime |
| `spring-application/.../bootstrap/RuntimeOrphanReservationProperties.java` | Novo — propriedades |
| `spring-application/src/main/resources/application.yml` | Configuração da faxina de reserva órfã |
| `spring-application/.../config/core/RunnerConfig.java` | Wiring |

---

## Fora de escopo (explicitamente)

- **Cancelar ordem viva por idade/TTL global.** Removido. Anula intenção de estratégia. Decisão de cancelar ordem viva é da T32.
- **Cancel-replace / reprice automático.** Reintroduz ordem e pode criar loop; só faria sentido como política opt-in por runner, em task futura.
- **Qualquer leitura de estado da exchange.** A faxina é puramente local (reserva sem `exchangeOrderId`); não consulta a exchange (isso é do recovery watchdog).

---

## Riscos / pontos de atenção

- **Não confundir com o recovery watchdog:** este watchdog age sobre `PENDING` órfão (local); o recovery age sobre `SUBMITTED`/`PARTIAL` (consulta exchange). Os dois não devem competir pela mesma transação.
- **Boot vs runtime:** garantir que a faxina de boot e a de runtime compartilhem a mesma lógica de expiração, para não divergirem.
- **Liberação de capital:** deve fluir exclusivamente pelo `ConciliationOrderUpdateExecutor` idempotente, nunca por liberação direta.
- **Verbo `cancel` sem consumidor:** entregar `OrderDispatchPort.cancel` sem gatilho automático é proposital. Cobrir com teste de unidade do adapter, mas o uso end-to-end é validado na T32.

---

## Critérios de aceitação

1. Uma transação `PENDING` **sem `exchangeOrderId`** mais velha que `ttl-ms` é expirada **em runtime** (não só no boot), liberando o capital reservado via conciliação.
2. Nenhuma ordem com `exchangeOrderId` (viva na exchange) é tocada por esta task.
3. Não existe configuração global que cancele ordem viva por idade.
4. `OrderDispatchPort.cancel(OrderCancelCommand)` existe, é implementado pelo adapter e traduz para `SendCancelOrderRequest` sem vazar tipo de provider para o core.
5. A lógica de expiração de reserva órfã é compartilhada entre boot (`PortfolioReservationTtlUseCase`) e runtime, sem duplicação divergente.
6. `runner.orphan-reservation.enabled: false` desabilita o watchdog de runtime sem afetar o boot TTL nem o recovery watchdog.
7. Boot, recovery e reconciliação existentes continuam sem regressão.
