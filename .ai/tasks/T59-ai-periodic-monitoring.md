# T59 — Monitoramento Periódico por IA

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T55 (estado consolidado, auditoria, runbook), T53 (soak: decisão 7), T25/T43/T44 (eventos
automáticos), T57/T58 (conferências que o monitor usa). VPS só para a Parte C.
**Status:** Pendente — decisões do mantenedor abertas (ver "Decisões pendentes").

---

## Descrição

A decisão 7 da T53 pede que, durante as rodadas longas (24h, 72h), o Claude verifique o sistema
periodicamente por um gatilho agendado com prompt definido, e não só no fim. O ctrade já é verificável de
fora (`GET /api/admin/operational-state`, runbook com procedimento por verificação, limites de autonomia),
mas falta o resto:

1. **O procedimento periódico** em si: o que olhar a cada execução, com que frequência, o que registrar,
   quando agir e quando escalar. Hoje o runbook descreve um ciclo para um operador presente, não uma
   verificação sem memória a cada N minutos.
2. **Memória entre execuções.** Cada execução agendada começa do zero. Para dizer "o que mudou desde a
   última vez" e ver tendência, ela precisa ler o registro das anteriores.
3. **O que o sistema fez sozinho entre duas execuções.** `operator-actions` registra só comandos HTTP;
   `GET /api/admin/portfolios/{id}/safe-mode` mostra só o nível atual. Um HALT automático (drawdown, DLQ,
   divergência de saldo), um runner parado por rejeições, uma saída protetiva do lote (T44) ou um boot
   que aconteceram e foram resolvidos entre duas verificações **não podem ser consultados pela API**; só
   aparecem no Discord e nos logs. Isso fere o princípio "verificável de fora"
   (`.ai/product-direction.md`).
4. **Onde e como o agente roda**, com acesso à API, às métricas e aos logs.

## Contexto técnico

- `GET /api/admin/operational-state` (17 verificações com `includeExchange=true`), `X-Admin-Key`.
- `GET /api/admin/operator-actions` — comandos com `X-Reason`/`X-Operator` (T55).
- Eventos automáticos hoje só em log e Discord: `AutomaticSafeModeTrigger` (HALT automático, tabela
  `safe_mode_changes`), `ConsecutiveRejectionGuard` (`[RUNNER_AUTO_HALTED]`), `PositionProtectionHandler`
  (`[PROTECTIVE_EXIT]`), boot (`/api/boot/status` só do último).
- Métricas em `/actuator/prometheus` (Prometheus do Compose), logs no Loki (`level` como label), JSON.
- `docs/OPERATIONS_RUNBOOK.md`: ciclo de verificação, limites de autonomia (confirmados em 2026-10-20),
  quando escalar (Discord).

## Solução proposta

### Parte A — Procedimento e prompt versionados (sem código no ctrade)

1. **`docs/monitoring/periodic-check.md`**: o prompt da execução periódica, escrito como procedimento
   para um agente sem contexto. A cada execução:
   1. Ler o registro da rodada (item 3): última execução, achados abertos.
   2. `GET /api/admin/operational-state`; com `includeExchange=true` só a cada 4ª execução (cota da
      exchange).
   3. O que aconteceu desde a última execução: `operator-actions` e a linha do tempo da Parte B.
   4. Logs `ERROR` e `WARN` novos no Loki desde a última execução, agrupados por mensagem.
   5. A cada 12ª execução: tendência de memória e threads da JVM, latência de avaliação e de
      conciliação (Prometheus), comparadas com o início da rodada (critério 7 da T53).
   6. Para cada `CRITICAL`/`WARN` **novo**: seguir a seção do runbook dentro dos limites de autonomia.
   7. Gravar a execução no registro e escalar quando o runbook manda.
2. **Saída de cada execução:** uma linha JSON no registro com instante, status geral, verificações fora
   de `OK` (novas e persistentes), eventos desde a última, `ERROR` novos, ações tomadas (com o `X-Reason`)
   e escalação. Resumo legível no fim de cada rodada, que alimenta o "Registro de execuções" da T53.
3. **Registro da rodada:** `soak-runs/<rodada>/checks.jsonl` na máquina onde o agente roda (fora do
   repositório). O agente lê as últimas linhas no começo de cada execução. Sem estado em memória.
4. **Escalação:** Discord (webhook já usado pelo ctrade) só para `CRITICAL` novo, ação tomada ou ação que
   exige o mantenedor; resumo a cada 6h ("rodada X: N verificações, tudo OK" ou a lista do que aconteceu).
5. **Autonomia:** a mesma tabela do runbook. Toda ação com `X-Operator: claude-monitor`, para separar das
   ações da sessão interativa.

### Parte B — Linha do tempo de eventos (código no ctrade)

`GET /api/admin/events?since=<instante>&limit=` — eventos do sistema e do operador em ordem, com tipo
estável, alvo (portfolio/runner/transação), motivo e instante:

