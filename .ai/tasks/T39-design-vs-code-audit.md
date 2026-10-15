# T39 — Auditoria Design × Código (capital, risco e concorrência)

**Complexidade:** Média (investigação; sem mudança de código)
**Responsável:** Claude
**Dependências:** nenhuma
**Status:** Pendente

---

## Descrição

`docs/IMPLEMENTATION_GUIDE.md` e `docs/BLUEPRINT.md` estão sendo lidos como descrição do sistema
atual, mas parte deles é **intenção não implementada**. A T36 nasceu exatamente disso (safety buffer e
devolução de excedente §7.2.1–7.2.3 especificados e ausentes no código), e a avaliação de prontidão
para operação de 2026-10-15 encontrou outros casos da mesma classe:

| Seção do design | O que descreve | Estado no código (2026-10-15) |
|---|---|---|
| IG §12.1–12.2 (Actor/Mailbox, nota #23 marcada "RESOLVIDA") | Mailbox por runner, capacidade 1, processamento sequencial | Inexistente — zero ocorrências de mailbox em `src/main`; ticks processados no `messageProcessingExecutor` compartilhado (ver T42) |
| BLUEPRINT §11.1.A / IG ~l.3379 (`DrawdownMonitor`) | Halt automático por drawdown de 24h | Inexistente (ver T43) |
| BLUEPRINT §11.1.B.1 (níveis do Safe Mode) | `HALT`, `CANCEL_ALL`, `PANIC_SELL` com execução assíncrona | Só o enum `SafeModeStatus`; nenhum código executa `CANCEL_ALL`/`PANIC_SELL` e **nenhum código chama `Portfolio.setSafeModeStatus`** (ver T45) |
| `Position.updateCurrentPrice` | Marcação a mercado da posição | Método sem chamador: `currentPrice` fica igual ao `averagePrice` da abertura, então `unrealizedPnl` e `currentPnlPercent` do `StrategyContextDto` são sempre zero |
| T25 (spec) | "Ativação atual: manual via `PUT /api/admin/portfolios/{id}/safe-mode`" | Endpoint não existe |

Esta task fecha o padrão: varrer as seções de capital, risco e concorrência do IG/Blueprint e
classificar cada decisão como **implementada**, **pendente (com task)** ou **descartada (doc
corrigido)**, para que nenhuma decisão financeira fique só no papel.

---

## Escopo

Seções a auditar (no mínimo):

- IG §3 (Portfolio/Safe Mode), §6 (Runner), §7 (reserva de capital), §11 (circuit breakers), §12
  (concorrência/mailbox/backpressure), §13–14 (sinal e execução).
- BLUEPRINT §11 (defesa de capital, níveis de Safe Mode, retomada).
- `docs/PHASE0_INVARIANTS.md`: cada invariante tem teste que a protege?
- Javadocs que afirmam comportamento (ex.: `CapitalRequest` já citado na T36).

Fora do escopo: corrigir código. Cada divergência vira task própria ou correção de documento.

---

## Entregáveis

1. Seção "Matriz de aderência" neste arquivo: `seção → decisão → estado → evidência (arquivo/linha
   ou ausência verificada) → destino (task/correção doc/descartar)`.
2. Docs com divergência de "descartar" corrigidos no próprio PR (IG/Blueprint/Javadoc marcados ou
   reescritos).
3. Divergências de "pendente" com task criada em `.ai/tasks/` e linha no `docs/tasks/BACKLOG.md`.
4. Correção da afirmação factual errada da T25 sobre ativação manual do Safe Mode (feita em
   2026-10-15 com nota apontando para a T45; confirmar).

---

## Critérios de aceitação

1. Toda seção listada no escopo aparece na matriz com estado e evidência.
2. Nenhuma linha "pendente" sem task associada.
3. Nenhum documento do escopo afirma como implementado algo que a matriz classificou como pendente.

---

## Fontes

- `.ai/tasks/T36-capital-reservation-safety-buffer.md` (caso que originou o padrão).
- `docs/IMPLEMENTATION_GUIDE.md`, `docs/BLUEPRINT.md`, `docs/PHASE0_INVARIANTS.md`.
- `core/src/main/java/com/marmitt/core/enums/SafeModeStatus.java`,
  `core/src/main/java/com/marmitt/core/domain/runner/Position.java` (`updateCurrentPrice`),
  `spring-application/src/main/java/com/marmitt/application/spring/config/AsyncConfig.java`.
