# Roadmap — Gestão Agêntica de Estratégias

Visão do mantenedor (2026-10-21) e o caminho até ela. Não é uma task: cada marco abaixo vira uma ou
mais tasks em `/.ai/tasks/` quando for priorizado. Ler junto com `product-direction.md`.

## Visão

Um agente (Claude) opera o portfólio de estratégias como um gestor:

1. **Lê o mercado:** classifica o regime atual (tendência, lateral, alta volatilidade...).
2. **Liga e desliga estratégias** pela classificação de cada uma e pelo desempenho recente. Exemplo:
   mercado lateral e as estratégias de tendência perdendo, então desliga essas e liga as de reversão.
3. **Propõe estratégias novas** quando nenhuma disponível serve ao regime atual.
4. **Valida antes de arriscar:** testa a estratégia nova com dados históricos, depois sem capital,
   depois com pouco capital.
5. **Escala o que funciona:** aumenta o capital de quem provou resultado e reduz o de quem piorou.

## Princípios (não mudam com a visão)

- **Proteções ficam no core.** O agente nunca substitui proteção do lote, Safe Mode, limites
  pré-trade nem conciliação. Ele decide **quais estratégias operam e com quanto**; o core decide se cada
  ordem é segura.
- **Estratégia continua só contrato.** Estratégia nova é uma app nova que implementa o contrato
  (`docs/strategy-service/CONTRACT.md`), cadastrada pela T54. O ctrade não ganha lógica de estratégia.
- **O agente age pela API**, com `X-Reason`, auditado em `operator_actions`, como qualquer operador.
- **Envelope de autonomia no core, não no prompt.** Os limites do que o agente pode fazer sozinho
  (capital máximo de canário, quanto realocar por dia) são regras do core que recusam o excesso com
  código estável. Um prompt errado não pode ultrapassá-los.
- **Toda decisão tem registro e revisão.** Cada decisão grava o motivo, os dados em que se baseou e o
  resultado esperado; depois o agente compara com o resultado real.
- **Testnet primeiro.** O ciclo inteiro roda em testnet antes de tocar capital real. O desempenho na
  testnet é indicativo (livro raso, preços que pulam); a validação de desempenho de verdade só vem com
  produção e capital pequeno.

## O ciclo

```
 observar ──► decidir ──► agir (API) ──► medir ──► revisar a decisão
    │                                                      │
    └──────────── propor estratégia nova ◄─────────────────┘
                  backtest ► shadow ► canário ► escala ► aposentadoria
```

## Blocos necessários

| # | Bloco | Existe hoje | Falta |
|---|---|---|---|
| 1 | **Histórico de eventos** (quando cada runner ligou/desligou e por quê) | Safe Mode, ações do operador, DLQ | Transições do runner e boot: **T59** |
| 2 | **Desempenho por runner e por estratégia, por janela** | `positions.realized_pnl`/`closed_at`; `equity_snapshots` por portfolio | Consulta por runner e por estratégia (todos os runners dela): PnL realizado e flutuante, taxas, nº de operações, acerto, drawdown, tempo exposto, por janela (24h, 7d, 30d) e por regime |
| 3 | **Dados de mercado históricos** | Só o último preço por símbolo | Candles (klines) armazenados ou buscados na exchange pelo adapter; base para regime e backtest |
| 4 | **Classificação de regime** | Nada | Quem calcula (ver decisão 2) e onde fica o histórico de regimes |
| 5 | **Perfil da estratégia** | Catálogo com nome, versão, URL, status | Regimes declarados em que a estratégia funciona, e o perfil **medido** (desempenho por regime, do bloco 2) |
| 6 | **Desligar sem largar o lote** | `halt` para tudo, inclusive a proteção do lote (T58: "halt não negocia nada") | Estado de **drenagem**: o runner para de abrir posição, mas a proteção e a saída da estratégia continuam até zerar. Sem isso, "desligar por regime" deixa lote aberto sem proteção |
| 7 | **Shadow** (estratégia opera sem capital) | Nada | Modo do runner em que a decisão é registrada com execução simulada no preço do momento, sem ordem na exchange; desempenho no mesmo formato do bloco 2 |
| 8 | **Canário e escala** | `max_allocation_percent` e `dedicated_budget` por runner, alteráveis por `PATCH` | Níveis de capital (shadow → canário → normal), critérios de promoção e rebaixamento, limites no core (envelope) |
| 9 | **Backtest** | Nada | Harness que reproduz candles históricos contra o **mesmo contrato HTTP** da estratégia, com fills simulados e taxas; fica fora do ctrade de produção |
| 10 | **Diário de decisões do agente** | `operator_actions` (comando + motivo) | Registro da decisão (dados de entrada, regime, hipótese, resultado esperado) e da revisão posterior |
| 11 | **Onde o agente roda** | Nada | VPS com execução agendada (horas, não minutos), acesso à API e ao diário |
| 12 | **Esteira de apps de estratégia** | `strategy-service-reference` (SMA fixa) | Como o agente escreve, versiona, sobe e derruba uma app de estratégia na VPS, fora do ctrade |