| Tipo | Origem |
|---|---|
| `SAFE_MODE_CHANGED` | `safe_mode_changes` (manual e automático, com o gatilho) |
| `RUNNER_STATUS_CHANGED` | transições do runner, incluindo o halt automático por rejeições |
| `PROTECTIVE_EXIT` | venda do lote pela proteção (T44) |
| `BOOT_COMPLETED` / `BOOT_FAILED` | execuções de boot |
| `DLQ_ENTRY` | entrada nova na DLQ |
| `OPERATOR_ACTION` | o que já está em `operator-actions` |

Fonte única: as tabelas que já registram cada evento, mais uma tabela `system_events` só para o que
hoje não fica gravado (proteção do lote, halt automático de runner, boot). Somente leitura e paginada.
Responde à pergunta "o que aconteceu desde as 14h?" sem acesso ao banco nem aos logs.

### Parte C — Onde o agente roda (depende da VPS)

Opções (ver decisão pendente 1):

| Opção | Como | Acesso | Prós | Contras |
|---|---|---|---|---|
| **1. Na VPS, sem interface** (recomendada) | `cron` chama o Claude Code em modo não interativo com o prompt da Parte A e ferramentas restritas | `localhost` (API, Prometheus, Loki) | Nada exposto à internet; funciona com a máquina do mantenedor desligada | Login do Claude Code na VPS; consumo do plano a cada execução |
| 2. Agente agendado na nuvem | rotina agendada do Claude | API, Prometheus e Loki expostos com TLS e chave | Sem instalar nada na VPS | Superfície de ataque; três serviços expostos |
| 3. Sessão local em loop | sessão do Claude Code na máquina do mantenedor, repetindo a verificação | túnel SSH até a VPS | Pronto hoje | Depende do PC ligado e da sessão aberta |

Antes da VPS, a Parte A pode ser exercitada com a opção 3 numa rodada curta local (fase 1 da T53), para
ajustar o prompt.

## Decisões pendentes (mantenedor)

1. **Onde o agente roda:** recomendação opção 1 (VPS, cron, sem interface); opção 3 só para ensaiar o
   prompt antes da VPS.
2. **Cadência:** recomendação a cada **30 min** (48 execuções por rodada de 24h); comparação com a
   exchange a cada 2h; tendência da JVM a cada 6h. Mais frequente = mais consumo do plano.
3. **Autonomia durante o soak:** recomendação **a mesma do runbook** (o soak também testa o operador
   IA). Alternativa: somente leitura e escalar tudo, para o soak medir só o ctrade.
4. **Parte B (linha do tempo) agora ou depois:** recomendação **agora**, antes da fase 2 da T53. Sem ela,
   o monitor depende de ler logs para saber o que o sistema fez sozinho.
5. **Resumo periódico no Discord:** a cada 6h ou só no fim da rodada.

## Fora do escopo

- Monitoramento contínuo de produção (vem depois da trilha testnet; reaproveita este procedimento).
- IA regulando ou propondo estratégias (visão de produto, sem task).
- Ações além dos limites de autonomia do runbook.

## Riscos

- **Execução sem contexto agindo errado:** mitigado por agir só em verificação **nova** com procedimento
  no runbook, dentro dos limites, e por registrar toda ação com `claude-monitor`.
- **Ruído:** `WARN` persistente e explicado (ex.: runners `HALTED` de propósito) não pode escalar a cada
  execução; o registro marca o achado como já reportado.
- **Cota da exchange:** `includeExchange` consulta conta e trades; limitado a cada 2h.
- **Credenciais na VPS:** `ADMIN_API_KEY` e o login do Claude Code ficam na VPS; o prompt nunca imprime
  a chave no registro.
- **Execução que falha ou trava:** o próprio registro mostra o buraco; o resumo de 6h acusa execução
  faltando.

## Critérios de aceitação

1. Uma execução, sem contexto além do prompt e do registro, produz uma linha válida no registro e escala
   só o que é novo.
2. Um HALT automático que aconteceu e foi revertido entre duas execuções aparece na seguinte (Parte B).
3. `WARN` persistente e já reportado não gera nova escalação.
4. Ação tomada pelo monitor aparece em `operator-actions` com `X-Operator: claude-monitor` e motivo.
5. Rodada de 24h termina com o resumo pronto para o registro da T53.

## Validação

- **Parte A:** ensaio local (opção 3) numa rodada curta da fase 1, com uma falha provocada (runner com
  estratégia desabilitada, por exemplo) e conferência do registro e da escalação.
- **Parte B:** unitários do use case da linha do tempo (cada fonte, ordem, `since`, paginação);
  integração com Postgres: HALT automático, retomada manual e proteção do lote aparecem em ordem.
- **Parte C:** primeira rodada de 24h da fase 2 da T53 com o monitor ligado.

## Fontes

- `.ai/tasks/T53-testnet-soak.md` (decisão 7, pré-requisito "acesso do monitoramento por IA")
- `.ai/tasks/T55-ai-operability.md`, `.ai/product-direction.md` (princípios de operabilidade por IA)
- `docs/OPERATIONS_RUNBOOK.md` (ciclo de verificação, limites de autonomia, quando escalar, alertas)
