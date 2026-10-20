# T53 — Soak Test em Testnet

**Complexidade:** Média
**Responsável:** Claude (spec, procedimento, análise) + mantenedor (execução no ambiente)
**Dependências:** T2 (perfil testnet), T18 (relatório de reconciliação), T21/T51 (alertas), T25/T43/T44/T45/T48
(proteções), T41 (estratégia remota).
**Status:** Rascunho — **decisões de negócio pendentes** (ver seção própria).

---

## Descrição

Antes de qualquer preparação para produção (perfil `prod`, segredos), o mantenedor quer estabilidade
comprovada em testnet por um período longo. Hoje o `GO_LIVE_CHECKLIST` (item 10) e o
`TESTING_ROADMAP` (§4) só dizem "rodar soak test"; não há procedimento, duração nem critério de
aprovação. Esta task define **como rodar, o que observar e quando uma rodada passa**, e mantém o
registro das execuções. Código só entra se uma rodada revelar lacuna — cada achado vira task própria.

## O que já existe (sem código novo)

| Necessidade | Onde |
|---|---|
| Conectar na testnet | perfil `testnet` (`application-testnet.yml`), credenciais `binance.api-key/api-secret` (T2) |
| Estratégias que exercitam cada ramo | estratégias de cenário com teto de ciclos, `CTRADE_SCENARIO_STRATEGIES_ENABLED=true` (T35) |
| Estratégia fora do processo | `strategy-service-reference` + catálogo `ctrade.strategy.remote.catalog` (T41) |
| Ciclo de vida do runner | `POST /api/admin/runners/{id}/start|resume|terminate`, `PATCH` de limites (T49) |
| Conferência local × exchange | `GET /api/reconciliation?symbol=&from=&to=` → `binanceOnly`, `localOnly` (T18) |
| Estado operacional | `/actuator/health` (`safeMode`), métricas da T51, Grafana + Loki + alertas Discord (T9/T17/T21) |
| Proteções | drawdown 24h (T43), proteção do lote (T44), Safe Mode manual/automático (T45/T25), PANIC_SELL LIMIT (T48) |
| Dados descartáveis | dado de testnet não é remediado: limpar e recomeçar (`scripts/reset_test_data.sql`) |

## Proposta de fases

Cada fase só começa com a anterior aprovada. Uma rodada reprovada gera achado → task → nova rodada.

1. **Smoke de ramos (curta).** Estratégias de cenário com `maxCycles`: BUY que descansa → cancela,
   fill → SELL que descansa → cancela, round-trip feliz, recusa por capital. Prova que cada ramo
   funciona contra a Binance real.
2. **Soak com estratégia local.** SMA local num símbolo, janela longa e contínua, sem intervenção.
3. **Soak com estratégia remota.** Mesma janela, com a estratégia no `strategy-service-reference`.
4. **Falhas controladas** (sobre o soak da fase 3):
   - restart da aplicação com ordem em voo (boot recovery);
   - queda de rede/WebSocket por alguns minutos (reconexão, watchdog);
   - estratégia remota fora do ar (HOLD implícito, proteção do lote);
   - drill de Safe Mode: `HALT` → `CANCEL_ALL` → `PANIC_SELL` (MARKET e LIMIT) → retomada;
   - kill switch e retomada do runner.

## Critérios de aprovação de uma rodada (proposta)

1. **Reconciliação** da janela: `binanceOnly = 0` e `localOnly = 0`.
2. **DLQ:** nenhuma entrada não resolvida ao fim da janela, ou cada uma explicada e com task.
3. **Capital:** com o runner sem posição e sem ordem em voo, `reserved = 0`; nenhuma reserva mais
   velha que o TTL (`capital_reservation_oldest_age_seconds`).
4. **Transações:** nenhuma `PENDING`/`SUBMITTED` presa além do limiar do watchdog.
5. **Safe Mode automático / halt de runner:** nenhum disparo sem causa identificada.
6. **Logs:** nenhum `ERROR` sem explicação no Loki.
7. **Estabilidade do processo:** memória e threads da JVM sem crescimento contínuo; latência de
   avaliação (`processing time`) e de conciliação estáveis ao longo da janela.
8. **Saldo:** saldo local coerente com o da exchange (checagem de saldo do boot sem `FAIL_DEFICIT` no
   restart do fim da rodada).

## Decisões pendentes

1. **Duração** da janela do soak (fases 2 e 3) e quantas rodadas limpas seguidas aprovam a fase.
2. **Onde roda:** máquina local (precisa ficar ligada a janela inteira) ou servidor/VPS.
3. **Símbolo(s) e capital** do portfolio na testnet (o saldo da testnet é limitado e a Binance zera a
   conta periodicamente).
4. **Estratégia do soak:** SMA com que parâmetros, ou outra estratégia de referência.
5. **Falhas controladas:** quais entram, e em que momento da rodada.
6. **Critérios:** confirmar, retirar ou acrescentar itens da lista acima.

## Riscos e limitações conhecidas

- **A testnet não é o mercado real:** livro raso, preços que pulam e indisponibilidades da própria
  Binance. Fill estranho ou queda da testnet não é bug do ctrade, mas precisa ficar registrado.
- **Reset periódico da testnet** apaga saldos e ordens; rodada que atravessar um reset é descartada.
- **Ferramental de coleta:** hoje a verificação dos critérios é manual (endpoints, Grafana, SQL). Se
  ficar trabalhosa, um relatório de rodada (endpoint ou script) pode virar task.

## Registro de execuções

| # | Data | Fase | Duração | Estratégia / símbolo | Resultado | Achados → tasks |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

## Fontes

- `docs/GO_LIVE_CHECKLIST.md` (item 10), `docs/TESTING_ROADMAP.md` (§4 e §5)
- `docs/OPERATIONS_RUNBOOK.md` (Safe Mode, gatilhos automáticos, estratégia remota, estado operacional)
- `.ai/tasks/T2-testnet-config.md`, `.ai/tasks/T35-testnet-scenario-strategies.md`,
  `.ai/tasks/T18-trade-reconciliation-report.md`