## Marcos

Cada marco entrega valor sozinho e destrava o seguinte.

| Marco | Entrega | Blocos | Destrava |
|---|---|---|---|
| **M0** | Histórico de eventos | 1 | Saber quando cada estratégia operou (T59) |
| **M1** | Medição | 2 | "Qual estratégia está rendendo, e desde quando" pela API |
| **M2** | Mercado e regime | 3, 4 | "Em que regime estamos e em qual estávamos" |
| **M3** | Regulação com as estratégias existentes | 5, 6, 10, 11 | Agente liga, drena e desliga estratégias por regime e desempenho, dentro do envelope, com diário. **Primeira versão da visão** |
| **M4** | Shadow, canário e escala | 7, 8 | Estratégia nova entra sem capital e sobe por critério; o agente realoca capital |
| **M5** | Backtest | 9 | Filtrar estratégia ruim antes do shadow |
| **M6** | Proposta de estratégias | 12 | Agente escreve a app, faz backtest, shadow, canário e escala. **Visão completa** |

Pré-requisitos de todos: a trilha do soak (T53) estável e **várias apps de estratégia** que
implementem o contrato (hoje só existe a SMA de referência). M3 com uma estratégia só não tem o que
regular.

## Decisões pendentes (mantenedor)

1. **Ordem dos marcos.** Recomendação: M0 → M1 → M2 → M3, e só então avaliar M4–M6. M3 já entrega a
   regulação pedida com estratégias escritas à mão.
2. **Quem classifica o regime.** Opções:
   - (a) uma **app externa de regime**, com contrato próprio, como as estratégias: o ctrade só guarda e
     expõe o regime; o classificador evolui sem deploy do ctrade (**recomendada**);
   - (b) o próprio agente calcula a partir dos candles a cada execução: simples, mas sem histórico
     consistente nem reprodutibilidade;
   - (c) cálculo no core: contraria "estratégia/análise fora do ctrade".
3. **Drenagem (bloco 6):** novo estado do runner ou política do `halt`. Recomendação: estado próprio,
   para "parar de abrir" e "parar tudo" não virarem o mesmo botão.
4. **Envelope de autonomia inicial** (para quando chegar a M3/M4). Proposta para discussão:
   - sozinho: drenar/desligar e religar runners já aprovados, mover estratégia de shadow para canário;
   - sozinho, com limite: realocar até X% do capital do portfolio por dia entre runners já em operação;
   - com o mantenedor: estratégia nova recebendo capital acima do canário, aumentar limites de risco,
     qualquer ação em produção nos primeiros meses.
5. **Backtest dentro do repositório ou fora.** Recomendação: módulo/ferramenta separada no mesmo
   repositório, sem dependência do runtime de produção, que reaproveita o cliente do contrato.

## Riscos

- **Overfitting:** estratégia proposta e calibrada no mesmo histórico parece ótima e falha ao vivo.
  Mitigação: janelas fora da amostra no backtest e o shadow como teste obrigatório.
- **Gangorra:** ligar e desligar a cada mudança curta de regime gera custo e operações pela metade.
  Mitigação: tempo mínimo em cada estado e histerese na mudança de regime.
- **Amostra pequena:** desempenho de poucas operações é ruído. Critérios de promoção exigem número
  mínimo de operações, não só PnL.
- **Testnet engana:** fills e liquidez da testnet não representam o mercado; desempenho medido lá só
  valida o mecanismo, não a estratégia.
- **Agente sem contexto decide mal:** cada execução lê o diário e o histórico (blocos 1, 2 e 10) antes
  de decidir; o envelope no core limita o estrago.

## Fontes

- `product-direction.md` (visão e estado atual, divisão de responsabilidades)
- `tasks/T41-remote-strategy-minimal-extraction.md`, `tasks/T54-dynamic-strategy-catalog.md` (contrato e catálogo)
- `tasks/T58-price-for-open-lots.md` (halt não negocia; motivação do bloco 6)
- `tasks/T59-event-timeline.md` (M0)
