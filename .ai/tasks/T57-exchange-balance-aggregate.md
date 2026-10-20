# T57 — Conferência de Saldo por Exchange (Soma dos Portfolios × Conta)

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T25 (HALT automático `BALANCE_DIVERGENCE`), T55-C2 (comparação com a exchange), boot
fase 2 (sanidade de saldo). Origem: achado F3 da rodada 1 da T53.
**Status:** Pendente — decisões registradas; pronta para implementação.

---

## Descrição

Hoje o saldo de **cada portfolio** é comparado com o saldo **inteiro** da conta na exchange
(`PortfolioBootSanityUseCase`, usado no boot e no `operational-state?includeExchange=true`). Com vários
portfolios na mesma conta — o uso normal, inclusive na testnet — cada um dá `WARN_SURPLUS` e a situação
que importa (a soma dos portfolios passar do que a conta tem) **não é detectada**.

O portfolio é um **orçamento virtual** sobre uma conta compartilhada: separar o dinheiro é papel do livro
do ctrade, não da exchange (chaves de API diferentes da mesma conta veem o mesmo saldo; subcontas exigiriam
um adapter por conta e não são necessárias aqui). O invariante a proteger é: **o que o ctrade acha que tem
numa exchange não pode passar do que a conta tem**, na moeda de cotação e em cada ativo comprado.

## Contexto técnico

- `core/.../usecase/boot/phase2/PortfolioBootSanityUseCase` — compara `GlobalBalance.getTotalBalance()`
  (`available + reserved`) do portfolio com `free + locked` da moeda base na conta; `FAIL_DEFICIT` quando
  local > exchange.
- `RunBootSequenceUseCase` (fase 2) — chama por portfolio × exchange; `FAIL_DEFICIT` aciona
  `AutomaticSafeModeTrigger` (`BALANCE_DIVERGENCE`, T25) para aquele portfolio.
- `InspectOperationalStateUseCase` (T55-C2) — `EXCHANGE_BALANCE_DIVERGENCE` reaproveita a mesma unidade.
- `GlobalBalance.confirmExecution` — o custo da BUY executada **fica em `reserved` até a SELL do lote
  casar**. Na conta, esse dinheiro já virou o ativo comprado.
- `ExchangeAdapterDescriptor.accountQuery().queryAccountSnapshot()` → `AccountDataDto` com `balances`
  (free) e `lockedBalances` por ativo.
- Lotes abertos: `Position` (`OPEN`/`CLOSING`) com `quantity` (líquida da fee no ativo, T46) e `costBasis`.

## Regra

Por exchange (runners não arquivados de todos os portfolios naquela exchange):

1. **Moeda de cotação** (a moeda base dos portfolios, ex.: USDT):
   `esperado = Σ (available + reserved − custo dos lotes abertos)` dos portfolios, ou seja, o disponível
   mais as reservas de BUY em voo (que na exchange aparecem como `locked`). Comparar com `free + locked`
   da conta. **Esperado maior que a conta → `CRITICAL`**; conta maior → normal, com o excedente informado
   como `unallocated` (saldo da conta fora de qualquer portfolio).
2. **Cada ativo comprado** (ex.: BTC): `esperado = Σ quantity` dos lotes abertos dos runners daquela
   exchange. Comparar com `free + locked` do ativo na conta (SELL no livro trava o ativo em `locked`).
   **Esperado maior que a conta → `CRITICAL`** (o ctrade acha que tem cripto que não existe; a próxima
   SELL falha). Conta maior → normal.
3. Tolerância por moeda/ativo: o limiar já existente
   (`runner.boot.phase2.portfolio.sanity-check.threshold`), aplicado à diferença absoluta.

## Decisões registradas (2026-10-20)

1. A soma dos portfolios acima da conta (moeda de cotação ou ativo) é **`CRITICAL` e coloca em `HALT`
   todos os portfolios daquela exchange** (não dá para atribuir o déficit a um portfolio só). Mesmo
   gatilho `BALANCE_DIVERGENCE` da T25; retomada sempre manual.
