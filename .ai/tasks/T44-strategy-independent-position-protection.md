# T44 — Proteção de Posição Independente da Estratégia

**Complexidade:** Alta (gera ordens fora da estratégia)
**Responsável:** Claude
**Dependências:** T43 (último preço por símbolo, reutilizável). Motivada pela T41, mas vale também
para estratégias in-process.
**Status:** Pendente — **decisão de negócio pendente** (limites e configuração por runner)

---

## Descrição

Toda saída de posição hoje depende da estratégia emitir `SHOULD_SELL`. O contrato out-of-process
define que estratégia indisponível = **HOLD implícito**: seguro para entradas, mas significa que,
com o serviço de estratégia fora do ar, **nenhuma posição aberta é vendida**, por maior que seja a
queda. O mesmo vale in-process para estratégia desabilitada, removida ou com bug que sempre retorna
HOLD.

Stop-loss aparece apenas como pergunta aberta em `docs/ROADMAP_QUESTOES.md` §7. Esta task define e
implementa uma proteção mínima que o `core` executa sem consultar a estratégia.

---

## Solução proposta

1. **Configuração por runner** (persistida, opcional, default definido na decisão de negócio):
   - `maxLossPercentPerLot` — perda máxima por lote em relação ao preço de entrada;
   - `maxHoldingDuration` — tempo máximo de vida de um lote (opcional).
2. **Avaliação no core**, a cada tick do símbolo, **antes e independente** da estratégia, dentro do
   fluxo do runner (respeitando a mailbox da T42): lote que violar um limite gera uma saída protetiva.
3. **Saída protetiva reaproveita o pipeline existente**: monta um sinal SELL com `targetLotId` do
   lote e passa pelo `SellSignalHandler` (persistência antes do envio, lock de posição, normalização).
   Nenhum caminho paralelo de ordem.
4. **Precedência:** saída protetiva vence a decisão da estratégia no mesmo tick; se o lote já estiver
   travado por outra SELL, não faz nada (o lock já garante a não-duplicação).
5. **Ordens em voo do lote:** se a estratégia tiver uma SELL limite descansando para o lote, avaliar
   cancelá-la (`SHOULD_CANCEL`/`OrderDispatchPort.cancel`, T32/T33) antes da saída protetiva.
6. **Observabilidade:** outcome `PROTECTIVE_EXIT` em `signal.evaluated.total`, log com motivo
   (`MAX_LOSS`/`MAX_HOLDING`), alerta Discord.
7. **Também com Safe Mode `HALT`:** a proteção continua ativa (ela só reduz exposição).

---

## Decisão de negócio necessária

- Defaults de `maxLossPercentPerLot` / `maxHoldingDuration` e se são obrigatórios em produção.
- Tipo de ordem da saída (MARKET vs. LIMIT agressiva com `tickSize`) e tratamento de slippage.
- Se lotes abertos antes da ativação da feature entram na proteção.

---

## Critérios de aceitação

1. Com a estratégia retornando sempre HOLD (ou indisponível), um lote que ultrapassa o limite de
   perda é vendido pelo pipeline normal, com outcome `PROTECTIVE_EXIT`.
2. Nenhuma SELL duplicada no mesmo lote (lock existente), inclusive com a estratégia emitindo SELL
   no mesmo tick.
3. A proteção opera com Safe Mode `HALT` ativo.
4. Teste E2E no harness da T38 cobrindo queda de preço com estratégia "sempre HOLD".
5. `.ai/flows/runner-signal-processing.md` atualizado.

---

## Fontes

- `docs/strategy-service/CONTRACT.md` ("Falha e timeout").
- `docs/ROADMAP_QUESTOES.md` §7 (stop-loss).
- `core/src/main/java/com/marmitt/core/application/usecase/runner/processsignal/ProcessTradeSignalUseCase.java`,
  `SellSignalHandler.java`.
- `.ai/tasks/T32-strategy-cancel-action.md`, `.ai/tasks/T33-order-cancel-dispatch-foundation.md`.
