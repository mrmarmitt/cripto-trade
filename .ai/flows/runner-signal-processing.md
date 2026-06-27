# Runner Signal Processing

## Objetivo

Transformar ticks de mercado em decisoes de estrategia e, quando aplicavel,
persistir uma transacao BUY ou SELL antes de enviar a ordem para a exchange.

Este fluxo fica no core. O Spring apenas fornece fronteiras transacionais e
adaptadores externos.

## Quando Consultar

- Mudancas em processamento de sinais de trade.
- Bugs em ordens que nao sao enviadas ou sao enviadas indevidamente.
- Alteracoes no contexto entregue a estrategias.
- Regras de reserva de capital, exposicao ou lock de posicao antes do dispatch.

## Entradas E Saidas

- Entrada principal: `MarketDataDto`.
- Entrada derivada: runners operacionais por simbolo/exchange.
- Saida principal: transacao `PENDING` persistida e ordem enviada via
  `OrderDispatchPort`.
- Efeitos colaterais: reserva atomica de capital para BUY; lock de posicao para
  SELL.

## Fluxo Principal

1. `ProcessTradeSignalUseCase` recebe um tick de mercado.
2. Busca runners operacionais por `symbol` e `exchange`.
3. `RunnerSignalPolicy` filtra runners que podem avaliar o sinal.
4. `RunnerContextAssembler` monta `StrategyContextDto` com portfolio,
   posicoes, ordens pendentes, limites e PnL.
5. `StrategySignalEvaluator` avalia a estrategia.
6. Sinal `HOLD` encerra o fluxo sem efeito.
7. `OrderNormalizer` alinha quantidade e preco as regras de filtro da exchange
   (`stepSize`/`tickSize`) ANTES de materializar a transacao, via
   `OrderQuantityNormalizerPort`. Exchange sem normalizador (ex.: MOCK) usa os
   valores originais.
8. Sinal `BUY` passa por `BuySignalHandler`.
9. Sinal `SELL` passa por `SellSignalHandler`.
10. A transacao e persistida antes do envio externo da ordem.

## BUY

1. `TradeIntentFactory` cria a intencao da transacao.
2. `BuySignalHandler` salva a transacao como `PENDING`.
3. `CapitalReservationPolicy` tenta reservar capital no `GlobalBalance`.
4. Se a reserva for aceita, a ordem e enviada por `OrderDispatchPort`.
5. A transacao continua `PENDING` ate a exchange confirmar via conciliacao.

Se a reserva for rejeitada, o sinal e descartado sem dispatch.

## SELL

1. `SellSignalHandler` resolve a posicao alvo por `targetLotId` ou por posicao
   aberta do runner/simbolo.
2. A transacao SELL e persistida como `PENDING`.
3. A posicao e travada com `tryLockPositionForSell`.
4. A ordem e enviada por `OrderDispatchPort`.

Se nao houver posicao aberta, o sinal e descartado. Se outra venda travou a
mesma posicao, o fluxo falha com `ConcurrentPositionLockException`.

## Componentes Principais

| Componente | Papel |
| --- | --- |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/ProcessTradeSignalUseCase.java` | Orquestra ticks, runners, contexto, decisao e handlers BUY/SELL. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/OrderNormalizer.java` | Resolve `OrderQuantityNormalizerPort` por exchange e alinha quantidade/preco antes do persist. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/RunnerContextAssembler.java` | Monta `StrategyContextDto`. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/BuySignalHandler.java` | Persiste BUY, reserva capital e faz dispatch. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/SellSignalHandler.java` | Persiste SELL, trava posicao e faz dispatch. |
| `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/CapitalReservationPolicy.java` | Valida Safe Mode, fundos e limite de exposicao por runner. |
| `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java` | Injeta fronteiras transacionais com `TransactionTemplate`. |

## Invariantes

- Nenhuma ordem externa deve ser enviada antes da transacao local existir.
- BUY so pode seguir para dispatch depois de reserva de capital aceita.
- Quantidade e preco persistidos/reservados devem ser identicos aos enviados a
  exchange: a normalizacao acontece antes do persist, e o `OrderFilterValidator`
  no dispatch apenas valida (rejeita desalinhamento), sem reajustar valores.
- SELL so pode seguir para dispatch depois de posicao travada.
- Falha em um runner nao deve impedir a avaliacao dos demais runners do tick.
- `RunnerContextAssembler` exige `GlobalBalance`; sem ele a avaliacao nao deve
  seguir.
- O core nao deve depender de detalhes do adapter da exchange.

## Observabilidade

`ProcessTradeSignalUseCase` emite o counter `signal.evaluated.total{runnerId,decision}` via
`SignalMetricsPort` (T23 G2; impl Micrometer `MicrometerSignalMetricsAdapter` no spring). O
`core` nao depende de Micrometer — a porta espelha o padrao de `BootExecutionObserverPort`.

As tags `decision` sao **outcomes mutuamente exclusivos**, nao a decisao bruta da estrategia:

- `HOLD` — sinal HOLD.
- `BUY` / `SELL` — ordem efetivamente despachada.
- `CANCEL` — `SHOULD_CANCEL` roteado.
- `REJECTED_CAPITAL` — BUY recusado na reserva de capital (engolido em `BuySignalHandler`).
- `REJECTED_LOCK` — SELL perdeu o lock concorrente da posicao.
- `REJECTED_NO_POSITION` — SELL sem posicao aberta para vender.
- `REJECTED_POLICY` — sinal barrado pela guarda de execucao (sobretudo politica
  SINGLE com posicao/ordem ja aberta) antes do pipeline de trade.

Para distinguir despacho de rejeicao sem efeito colateral escondido, `BuySignalHandler` e
`SellSignalHandler` retornam `BuyOutcome`/`SellOutcome`; o registro fica centralizado no
orquestrador. Catalogo de indicadores/alertas em `/.ai/monitoring-spec.md`.

**Rastreamento end-to-end (T26):** ao materializar a transacao, o orquestrador ancora o
`transactionId` no MDC (escopo cobre todo o handler) e, **apenas quando a transacao e
persistida e despachada** (outcome `DISPATCHED`), emite `signal: transaction created
transactionId={} correlationId={} runnerId={} side={}`. Sinais descartados antes do commit
(BUY recusado por capital, SELL sem posicao, falha de lock com rollback) nao emitem o log de
criacao — evita transacao fantasma na query do Loki. O `correlationId` (do tick, herdado do
MDC) cria o "join" tick→transacao; o `transactionId` e o eixo da query
`{app="ctrade"} | json | transactionId="X"`.

## Validacao

- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/ProcessTradeSignalMockIntegrationTest.java`
- `core/src/test/java/com/marmitt/core/domain/runner/StrategyRunnerInvariantsTest.java`
- `core/src/test/java/com/marmitt/core/domain/portfolio/GlobalBalanceInvariantsTest.java`
- Comando recomendado para mudancas neste fluxo:
  `./scripts/gradle-run.ps1 -q :core:test :spring-application:test`

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md`, secoes 12, 13 e 14.
- `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/`.
- `spring-application/src/main/java/com/marmitt/application/spring/config/core/RunnerConfig.java`.
