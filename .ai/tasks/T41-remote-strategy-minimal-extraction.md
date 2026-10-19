# T41 — Extração Mínima da Estratégia: Port, Proxy Remoto e Catálogo Estático

**Complexidade:** Alta
**Responsável:** Claude
**Dependências:** T40 (contrato revisado). T42 é pré-requisito para **ativar** estratégia remota em
testnet/produção, não para o código desta task.
**Status:** Concluído (2026-10-19) — código entregue; **ativação em ambiente real aguarda a T44**.

---

## Descrição

Implementar a camada 1 do desacoplamento descrita em `docs/strategy-service/CONTRACT.md` ("Onde isto
se encaixa"): o ctrade passa a poder avaliar uma estratégia que roda fora do processo, **sem mudar o
pipeline de BUY/SELL/CANCEL**. O proxy remoto é só mais uma implementação de `TradingStrategy`.

Fora do escopo (fica para depois de produção estável): troca de versão em runtime, shadow, canário,
máquina de promoção `REGISTERED → SHADOW → ACTIVE → DRAINING → RETIRED`, harness de backtest.

---

## Solução proposta

### 1. Core

- `StrategyEvaluationPort` (outbound): `StrategyOutputDto evaluate(UUID strategyRef, StrategyInputDto, StrategyContextDto)`,
  agnóstico de transporte. Falha de transporte é sinalizada por exceção própria do core
  (ex.: `StrategyUnavailableException`), nunca por tipo HTTP.
- `RemoteTradingStrategy implements TradingStrategy`: identidade (`strategyId`, nome, versão) vinda
  do catálogo; `executeStrategy` delega ao port.
- `StrategySignalEvaluator`: tratar `StrategyUnavailableException` e **qualquer** `RuntimeException`
  da estratégia como HOLD (hoje só `IllegalArgumentException` é normalizado; o resto cai no catch
  genérico do runner sem métrica de sinal).
- Novo outcome em `SignalDecision`: `STRATEGY_UNAVAILABLE` (timeout, 5xx, não-ready, decisão velha),
  separado de `HOLD`, para o monitoramento distinguir "estratégia decidiu esperar" de "estratégia
  não respondeu".

### 2. spring-application

- Cliente REST do contrato (OkHttp já é dependência), com:
  - **timeout rígido** configurável (`ctrade.strategy.remote.timeout`, default 250ms);
  - **sem retry** (T40);
  - `tickId`/`sequence` por runner;
  - descarte por `max-decision-age` (T40);
  - token de serviço em header; endpoint só em rede interna.
- Validação da resposta contra o contrato (`oneOf` por decisão, faixas de `quantity`/`confidence`/
  `limitPrice`); decisão inválida = HOLD + log WARN + métrica.
- `strategy_catalog` estático (config YAML ou tabela Flyway): `strategyRef → baseUrl, name, version, enabled`.
  No boot, registra um `RemoteTradingStrategy` por entrada via `StrategyRepositoryPort.registerStrategy`
  e confere `/v1/meta` (versão de contrato e identidade); divergência = entrada não registrada + ERROR.
- Resolução **somente por `strategyId`** para estratégias remotas (o fallback por nome do
  `StrategySignalEvaluator` fica ambíguo com duas versões de mesmo nome).
- Readiness: `GET /v1/health/{strategyRef}` com `ready=false` ⇒ HOLD implícito (`STRATEGY_UNAVAILABLE`).
- Métricas: histograma `strategy.remote.latency{strategyRef}`, contador
  `strategy.remote.failures{strategyRef,reason}`.

### 3. App de referência e teste de paridade

- App Java mínima (fora do classpath do ctrade — módulo/repo próprio a decidir na implementação) que
  serve o SMA pelo contrato, com warm-up por `(strategyRef, runnerId)`.
- **Teste de paridade**: o mesmo replay de ticks no SMA in-process e no SMA remoto produz sequência
  de decisões idêntica. É o portão da migração.
- E2E reutilizando o harness da T38: runner apontando para estratégia remota (MockWebServer ou app de
  referência), cobrindo decisão normal, timeout, 5xx, `ready=false` e decisão malformada.

### 4. Convivência

- Estratégias in-process (SMA e cenários da T35/T38) continuam registradas; nada é removido.
- **Warm-up in-process por runner (T40):** o `SimpleMovingAverageStrategy` guarda um único
  `priceHistory` por instância; dois runners com a mesma estratégia misturam o histórico. Chavear pelo
  mesmo `(strategyId, runnerId)` do contrato — senão a paridade in-process × remoto só vale para um runner.
- Remover a dependência `spring-application → strategy` **não** faz parte desta task — só após a
  paridade comprovada em soak de testnet.

---

## Riscos / pontos de atenção

- **Latência no caminho do tick:** sem a T42, a chamada HTTP roda no `messageProcessingExecutor`
  compartilhado com os fills; não habilitar estratégia remota em ambiente real antes da T42.
- **Posições sem saída com a estratégia fora:** HOLD implícito impede venda; a mitigação é a T44.
- **Fronteira:** nenhum tipo HTTP/JSON no `core`; DTOs de rede ficam no spring-application.

---

## Critérios de aceitação

1. Runner configurado com `strategyId` de catálogo remoto opera BUY/SELL/CANCEL pelo pipeline atual,
   sem alteração em handlers de sinal.
2. Timeout, 5xx, `ready=false`, decisão velha e decisão malformada resultam em HOLD implícito com
   outcome `STRATEGY_UNAVAILABLE`, sem afetar os demais runners do tick.
3. Exceções arbitrárias de estratégia in-process passam a gerar HOLD com métrica.
4. Teste de paridade SMA in-process × remoto verde.
5. `:core:test` e `:spring-application:test` verdes; flow map `runner-signal-processing.md` atualizado.

---

## Fontes

- `docs/strategy-service/CONTRACT.md`, `docs/strategy-service/openapi.yaml`, `.ai/tasks/T40-strategy-contract-revision.md`.
- `core/src/main/java/com/marmitt/core/ports/outbound/strategy/TradingStrategy.java`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/StrategySignalEvaluator.java`.
- `spring-application/src/main/java/com/marmitt/application/spring/repository/InMemoryStrategyRepository.java`.
- `.ai/flows/runner-signal-processing.md`.

---

## Entrega (2026-10-19)

### Decisões registradas (implementação)

- **Módulos:** `adapter-strategy-remote` (formato de rede do contrato, validação da decisão e cliente;
  porta HTTP própria `StrategyServiceHttpPort`, no mesmo padrão do `HttpClientPort` da Binance) e
  `strategy-service-reference` (app mínima com `HttpServer` do JDK, fora do classpath do ctrade, que
  usa o mesmo mapeamento — um contrato, um mapeamento para os dois lados).
- **Sem portão de readiness no caminho do tick:** o warm-up é por runner e só avança quando o runner
  chama `/evaluate`; bloquear a chamada enquanto `ready=false` impediria o aquecimento. A app responde
  HOLD enquanto aquece (o contrato exige decisão válida). A readiness escopada fica para o portão de
  promoção (fora do escopo).
- **Toda falha da estratégia vira `STRATEGY_UNAVAILABLE`:** serviço fora, timeout, 5xx, resposta fora
  do contrato, decisão velha, retorno nulo, sinal inválido e exceção de estratégia in-process — sempre
  HOLD com o desfecho próprio, sem cair no catch genérico do runner.
- **Resolução só por `strategyId`** no avaliador e identidade só por id no `InMemoryStrategyRepository`
  (duas versões podem ter o mesmo nome); `StrategyRepositoryPort.findByName` removido (sem uso).
- **Catálogo estático em YAML** (`ctrade.strategy.remote.catalog`); o core (`RegisterRemoteStrategiesUseCase`)
  confere `/v1/meta` — mesmo id, nome, versão e contrato maior 1 — antes de registrar; o spring tenta
  de novo a cada `registration-retry-interval-ms` (serviço que sobe depois do ctrade).
- **Parâmetros:** `timeout-ms` 250 (spec), `max-decision-age-ms` 2000 (a idade conta desde o
  recebimento do tick e inclui a espera na mailbox, já limitada a 5 s pela T42), token opcional como
  `Authorization: Bearer` (`CTRADE_STRATEGY_REMOTE_TOKEN`) — documentado no contrato.
- **SMA in-process com warm-up por runner** (pendência da T40), mesma chave do contrato.

### Validação

- `:core:test` 201 — `StrategySignalEvaluatorTest`, `RegisterRemoteStrategiesUseCaseTest`.
- `:adapter-strategy-remote:test` 8 — `RemoteStrategyClientTest` (mapeamento, `tickId`/`sequence`
  por runner, decimal como string, 503/timeout/JSON inválido/faixa inválida sem retry, decisão velha,
  `/meta`), `DecisionValidatorTest`.
- `:strategy-service-reference:test` 2 — **paridade**: 80 ticks × 2 runners intercalados, SMA in-process
  e SMA remoto (HTTP real) com a mesma sequência de decisões, incluindo BUYs.
- `:spring-application:test` 201 — `RemoteStrategyE2ETest` (MockWebServer pelo catálogo): decisão remota
  vira BUY preenchida pelo pipeline normal; 503, timeout, decisão velha e decisão fora do contrato viram
  `STRATEGY_UNAVAILABLE` sem ordem e contam em `strategy.remote.failures{reason}`.

### Riscos residuais

- **Não ativar em ambiente real antes da T44:** com a estratégia fora do ar, HOLD implícito impede
  qualquer saída de posição.
- **Rede no caminho do tick:** a chamada roda no `runnerSignalExecutor` (T42); runners com estratégia
  lenta ocupam threads do pool — `runner.signal.threads` e o timeout limitam o efeito.
- **App de referência** atende fora de sequência com HOLD (o SMA não sabe avaliar sem mexer no warm-up).
