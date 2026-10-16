# T49 — Ciclo de Vida do Runner por API (start, resume, terminate)

**Complexidade:** Média/Alta
**Responsável:** Claude
**Dependências:** nenhuma. **Crítica para operar:** sem ela, um runner parado não volta.
**Status:** Pendente

---

## Descrição

Achado da T39 (IG §13.1, §13.3, §13.4, §13.6, §12.5, §7.4.4):

- `StrategyRunner.resume()`, `startTerminating()` e `archive()` existem e **não têm chamador**.
- Runner criado por `POST /api/portfolios/{id}/runners` fica `CREATED` e só vira `ACTIVE` no próximo
  boot (`RunnerBootRecoveryUseCase`, passo 6).
- Runner parado pelo kill switch (T8) ou pelo boot (`ACTIVE → HALTED`) **nunca volta** a `ACTIVE` sem
  editar o banco: no boot ele só conclui a reconciliação e continua `HALTED`.
- Não há como encerrar um runner com posição aberta, nem alterar limites mutáveis em runtime.

## Solução proposta

1. Use cases no core (um por transição, regras no domínio do `StrategyRunner`):
   - **start** (`CREATED → INITIALIZING → ACTIVE`) reaproveitando a reconciliação do boot para aquele
     runner (mesmo caminho do `RunnerBootRecoveryUseCase`, sem duplicar a lógica);
   - **resume** (`HALTED → ACTIVE`) exigindo DLQ do runner/portfolio resolvida e reconciliação limpa;
   - **terminate** (`ACTIVE/HALTED → TERMINATING`): bloqueia BUY, permite SELL de fechamento; worker
     move para `ARCHIVED` quando não houver posição, ordem em voo nem reserva (IG §13.4);
   - **update limits** (`maxAllocationPercent`, `maxOpenPositions`, `maxPendingOrders`) — campos
     mutáveis de IG §13.2.2.
2. Endpoints admin (API key da T8): `POST /api/admin/runners/{id}/start|resume|terminate`,
   `PATCH /api/admin/runners/{id}`.
3. Health check de ativação (IG §13.3): exchange alcançável e símbolo com filtros carregados.
4. Comandos por posição/ordem (IG §12.5): `FORCE_CANCEL` de uma ordem e `FORCE_CLOSE_POSITION`
   reaproveitando o pipeline de cancel (`buildCancelCommand`) e a liquidação MARKET da T45.

## Decisões pendentes

- Encerramento com posição aberta: liquidar a mercado, aguardar a estratégia vender ou "detach"
  (arquivar e deixar a posição para resolução manual — IG §13.4).

## Critérios de aceitação

1. Runner criado pela API pode ser iniciado sem restart.
2. Runner halted pelo kill switch volta a operar por API, com validação.
3. Encerramento leva a `ARCHIVED` só depois de zerar posição, ordens e reserva.
4. Limites mutáveis alterados em runtime valem na próxima reserva.
