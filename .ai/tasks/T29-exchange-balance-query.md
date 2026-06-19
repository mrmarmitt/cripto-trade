# T29 — Implementar consulta de saldo na exchange

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T28 (camada agnóstica)  
**Status:** Pendente

---

## Descrição

Implementar a **consulta de saldo** real através da camada agnóstica criada em T28, expondo a capacidade ao use case (e, se decidido, a um endpoint). A primeira implementação concreta é no `adapter-binance`, reusando a infraestrutura já existente; os demais adapters permanecem como stub (`UnsupportedOperationException`) até serem necessários.

---

## Contexto técnico

A capacidade de conta já existe internamente: `ExchangeAccountQueryPort.queryAccountSnapshot()` retorna `AccountDataDto` com `Map<asset, balance>` e `Map<asset, lockedBalance>`, hoje consumida apenas pelo boot (`PortfolioBootSanityUseCase`). Esta task expõe essa leitura ao caminho de use case agnóstico do T28 (`QueryExchangeBalancePort`), sem acoplar o consumidor ao Binance.

`adapter-binance` já implementa `ExchangeAccountQueryPort` em `BinanceMarketStreamAdapter` — reutilizar essa chamada como base, evitando uma segunda rota REST para a mesma informação.

---

## Decisão de design em aberto — por asset vs lista completa

O autor levantou: consultar saldo **por asset** ou retornar **lista de todos os saldos**?

| Opção | Prós | Contras |
|---|---|---|
| A. Lista completa (`Map<asset, BalanceDto>` ou `List<BalanceDto>`) | Uma só chamada à exchange (o snapshot já vem completo); evita N chamadas; alinhado ao `AccountDataDto` existente | Consumidor que só quer 1 asset recebe tudo |
| B. Por asset (`BalanceDto balanceOf(String asset)`) | Resposta enxuta para o caso "quanto tenho de USDT?" | Exchange normalmente não filtra por asset no snapshot → ainda busca tudo e filtra local; tende a virar N chamadas |

**Recomendação:** **Opção A com filtro opcional.** O port retorna a lista/mapa completo (uma chamada ao snapshot), e o filtro por asset é um parâmetro opcional aplicado na borda (use case ou query param). Isso entrega os dois comportamentos com um único custo de I/O e reaproveita o `AccountDataDto`. Assinatura sugerida:

```java
// QueryExchangeBalancePort (inbound, definido em T28)
ExchangeBalanceSnapshot queryBalances(String exchangeName);            // todos
// filtro por asset aplicado pelo consumidor / query param ?asset=USDT
```

> Confirmar esta decisão antes de implementar; ela define a assinatura do port do T28.

### DTO de saldo

Evitar vazar `AccountDataDto` (DTO de WebSocket) como contrato de saída do use case. Definir um DTO próprio de saldo, ex.: `BalanceDto(asset, free, locked, total)`.

---

## Arquivos a criar/modificar

| Arquivo | Mudança |
|---|---|
| `core/.../dto/exchange/BalanceDto.java` (+ snapshot) (novo) | Contrato de saída de saldo |
| `core/.../ports/inbound/exchange/QueryExchangeBalancePort.java` | Assinatura final conforme decisão A/B |
| `core/.../application/usecase/exchange/QueryExchangeBalanceUseCase.java` | Lógica agnóstica usando o descriptor |
| `adapter-binance` | Ligar saldo ao caminho novo, reusando `queryAccountSnapshot()` |
| `spring-application/.../controller` (opcional, se houver endpoint) | Endpoint de saldo seguindo o padrão de retorno (ver T27) |
| Testes (`core`, `adapter-binance`) | Saldo agnóstico + mapeamento de `AccountDataDto`→`BalanceDto` |

---

## Critérios de aceitação

1. Consulta de saldo retorna dados reais via `adapter-binance`, reusando o snapshot de conta existente (sem rota REST duplicada).
2. Contrato de saída é um DTO de saldo dedicado — `AccountDataDto` não vaza para fora do core.
3. Decisão "por asset vs lista" aplicada conforme alinhado (recomendado: lista completa + filtro opcional).
4. Adapters sem suporte continuam lançando `UnsupportedOperationException`, tratado pelo use case.
5. Se houver endpoint, ele segue o padrão de retorno de controllers (T27).
6. Boot/recovery existentes intactos.

---

## Impacto documental

- Atualizar `/.ai` (Ports/Use Cases Reference) e, se houver endpoint, doc de API.
- Avaliar entrada no glossário/flows se a consulta de saldo virar fluxo operacional documentado.