2. Separação de fundos fica no ctrade (portfolio = orçamento virtual); subcontas na exchange fora do
   escopo.

## Solução proposta

1. **Uma unidade no core** (substitui a comparação por portfolio): `ExchangeBalanceReconciliation` (nome
   a definir) que, para uma exchange, carrega portfolios e lotes abertos dos runners dela, consulta a conta
   uma vez e devolve o resultado por moeda/ativo (`localExpected`, `exchangeTotal`, `delta`, `unallocated`,
   status).
2. **Boot (fase 2):** passa a chamar a unidade por exchange; `CRITICAL` aciona o HALT de todos os
   portfolios da exchange. Some a chamada por portfolio (manual §1.3).
3. **`operational-state?includeExchange=true`:** `EXCHANGE_BALANCE_DIVERGENCE` passa a mostrar o
   resultado por exchange e ativo. A consulta segue **somente leitura**.
4. **Durante a operação (proposta):** a mesma unidade avaliada periodicamente pelo
   `OperationalTriggerScheduler` (T25), por exemplo a cada 5 min (uma consulta de conta por exchange,
   custo baixo de cota), acionando o HALT quando `CRITICAL`. O HALT nunca sai de um `GET`.
5. Runbook: `#exchange-balance-divergence` reescrito (sem a limitação F3) e `#boot-not-ready` ajustado.

## Riscos

- **Fórmula atual provavelmente acusa falso `FAIL_DEFICIT` com lote aberto:** o `reserved` local inclui o
  custo do lote, que na conta já virou o ativo. É a explicação mais provável do HALT automático
  `BALANCE_DIVERGENCE` visto num portfolio antigo antes da rodada 1. A nova regra (item 1) corrige;
  confirmar com teste antes de mudar.
- Fees no ativo de cotação ou em BNB: a T46 cobre fee no ativo recebido; fee em terceiro ativo (BNB)
  quebraria a conta de cotação — pré-requisito do runbook (BNB para fees desligado).
- Saldo depositado/sacado na conta fora do ctrade: depósito vira `unallocated` (normal); saque abaixo do
  esperado vira `CRITICAL` + HALT (correto: o ctrade operaria sobre dinheiro que saiu).
- Ordens em voo no instante da consulta (fill entre a leitura local e a da conta): diferença transitória;
  a avaliação periódica deve exigir a divergência em duas leituras seguidas antes do HALT, ou usar a
  tolerância — a definir na implementação.

## Critérios de aceitação

1. Vários portfolios na mesma conta, somando menos que a conta: `OK`, com `unallocated` informado.
2. Soma dos portfolios (cotação) acima da conta: `CRITICAL` e HALT automático de todos os portfolios da
   exchange, no boot e na avaliação periódica.
3. Lotes abertos somando mais do ativo do que a conta tem: `CRITICAL` e HALT.
4. Lote aberto com a conta coerente não acusa déficit na moeda de cotação.
5. `GET /api/admin/operational-state?includeExchange=true` mostra o resultado por exchange e ativo, sem
   efeito colateral.

## Validação

- Unitários da unidade: soma abaixo/acima, lote aberto, ativo comprado faltando, tolerância, conta sem
  consulta (`SKIPPED`).
- Integração: boot com déficit agregado → HALT de todos os portfolios da exchange.
- Repetir a fase 1 da T53 (vários portfolios, uma conta): `EXCHANGE_BALANCE_DIVERGENCE` em `OK`.

## Fontes

- `.ai/tasks/T53-testnet-soak.md` (rodada 1, achado F3)
- `.ai/tasks/T25-safe-mode-automatico.md`, `.ai/tasks/T55-ai-operability.md` (C2)
- `docs/OPERATIONS_RUNBOOK.md` (`#exchange-balance-divergence`)
