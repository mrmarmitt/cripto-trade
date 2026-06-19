# T28 — Camada agnóstica de adapter para consulta de saldo e histórico

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** Nenhuma (fundação para T29 e T30)  
**Status:** Pendente

---

## Descrição

Queremos expor ao caso de uso (e futuramente à API/controllers) duas capacidades de leitura na exchange: **consulta de saldo** e **consulta de histórico de trades**. O use case deve ser **agnóstico ao adapter** — não pode conhecer Binance, Coinbase ou Mock. Cada adapter deve fornecer sua própria implementação; enquanto a implementação real não existe, o adapter expõe a capacidade como ausente e/ou lança `UnsupportedOperationException`.

Esta task é a **fundação**: define os ports, integra as capacidades à camada de seleção agnóstica (`ExchangeAdapterDescriptor`) e cria o esqueleto do use case. **Não implementa a lógica real** de nenhum adapter — todos retornam capability `false` e/ou lançam `UnsupportedOperationException`. A implementação real fica em T29 (saldo) e T30 (histórico).

> Convenção do projeto: "não implementado" é sinalizado com `UnsupportedOperationException`, que os use cases já tratam de forma graciosa (ex.: `PortfolioBootSanityUseCase`, `RunnerBootRecoveryUseCase`, `RecoverTransactionStatusUseCase`). **Não** usar `NotImplementedException` (não existe no Java padrão).

---

## Contexto técnico — o que já existe

| Artefato | Situação |
|---|---|
| `ExchangeAdapterDescriptor` (`core/.../ports/outbound/exchange`) | Camada agnóstica central: expõe capacidades por adapter via `hasXxx()` + accessor `xxx()`. Já contém `hasAccountQuery()/accountQuery()`. |
| `ExchangeAccountQueryPort.queryAccountSnapshot()` → `AccountDataDto` | Saldo já existe como capacidade **interna** do boot (sanity check). `AccountDataDto` traz `Map<asset, balance>` + `Map<asset, locked>`. Só `adapter-binance` implementa (`BinanceMarketStreamAdapter`). Não há use case/endpoint expondo isso ao usuário. |
| `TradeHistoryQueryPort.fetchTrades(symbol, from, to)` → `List<TradeExecutionDto>` | Histórico existe como port **standalone** (fora do descriptor, com `getExchangeName()` próprio). Só `adapter-binance` implementa (`BinanceTradeHistoryAdapter`). Consumido por `ReconcileTradesUseCase`. |
| Adapters | `adapter-binance`, `adapter-coinbase`, `adapter-mock`. Coinbase e Mock não implementam nenhuma das duas capacidades. |

**Lacuna:** não há caminho agnóstico de use case → capacidade de saldo/histórico. O histórico nem participa do descriptor.

---

## Solução proposta

### 1. Unificar as duas capacidades no `ExchangeAdapterDescriptor`

O descriptor é a fronteira agnóstica. Acrescentar a capacidade de histórico ao lado da de conta já existente:

```java
boolean hasAccountQuery();          // já existe
ExchangeAccountQueryPort accountQuery();   // já existe

boolean hasTradeHistory();          // novo
TradeHistoryQueryPort tradeHistory();      // novo
```

Assim, saldo e histórico são resolvidos pelo mesmo mecanismo de seleção por exchange, sem o use case conhecer o adapter.

### 2. Ports inbound + use case esqueleto (core)

Criar os ports inbound (caso de uso) em `core/.../ports/inbound/exchange/`:

- `QueryExchangeBalancePort` — assinatura definida em T29 (ver decisão "por asset vs lista").
- `QueryTradeHistoryPort` — leitura de histórico por símbolo/intervalo.

E os use cases correspondentes em `core/.../application/usecase/exchange/`, que:
1. resolvem o `ExchangeAdapterDescriptor` da exchange pedida (via repositório de adapters);
2. checam `hasTradeHistory()` / `hasAccountQuery()`;
3. se ausente, retornam um resultado tipado "não suportado" (ou propagam `UnsupportedOperationException` traduzida) — **sem** vazar tipo de adapter.

### 3. Stubs em todos os adapters

Nesta task, **todos os adapters** ficam no estado "não implementado" para o caminho novo:

- `adapter-coinbase` e `adapter-mock`: `hasTradeHistory()` (e, se aplicável, `hasAccountQuery()`) retornam `false`; os accessors lançam `UnsupportedOperationException`.
- `adapter-binance`: pode reusar as implementações já existentes (`BinanceTradeHistoryAdapter`, account query) **ou** também ficar como stub até T29/T30 ligarem o caminho de use case. Decidir na implementação, mantendo o boot/reconciliação atuais intactos.

### 4. Camada Spring

Onde o `ExchangeAdapterDescriptor` é montado (config do `spring-application`), ligar o novo accessor `tradeHistory()` ao bean correspondente de cada adapter, espelhando como `accountQuery()` já é ligado.

---

## Arquivos a criar/modificar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/outbound/exchange/ExchangeAdapterDescriptor.java` | Adicionar `hasTradeHistory()` / `tradeHistory()` |
| `core/.../ports/inbound/exchange/QueryExchangeBalancePort.java` (novo) | Port inbound de saldo |
| `core/.../ports/inbound/exchange/QueryTradeHistoryPort.java` (novo) | Port inbound de histórico |
| `core/.../application/usecase/exchange/*.java` (novo) | Use cases agnósticos, com tratamento de capacidade ausente |
| `adapter-coinbase`, `adapter-mock` | Stubs: capability `false` + `UnsupportedOperationException` |
| `adapter-binance` | Reuso das impls existentes ou stub provisório |
| `spring-application` (config do descriptor) | Wiring do accessor `tradeHistory()` por adapter |
| Testes (`core/src/test`) | Cobrir dispatch agnóstico e capacidade ausente |

---

## Critérios de aceitação

1. O use case de saldo e o de histórico não referenciam nenhum tipo de adapter concreto — só `ExchangeAdapterDescriptor` e ports.
2. `ExchangeAdapterDescriptor` resolve histórico via `hasTradeHistory()/tradeHistory()`, no mesmo padrão de `accountQuery()`.
3. Adapters sem implementação expõem capability `false` e lançam `UnsupportedOperationException` nos accessors; o use case trata isso sem quebrar.
4. Boot, recovery e reconciliação existentes continuam funcionando sem regressão.
5. Testes cobrem: dispatch para a exchange correta e caminho "capacidade ausente".

---

## Impacto documental

- Atualizar `/.ai` (Ports Reference / descriptor) e flows aplicáveis quando o caminho de use case for ligado.
- Sem impacto em `docs/` de negócio nesta fundação (sem comportamento exposto ainda).
