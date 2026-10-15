# T45 — Ativação do Safe Mode e Execução dos Níveis CANCEL_ALL / PANIC_SELL

**Complexidade:** Média/Alta
**Responsável:** Claude
**Dependências:** T33 (`OrderDispatchPort.cancel`). É pré-requisito de T25 e T43, que precisam de um
caminho para acionar o Safe Mode.
**Status:** Pendente

---

## Descrição

O Safe Mode é citado como o freio do portfolio, mas hoje só existe parcialmente:

- `SafeModeStatus` define `NORMAL`, `HALT`, `CANCEL_ALL`, `PANIC_SELL` (BLUEPRINT §11.1.B.1).
- `CapitalReservationPolicy` bloqueia novas reservas quando `isActive()` — então qualquer nível só
  impede BUY.
- **Nenhum código chama `Portfolio.setSafeModeStatus`.** Não há endpoint, use case nem gatilho que
  ative o Safe Mode; a única forma é editar a tabela `portfolios` no banco. A T25 afirma que existe
  ativação manual via `PUT /api/admin/portfolios/{id}/safe-mode` — esse endpoint não existe.
- `CANCEL_ALL` e `PANIC_SELL` não têm efeito algum além do bloqueio de BUY.

Na prática, a saída de emergência para cancelar todas as ordens ou liquidar posições não existe,
nem manual. O kill switch (T8) põe runners em `HALTED`, mas não cancela ordens em voo nem fecha
posições.

---

## Solução proposta

1. **Use case no core** `ChangeSafeModeUseCase` (inbound port): muda o nível com motivo obrigatório e
   origem (`MANUAL`/`AUTOMATIC:<reason>`), idempotente, com auditoria persistida. Expõe a porta que
   T25 e T43 vão usar para acionar `HALT` automaticamente.
2. **Endpoints admin** (`/api/admin/portfolios/{id}/safe-mode`, protegidos pelo `admin.api.key` da T8):
   - `GET` — nível atual, motivo, desde quando, progresso da execução.
   - `PUT` — muda nível. Níveis 2 e 3 exigem confirmação explícita (`confirm=true` + motivo),
     conforme BLUEPRINT §11.1.B.1.
3. **Nível 1 — HALT:** bloqueia novos sinais (já ocorre na reserva de BUY). Definir se SELL da
   estratégia continua permitida (recomendação: sim, só reduz exposição).
4. **Nível 2 — CANCEL_ALL:** HALT + cancelamento de todas as ordens `SUBMITTED`/`PARTIAL` do
   portfolio via `OrderDispatchPort.cancel`. Execução assíncrona com progresso
   (`n/total canceladas`); a liberação de capital segue pela conciliação normal (sem atalho).
5. **Nível 3 — PANIC_SELL:** CANCEL_ALL + SELL de todas as posições abertas pelo `SellSignalHandler`
   (lock de posição, normalização, persistência antes do envio). Progresso por posição.
6. **Retomada:** descida explícita de nível (3→2→1→NORMAL), com motivo, como no BLUEPRINT §11.1.C.
7. **Persistência:** nível e progresso sobrevivem a restart; o boot retoma a execução pendente do
   nível ativo.

---

## Riscos / pontos de atenção

- Cancelamento e liquidação em massa geram muitas chamadas à exchange: respeitar rate limit
  (relação com T24, circuit breaker REST).
- PANIC_SELL com ordem SELL já descansando no lote: cancelar antes (CANCEL_ALL faz isso) e só então
  vender; o lock de posição impede duplicação.
- Idempotência: repetir o `PUT` no mesmo nível não reenvia cancelamentos/vendas já feitos.

---

## Critérios de aceitação

1. Safe Mode pode ser ativado e desativado por API com motivo e auditoria; T25 e T43 têm porta para
   ativação automática.
2. `CANCEL_ALL` cancela todas as ordens em voo do portfolio e o capital volta a `available` pela
   conciliação.
3. `PANIC_SELL` fecha todas as posições abertas pelo pipeline normal de SELL, sem SELL duplicada.
4. Progresso consultável; execução retomada após restart.
5. Testes de integração com o MOCK (resting da T38) para os três níveis.
6. `.ai/tasks/T25-safe-mode-automatico.md` e `.ai/flows/portfolio-capital.md` atualizados.

---

## Fontes

- `docs/BLUEPRINT.md` §11.1.B.1 e §11.1.C.
- `core/src/main/java/com/marmitt/core/enums/SafeModeStatus.java`,
  `core/src/main/java/com/marmitt/core/domain/portfolio/Portfolio.java` (`setSafeModeStatus` sem chamador),
  `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/CapitalReservationPolicy.java`.
- `core/src/main/java/com/marmitt/core/application/usecase/runner/HaltRunnerUseCase.java` (kill switch T8).
- `.ai/tasks/T8-kill-switch.md`, `.ai/tasks/T25-safe-mode-automatico.md`, `.ai/tasks/T33-order-cancel-dispatch-foundation.md`.
