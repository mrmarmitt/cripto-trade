# Implementation Guide — CTrade

## 1. Propósito e Escopo

Este documento detalha **como construir** cada componente definido no Blueprint. Enquanto o Blueprint define "o quê" e "por quê", este guia responde "como" — com decisões técnicas de implementação, padrões de código e contratos entre componentes.

### Escopo

- Comunicação entre agregados: síncrona vs assíncrona, garantias de entrega, retry e timeout
- Consistência de dados: eventual consistency, window máximo, impacto de saldo desatualizado
- Granularidade de Position: definição por modo (Hedging vs Netting), composição interna
- Escopo das Accounting Policies: aplicação em aberturas vs fechamentos, mutabilidade dinâmica
- Capital Request: abstração (chamada direta vs serviço), sincronia
- Validação de Trade Parameters: níveis de validação, configuração, tratamento de falha
- Context Injection: composição do contexto injetado na Strategy
- Cooldown: granularidade (por símbolo, direção, global), regras de reset

## 2. Relação com outros documentos

- **[Blueprint](BLUEPRINT.md):** Define a arquitetura que este guia implementa
- **Operations Runbook:** Consome as decisões técnicas deste guia para definir procedimentos operacionais

---

## 3. Modelo de Dados

Esta seção especifica campo-a-campo como transformar o modelo de dados atual (agregado único — Portfolio) no modelo alvo definido pelo Blueprint (dois agregados — Portfolio + StrategyRunner). Serve como referência autoritativa para a implementação das entidades, Value Objects, enums e schema do banco de dados.

> **Referência:** Blueprint Seção 3 (Modelo de Dados e Relacionamentos) e Seção 7 (Aggregate Boundaries).

### 3.1 Estado Atual (Resumo)

O modelo atual utiliza um **único agregado** (Portfolio) que concentra responsabilidades financeiras, operacionais e de execução. A tabela abaixo mapeia cada entidade/VO ao seu package, tabela DB e campos-chave.

| Entidade / VO            | Package (Domain)            | Tabela DB             | Campos-Chave                                                                                                                                                                                                         |
|--------------------------|-----------------------------|-----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Portfolio**            | `domain.portfolio`          | `portfolios`          | `id`, `name`, `strategyId`, `strategyName`, `symbol`, `initialCapitalAmount/Currency`, `orderExecutionExchange`, `isActive`, `createdAt`                                                                             |
| **Balance**              | `domain.portfolio`          | `portfolio_balances`  | `portfolioId` (PK/FK), `availableAmount/Currency`, `investedAmount/Currency`, `realizedPnL`, `lastExecutionTime`                                                                                                     |
| **Position**             | `domain.portfolio`          | `positions`           | `portfolioId` (PK/FK), `symbol`, `quantityAmount/Currency`, `averagePriceAmount/Currency`, `currentPriceAmount/Currency`                                                                                             |
| **Transaction**          | `domain.portfolio`          | `transactions`        | `id`, `portfolioId` (FK), `clientOrderId`, `status`, `type`, `symbol`, `quantity` (3 cols), `executedQuantity` (3 cols), `price` (3 cols), `executedPrice` (3 cols), `total` (3 cols), `fee` (3 cols), `targetLotId` |
| **TransactionMatch**     | `domain.portfolio`          | `transaction_matches` | `buyTransactionId` + `sellTransactionId` (PK composta), `matchedQuantity`, `createdAt`                                                                                                                               |
| **Asset** (VO)           | `domain.portfolio`          | — (embutido)          | `amount` (BigDecimal), `currency` (String), `type` (AssetType)                                                                                                                                                       |
| **AccountingPolicy**     | `domain.portfolio.contrats` | — (interface)         | `calculatePosition()`, `matchOrder()`, `computeCost()`                                                                                                                                                               |
| **FifoAccountingPolicy** | `domain.portfolio.contrats` | —                     | Implementação FIFO com `DUST_THRESHOLD = 1e-8`                                                                                                                                                                       |
| **TradingDecision** (VO) | `domain.portfolio`          | —                     | `decision`, `symbol`, `quantity`, `price`, `targetLotId`, `reasoning`, `confidence`                                                                                                                                  |
| **TradingAssetsConfig**  | `domain.portfolio`          | —                     | Classificação de ativos (FIAT, STABLECOIN, CRYPTO)                                                                                                                                                                   |

**Problemas identificados no modelo atual:**
1. **God Aggregate:** Portfolio acumula gestão financeira (Balance), execução (Transaction/TransactionMatch), configuração de estratégia (`strategyId`, `symbol`) e posição calculada.
2. **Asset VO com 3 colunas:** Cada campo Asset persiste `amount`, `currency` e `type` — totalizando 18 colunas apenas para os 6 campos Asset de uma Transaction.
3. **Position 1:1 com Portfolio:** Não suporta múltiplas posições simultâneas (Hedging).
4. **TransactionMatch sem PK próprio:** PK composta (`buyTransactionId`, `sellTransactionId`) dificulta referenciação e não permite múltiplos matches parciais entre o mesmo par.
5. **Sem Fee VO dedicado:** Fee é um Asset genérico sem campos específicos (tipo da fee, ativo original vs convertido).
6. **Sem StrategyRunner:** Toda lógica de lifecycle, policies e reconciliação está acoplada ao Portfolio.

### 3.2 Estado Alvo: Agregado Portfolio

O Portfolio no modelo alvo é o **mestre financeiro** — responsável exclusivamente pelo `GlobalBalance`, alocação de margem, roteamento de eventos e defesa de capital. Campos operacionais (`strategyId`, `symbol`, etc.) migram para o StrategyRunner.

> **Referência:** Blueprint Seção 2.A, 7.A, 9.1, 9.2.C, 11.1.

#### 3.2.1 Portfolio (Aggregate Root)

| Campo                | Tipo                 | Constraint               | Origem  | Notas                                                                                                                              |
|----------------------|----------------------|--------------------------|---------|------------------------------------------------------------------------------------------------------------------------------------|
| `id`                 | `UUID`               | PK                       | KEEP    | —                                                                                                                                  |
| `name`               | `String`             | UNIQUE, NOT NULL         | KEEP    | Nome legível do portfolio                                                                                                          |
| `isActive`           | `boolean`            | NOT NULL, DEFAULT true   | KEEP    | Controle de ativação geral                                                                                                         |
| `createdAt`          | `Instant`            | NOT NULL                 | KEEP    | —                                                                                                                                  |
| `safeModeStatus`     | `SafeModeStatus`     | NOT NULL, DEFAULT NORMAL | NEW     | Nível ativo do Safe Mode (NORMAL, HALT, CANCEL_ALL, PANIC_SELL). Persistido para sobreviver a restarts (Blueprint 11.1.B.1)        |
| `capitalPoolingMode` | `CapitalPoolingMode` | NOT NULL, DEFAULT SHARED | NEW     | Modo de visibilidade de capital: SHARED (todos competem pelo saldo) ou DEDICATED (fatias reservadas por Runner) (Blueprint 11.2.C) |
| `version`            | `Long`               | Optimistic Lock          | KEEP    | —                                                                                                                                  |

**Campos removidos (migram para StrategyRunner):**

| Campo Removido                  | Destino                        | Justificativa                                      |
|---------------------------------|--------------------------------|----------------------------------------------------|
| `strategyId`                    | `StrategyRunner.strategyId`    | A estratégia pertence ao Runner, não ao Portfolio  |
| `strategyName`                  | `StrategyRunner.strategyName`  | Idem                                               |
| `symbol`                        | `StrategyRunner.symbol`        | O ativo operado é configuração do Runner           |
| `orderExecutionExchange`        | `StrategyRunner.exchangeId`    | A exchange de execução é por Runner                |
| `initialCapitalAmount/Currency` | `GlobalBalance.initialCapital` | Migra para sub-entidade financeira                 |
| `allowedMarketDataSources`      | `StrategyRunner` (config)      | Fontes de dados são contexto operacional do Runner |

#### 3.2.2 GlobalBalance (substitui Balance)

O `GlobalBalance` é um **atributo do Portfolio** (1:1) que substitui o `Balance` atual. A principal mudança é a decomposição de `invested` em `reservedBalance` (capital congelado para ordens em voo) e o rastreio explícito de fees pagas.

| Campo               | Tipo         | Constraint          | Origem                                        | Notas                                                                       |
|---------------------|--------------|---------------------|-----------------------------------------------|-----------------------------------------------------------------------------|
| `portfolioId`       | `UUID`       | PK, FK → Portfolio  | KEEP                                          | —                                                                           |
| `availableBalance`  | `BigDecimal` | NOT NULL            | RENAME (`availableAmount`)                    | Saldo livre para novas operações. Moeda implícita = moeda base do Portfolio |
| `reservedBalance`   | `BigDecimal` | NOT NULL, DEFAULT 0 | NEW (split de `invested`)                     | Capital congelado para ordens PENDING/SUBMITTED. Antes era `investedAmount` |
| `realizedBalance`   | `BigDecimal` | NOT NULL, DEFAULT 0 | RENAME (`realizedPnL`)                        | PnL acumulado de todas as operações fechadas de todos os Runners            |
| `initialCapital`    | `BigDecimal` | NOT NULL            | MOVE (de `portfolios.initialCapitalAmount`)   | Capital inicial depositado                                                  |
| `baseCurrency`      | `String(20)` | NOT NULL            | MOVE (de `portfolios.initialCapitalCurrency`) | Moeda base do portfolio (ex: USDT)                                          |
| `totalFeesPaid`     | `BigDecimal` | NOT NULL, DEFAULT 0 | NEW                                           | Rastreio de fees acumuladas de todos os Runners (Blueprint 9.1)             |
| `lastExecutionTime` | `Instant`    | NULLABLE            | KEEP                                          | Último trade executado                                                      |
| `updatedAt`         | `Instant`    | NOT NULL            | KEEP                                          | —                                                                           |
| `version`           | `Long`       | Optimistic Lock     | KEEP                                          | —                                                                           |

**Decisão de simplificação — Moeda implícita:** No modelo atual, cada campo Asset persiste `amount + currency + type` (3 colunas). No modelo alvo, o `GlobalBalance` opera em **moeda única** (a `baseCurrency`). Os campos de saldo (`availableBalance`, `reservedBalance`, etc.) são `BigDecimal` puro, eliminando a redundância de currency/type por campo. A moeda é inferida do campo `baseCurrency`.

**Semântica dos campos:**
- `availableBalance`: Poder de compra real — `Total - Reserved - DustDebits`
- `reservedBalance`: Margem "congelada" para ordens em voo. Cresce no `Capital Request`, decresce na confirmação de execução (→ `realizedBalance`) ou no estorno (→ `availableBalance`)
- `realizedBalance`: PnL líquido acumulado. Após cada `TransactionMatch`, a margem correspondente é convertida de Reserved → Realized, com fees deduzidas
- `totalFeesPaid`: Soma incremental de todas as fees (já convertidas para `baseCurrency`) — métrica de eficiência, não afeta o saldo diretamente

#### 3.2.3 MarginAccount (Nova — 1:N com Portfolio)

O `MarginAccount` controla o capital reservado por exchange/sub-conta. Permite que um Portfolio opere em múltiplas exchanges simultaneamente, cada uma com sua própria contabilidade de margem.

> **Referência:** Blueprint Seção 3, 7.A.

| Campo             | Tipo         | Constraint               | Notas                                              |
|-------------------|--------------|--------------------------|----------------------------------------------------|
| `id`              | `UUID`       | PK                       | —                                                  |
| `portfolioId`     | `UUID`       | FK → Portfolio, NOT NULL | —                                                  |
| `exchangeId`      | `String(50)` | NOT NULL                 | Identificador da exchange (ex: BINANCE, OKX)       |
| `reservedCapital` | `BigDecimal` | NOT NULL, DEFAULT 0      | Total de margem alocada para ordens desta exchange |
| `totalFeesPaid`   | `BigDecimal` | NOT NULL, DEFAULT 0      | Fees acumuladas nesta exchange                     |
| `isActive`        | `boolean`    | NOT NULL, DEFAULT true   | —                                                  |
| `createdAt`       | `Instant`    | NOT NULL                 | —                                                  |
| `updatedAt`       | `Instant`    | NOT NULL                 | —                                                  |
| `version`         | `Long`       | Optimistic Lock          | —                                                  |

**Constraint de unicidade:** `UNIQUE(portfolioId, exchangeId)` — no máximo uma conta por exchange por portfolio.

#### 3.2.4 DustAccount (Sub-entidade do Portfolio — Nova)

A `DustAccount` é o receptor universal de débitos não resolvidos e resíduos operacionais. Seus valores são **excluídos** do `AvailableBalance`, mantendo o capital de giro "limpo".

> **Referência:** Blueprint Seção 9.2.C.

| Campo             | Tipo             | Constraint                    | Notas                                                             |
|-------------------|------------------|-------------------------------|-------------------------------------------------------------------|
| `id`              | `UUID`           | PK                            | —                                                                 |
| `portfolioId`     | `UUID`           | FK → Portfolio, NOT NULL      | —                                                                 |
| `runnerId`        | `UUID`           | FK → StrategyRunner, NULLABLE | Runner que gerou o resíduo (nullable para dust do Portfolio)      |
| `sourceType`      | `DustSourceType` | NOT NULL                      | ROUNDING_DUST ou TECHNICAL_DEBT                                   |
| `originalAsset`   | `String(20)`     | NOT NULL                      | Ativo original do débito (ex: BNB, BTC)                           |
| `originalAmount`  | `BigDecimal`     | NOT NULL                      | Valor no ativo original                                           |
| `convertedAmount` | `BigDecimal`     | NULLABLE                      | Valor convertido para baseCurrency (null = pendente de conversão) |
| `transactionId`   | `UUID`           | FK → Transaction, NULLABLE    | Transação que originou o resíduo                                  |
| `isResolved`      | `boolean`        | NOT NULL, DEFAULT false       | Se já foi processado pelo Worker de Sweep                         |
| `resolvedAt`      | `Instant`        | NULLABLE                      | —                                                                 |
| `createdAt`       | `Instant`        | NOT NULL                      | Timestamp da transação original (para conversão histórica)        |

**Fontes de entrada (DustSourceType):**
1. **ROUNDING_DUST:** Resíduo de arredondamento quando a quantidade restante de um lote < `minQty` da exchange
2. **TECHNICAL_DEBT:** Falha na conversão de Fee cross-currency (Fallback Crítico — Blueprint 9.1)

#### 3.2.5 DeadLetterEntry (DLQ — Nova)

Captura execuções órfãs, IDs inválidos ou transações irreconciliáveis para intervenção manual.

> **Referência:** Blueprint Seção 4.D, 10.3.D.

| Campo             | Tipo          | Constraint               | Notas                                                                   |
|-------------------|---------------|--------------------------|-------------------------------------------------------------------------|
| `id`              | `UUID`        | PK                       | —                                                                       |
| `portfolioId`     | `UUID`        | FK → Portfolio, NOT NULL | —                                                                       |
| `clientOrderId`   | `String(255)` | NULLABLE                 | ID da ordem (pode ser malformado)                                       |
| `exchangeOrderId` | `String(255)` | NULLABLE                 | ID retornado pela exchange                                              |
| `rawPayload`      | `TEXT`        | NOT NULL                 | JSON do callback original da exchange                                   |
| `reason`          | `DlqReason`   | NOT NULL                 | UNKNOWN_RUNNER, INVALID_FORMAT, RECONCILIATION_CONFLICT, UNKNOWN_SYMBOL |
| `isResolved`      | `boolean`     | NOT NULL, DEFAULT false  | —                                                                       |
| `resolvedBy`      | `String`      | NULLABLE                 | Operador que resolveu                                                   |
| `resolvedAt`      | `Instant`     | NULLABLE                 | —                                                                       |
| `createdAt`       | `Instant`     | NOT NULL                 | —                                                                       |

### 3.3 Estado Alvo: Agregado StrategyRunner

O StrategyRunner é o **mestre operacional** de uma estratégia específica. Ele gerencia o ciclo de vida das ordens, a contabilidade de posições e o matching de transações. Cada Runner opera um único símbolo em uma única exchange.

> **Referência:** Blueprint Seção 2.B, 7.B, 12, 13.

#### 3.3.1 StrategyRunner (Aggregate Root — NOVO)

| Campo                      | Tipo                   | Constraint                | Notas                                                                                                   |
|----------------------------|------------------------|---------------------------|---------------------------------------------------------------------------------------------------------|
| `id`                       | `UUID`                 | PK                        | —                                                                                                       |
| `portfolioId`              | `UUID`                 | FK → Portfolio, NOT NULL  | Portfolio proprietário                                                                                  |
| `shortCode`                | `String(4)`            | NOT NULL, UNIQUE          | Código curto para `clientOrderId` (ex: "01f", "a2b"). Usado no prefixo `{r}{shortCode}` para roteamento |
| `strategyId`               | `UUID`                 | NOT NULL                  | MOVE (de `portfolios.strategyId`)                                                                       |
| `strategyName`             | `String(255)`          | NOT NULL                  | MOVE (de `portfolios.strategyName`)                                                                     |
| `symbol`                   | `String(50)`           | NOT NULL                  | MOVE (de `portfolios.symbol`). Par de trading (ex: BTCUSDT)                                             |
| `exchangeId`               | `String(50)`           | NOT NULL                  | MOVE (de `portfolios.orderExecutionExchange`). Exchange para execução                                   |
| `status`                   | `RunnerStatus`         | NOT NULL, DEFAULT CREATED | Lifecycle: CREATED → INITIALIZING → ACTIVE → HALTED → TERMINATING → ARCHIVED                            |
| `executionPolicy`          | `ExecutionPolicy`      | NOT NULL                  | Regra de entrada: SINGLE, HEDGING, NETTING (Blueprint 2.B.1)                                            |
| `accountingPolicyType`     | `AccountingPolicyType` | NOT NULL, DEFAULT FIFO    | Regra contábil: FIFO, LIFO, SPECIFIC_MATCH (Blueprint 2.B.2)                                            |
| `maxAllocationPercent`     | `BigDecimal`           | NOT NULL                  | Teto máximo de capital como % do GlobalBalance (Blueprint 11.2.B)                                       |
| `maxOpenPositions`         | `Integer`              | NOT NULL, DEFAULT 1       | Número máximo de posições abertas simultâneas                                                           |
| `maxPendingOrders`         | `Integer`              | NOT NULL, DEFAULT 1       | Número máximo de ordens SUBMITTED simultâneas                                                           |
| `isReconciling`            | `boolean`              | NOT NULL, DEFAULT false   | Flag de Boot Sequence — rejeita sinais enquanto true (Blueprint 6.D)                                    |
| `allowedMarketDataSources` | `Set<String>`          | —                         | MOVE (de `portfolio_market_data_sources`)                                                               |
| `createdAt`                | `Instant`              | NOT NULL                  | —                                                                                                       |
| `archivedAt`               | `Instant`              | NULLABLE                  | Soft delete — nunca remover fisicamente (Blueprint 13.C, Nota #27)                                      |
| `version`                  | `Long`                 | Optimistic Lock           | —                                                                                                       |

**Constraint de unicidade:** `UNIQUE(strategyId, symbol, exchangeId, portfolioId)` onde `status NOT IN (ARCHIVED, TERMINATING)` — impede Runners duplicados ativos para a mesma combinação (Blueprint 13.B).

#### 3.3.2 Position (Refatorada)

A Position deixa de ser 1:1 com Portfolio e passa a ser 1:N com StrategyRunner. Ganha campo `status` para suportar lifecycle explícito e `realizedPnl` para rastreio de PnL por posição.

> **Referência:** Blueprint Seção 3, 7.B, 9.3.

| Campo          | Tipo             | Constraint                    | Origem                                       | Notas                                                           |
|----------------|------------------|-------------------------------|----------------------------------------------|-----------------------------------------------------------------|
| `id`           | `UUID`           | PK                            | NEW                                          | Antes a PK era `portfolioId` (1:1); agora é PK próprio (1:N)    |
| `runnerId`     | `UUID`           | FK → StrategyRunner, NOT NULL | CHANGE (era `portfolioId`)                   | Posição pertence ao Runner, não ao Portfolio                    |
| `symbol`       | `String(50)`     | NOT NULL                      | KEEP                                         | Par de trading                                                  |
| `status`       | `PositionStatus` | NOT NULL, DEFAULT OPEN        | NEW                                          | OPEN, CLOSING, CLOSED. Permite controle de lifecycle            |
| `quantity`     | `BigDecimal`     | NOT NULL                      | SIMPLIFY (era `quantityAmount` + 2 cols)     | Quantidade total detida. Moeda inferida do symbol               |
| `averagePrice` | `BigDecimal`     | NOT NULL                      | SIMPLIFY (era `averagePriceAmount` + 2 cols) | Preço médio ponderado (WAP). Campo persistido (Blueprint 9.3.C) |
| `currentPrice` | `BigDecimal`     | NULLABLE                      | SIMPLIFY (era `currentPriceAmount` + 2 cols) | Último preço de mercado                                         |
| `realizedPnl`  | `BigDecimal`     | NOT NULL, DEFAULT 0           | NEW                                          | PnL realizado acumulado desta posição (líquido de fees)         |
| `openedAt`     | `Instant`        | NOT NULL                      | KEEP                                         | —                                                               |
| `closedAt`     | `Instant`        | NULLABLE                      | NEW                                          | Timestamp de fechamento (quando quantity → 0)                   |
| `updatedAt`    | `Instant`        | NOT NULL                      | KEEP                                         | —                                                               |
| `version`      | `Long`           | Optimistic Lock               | KEEP                                         | —                                                               |

**Simplificação Asset → BigDecimal:** Os campos que antes usavam o padrão `amount + currency + type` (3 colunas) passam a ser `BigDecimal` simples. A moeda é inferida do `symbol` do Runner: para BTCUSDT, `quantity` é em BTC e `averagePrice`/`currentPrice` são em USDT.

#### 3.3.3 Transaction (Refatorada)

A Transaction migra do Portfolio para o StrategyRunner. Principais mudanças: FK → Runner, novos campos de exchange, e simplificação de Asset (3 colunas → 1 coluna BigDecimal).

> **Referência:** Blueprint Seção 4, 6.A, 10.1.

| Campo              | Tipo                | Constraint                    | Origem                     | Notas                                                                            |
|--------------------|---------------------|-------------------------------|----------------------------|----------------------------------------------------------------------------------|
| `id`               | `UUID`              | PK                            | KEEP                       | —                                                                                |
| `runnerId`         | `UUID`              | FK → StrategyRunner, NOT NULL | CHANGE (era `portfolioId`) | —                                                                                |
| `clientOrderId`    | `String(255)`       | UNIQUE, NOT NULL              | KEEP                       | Chave de idempotência. Formato: `v1r{shortCode}t{ts}s{seq}{type}_{uuid}`         |
| `exchangeOrderId`  | `String(255)`       | NULLABLE                      | NEW                        | ID atribuído pela exchange após aceite da ordem. Null se crash antes do dispatch |
| `status`           | `TransactionStatus` | NOT NULL                      | KEEP                       | PENDING, SUBMITTED, PARTIAL, FILLED, CANCELED, EXPIRED, REJECTED                 |
| `type`             | `TransactionType`   | NOT NULL                      | KEEP                       | BUY, SELL                                                                        |
| `symbol`           | `String(50)`        | NOT NULL                      | KEEP                       | —                                                                                |
| `quantity`         | `BigDecimal`        | NOT NULL                      | SIMPLIFY (era 3 cols)      | Quantidade solicitada                                                            |
| `executedQuantity` | `BigDecimal`        | NULLABLE                      | SIMPLIFY (era 3 cols)      | Quantidade efetivamente executada                                                |
| `price`            | `BigDecimal`        | NOT NULL                      | SIMPLIFY (era 3 cols)      | Preço solicitado/estimado                                                        |
| `executedPrice`    | `BigDecimal`        | NULLABLE                      | SIMPLIFY (era 3 cols)      | Preço real de execução                                                           |
| `total`            | `BigDecimal`        | NOT NULL                      | SIMPLIFY (era 3 cols)      | Valor total estimado (quantity × price)                                          |
| `confidence`       | `BigDecimal`        | NULLABLE                      | NEW                        | Confiança do sinal da estratégia (0.0-1.0). Para auditoria (Blueprint 4.A)       |
| `reasoning`        | `TEXT`              | NULLABLE                      | NEW                        | Motivação da estratégia. Para auditoria (Blueprint 4.A)                          |
| `targetLotId`      | `UUID`              | FK → Position, NULLABLE       | KEEP                       | Para SELL: posição específica a fechar. Null = AccountingPolicy decide           |
| `requestedAt`      | `Instant`           | NOT NULL                      | KEEP                       | —                                                                                |
| `executedAt`       | `Instant`           | NULLABLE                      | KEEP                       | —                                                                                |
| `rejectReason`     | `TEXT`              | NULLABLE                      | KEEP                       | —                                                                                |
| `version`          | `Long`              | Optimistic Lock               | KEEP                       | —                                                                                |

**Campos removidos:**
- `fee` (3 colunas: `feeAmount`, `feeCurrency`, `feeAssetType`): Migra para o VO `Fee` embutido no `TransactionMatch`. Fees são registradas apenas onde há execução real.
- `portfolioId`: Substituído por `runnerId`

#### 3.3.4 TransactionMatch (Enriquecida)

O TransactionMatch ganha PK próprio, Fee VO embutido, PnL realizado e preços denormalizados para permitir cálculos rápidos sem JOINs.

> **Referência:** Blueprint Seção 4.D, 9.1, 9.3.

| Campo                | Tipo         | Constraint                    | Origem             | Notas                                                                              |
|----------------------|--------------|-------------------------------|--------------------|------------------------------------------------------------------------------------|
| `id`                 | `UUID`       | PK                            | NEW                | Substitui a PK composta. Permite referenciação direta                              |
| `runnerId`           | `UUID`       | FK → StrategyRunner, NOT NULL | NEW                | Para queries eficientes por Runner                                                 |
| `buyTransactionId`   | `UUID`       | FK → Transaction, NOT NULL    | KEEP               | —                                                                                  |
| `sellTransactionId`  | `UUID`       | FK → Transaction, NOT NULL    | KEEP               | —                                                                                  |
| `matchedQuantity`    | `BigDecimal` | NOT NULL, CHECK > 0           | KEEP               | Quantidade casada neste match                                                      |
| `buyPrice`           | `BigDecimal` | NOT NULL                      | NEW (denormalized) | Preço de execução da compra no momento do match                                    |
| `sellPrice`          | `BigDecimal` | NOT NULL                      | NEW (denormalized) | Preço de execução da venda no momento do match                                     |
| `feeAmount`          | `BigDecimal` | NOT NULL, DEFAULT 0           | NEW (VO Fee)       | Valor da fee neste match                                                           |
| `feeAsset`           | `String(20)` | NOT NULL                      | NEW (VO Fee)       | Ativo da fee (pode diferir do par — ex: BNB)                                       |
| `feeType`            | `FeeType`    | NOT NULL                      | NEW (VO Fee)       | MAKER, TAKER, UNKNOWN                                                              |
| `feeConvertedAmount` | `BigDecimal` | NULLABLE                      | NEW (VO Fee)       | Fee convertida para baseCurrency do Portfolio. Null = mesma moeda                  |
| `pnlRealized`        | `BigDecimal` | NOT NULL                      | NEW                | PnL líquido deste match: `(sellPrice - buyPrice) × matchedQuantity - feeConverted` |
| `createdAt`          | `Instant`    | NOT NULL                      | KEEP               | —                                                                                  |

**Constraint de unicidade:** `UNIQUE(buyTransactionId, sellTransactionId)` — mantém a integridade de matching, mas sem ser PK.

### 3.4 Value Objects Compartilhados

#### 3.4.1 ClientOrderId

Chave única de roteamento e idempotência, gerada pelo StrategyRunner antes do dispatch.

> **Referência:** Blueprint Seção 4.C, Nota de Implementação #2.

**Formato:** `v{version}r{runner_short}t{timestamp}s{sequence}{type}_{transaction_uuid}`

| Componente            | Tamanho   | Exemplo             | Descrição                                                                                   |
|-----------------------|-----------|---------------------|---------------------------------------------------------------------------------------------|
| `v{version}`          | 2 chars   | `v1`                | Prefixo de versão do layout. Permite evolução do formato sem quebrar parsing de IDs antigos |
| `r{runner_short}`     | 3-5 chars | `r01f`              | ShortCode do StrategyRunner. Permite roteamento sem consulta ao DB                          |
| `t{timestamp}`        | 14 chars  | `t1700000000000`    | Timestamp em millis (epoch). Para ordenação temporal                                        |
| `s{sequence}`         | 4 chars   | `s001`              | Sequência dentro do mesmo milissegundo. Evita colisão                                       |
| `{type}`              | 1 char    | `N`                 | Tipo: `N` (New/Buy), `S` (Sell), `C` (Cancel)                                               |
| `_{transaction_uuid}` | 17 chars  | `_8da233214f11b12a` | UUID truncado da Transaction. Elo imutável entre DB e Exchange                              |

**Tamanho total:** ~36 caracteres (compatível com o limite de 36 chars da maioria das exchanges).

**Métodos do VO:**
- `generate(shortCode, transactionId, type)` → `ClientOrderId`
- `parse(rawString)` → extrai `version`, `runnerShort`, `timestamp`, `sequence`, `type`, `transactionUuid`
- `getRunnerShort()` → usado pelo Portfolio para roteamento de callbacks (Blueprint 4.C)
- `getTransactionUuid()` → usado para reconciliação com o DB

**Regra de validação no parsing (Blueprint 4.C):** Antes de interpretar qualquer campo, o parser deve extrair e validar o prefixo de versão. Se a versão for desconhecida, abortar o processamento e encaminhar para a DLQ.

#### 3.4.2 Fee (Value Object embutido no TransactionMatch)

Representa o custo operacional imutável de uma execução.

> **Referência:** Blueprint Seção 9.1.

| Campo             | Tipo                    | Descrição                                                           |
|-------------------|-------------------------|---------------------------------------------------------------------|
| `amount`          | `BigDecimal`            | Valor cobrado pela exchange                                         |
| `asset`           | `String`                | Ativo da cobrança (ex: BNB, USDT, BTC)                              |
| `type`            | `FeeType`               | MAKER, TAKER, UNKNOWN                                               |
| `convertedAmount` | `BigDecimal` (nullable) | Valor convertido para baseCurrency. Null se `asset == baseCurrency` |

**Regra de conversão cross-currency (Blueprint 9.1):**
1. Se `fee.asset == portfolio.baseCurrency`: `convertedAmount = amount` (conversão trivial)
2. Se `fee.asset != portfolio.baseCurrency`: Portfolio consulta o Mark Price do ExchangeAdapter e converte no momento do TransactionMatch
3. Em caso de falha total na precificação: débito registrado na `DustAccount` como TECHNICAL_DEBT com o valor original

#### 3.4.3 PositionContext (Read-Only — Contrato da Strategy)

Objeto imutável injetado na Strategy pelo Runner, contendo o estado operacional necessário para decisão.

> **Referência:** Blueprint Seção 9.3.D.

| Campo            | Tipo                    | Descrição                                         |
|------------------|-------------------------|---------------------------------------------------|
| `positionId`     | `UUID`                  | ID da posição (nullable se não há posição aberta) |
| `symbol`         | `String`                | Par de trading                                    |
| `quantity`       | `BigDecimal`            | Quantidade detida                                 |
| `averagePrice`   | `BigDecimal`            | Preço médio ponderado (WAP)                       |
| `currentPrice`   | `BigDecimal`            | Último preço de mercado                           |
| `unrealizedPnl`  | `BigDecimal`            | PnL não realizado (calculado)                     |
| `realizedPnl`    | `BigDecimal`            | PnL realizado acumulado                           |
| `openBuyEntries` | `List<OpenBuyEntryDto>` | Lotes de compra disponíveis (para targeting)      |

### 3.5 Definição de Enums

#### Enums Existentes (Mantidos)

| Enum                | Valores                                                                        | Package Atual      | Notas                                                                                    |
|---------------------|--------------------------------------------------------------------------------|--------------------|------------------------------------------------------------------------------------------|
| `TransactionStatus` | `PENDING`, `SUBMITTED`, `PARTIAL`, `FILLED`, `CANCELED`, `EXPIRED`, `REJECTED` | `domain.portfolio` | Sem alteração. `PARTIALLY_FILLED` renomeado para `PARTIAL` conforme Blueprint 6.A        |
| `TransactionType`   | `BUY`, `SELL`                                                                  | `domain.portfolio` | Sem alteração                                                                            |
| `TradingAction`     | `SHOULD_BUY`, `SHOULD_SELL`, `SHOULD_HOLD`                                     | `domain.portfolio` | Sem alteração                                                                            |
| `AssetType`         | `FIAT`, `STABLECOIN`, `CRYPTO`                                                 | `domain.portfolio` | Mantido para classificação em `TradingAssetsConfig`. Removido das colunas de Transaction |

#### Enums Novos

| Enum                   | Valores                                                                         | Propósito                                                                | Referência (Blueprint)   |
|------------------------|---------------------------------------------------------------------------------|--------------------------------------------------------------------------|--------------------------|
| `RunnerStatus`         | `CREATED`, `INITIALIZING`, `ACTIVE`, `HALTED`, `TERMINATING`, `ARCHIVED`        | Ciclo de vida do StrategyRunner                                          | Seção 13.A               |
| `PositionStatus`       | `OPEN`, `CLOSING`, `CLOSED`                                                     | Estado da posição. CLOSING = há ordens de venda em voo para esta posição | Seção 3, 7.B             |
| `ExecutionPolicy`      | `SINGLE`, `HEDGING`, `NETTING`                                                  | Regra de entrada — como o Runner reage a novos sinais                    | Seção 2.B.1              |
| `AccountingPolicyType` | `FIFO`, `LIFO`, `SPECIFIC_MATCH`                                                | Regra contábil — como vendas são casadas com compras                     | Seção 2.B.2              |
| `SafeModeStatus`       | `NORMAL`, `HALT`, `CANCEL_ALL`, `PANIC_SELL`                                    | Níveis escaláveis do Safe Mode do Portfolio                              | Seção 11.1.B.1           |
| `CapitalPoolingMode`   | `SHARED`, `DEDICATED`                                                           | Modo de visibilidade de capital entre Runners                            | Seção 11.2.C             |
| `FeeType`              | `MAKER`, `TAKER`, `UNKNOWN`                                                     | Tipo da fee cobrada pela exchange                                        | Seção 9.1                |
| `DustSourceType`       | `ROUNDING_DUST`, `TECHNICAL_DEBT`                                               | Origem do resíduo na DustAccount                                         | Seção 9.2.C              |
| `DlqReason`            | `UNKNOWN_RUNNER`, `INVALID_FORMAT`, `RECONCILIATION_CONFLICT`, `UNKNOWN_SYMBOL` | Motivo do encaminhamento para DLQ                                        | Seção 4.D, 10.3.D        |

### 3.6 Schema Alvo (Conceitual)

Tabelas conceituais com campos, tipos e constraints para cada entidade alvo. Formato markdown — sem DDL SQL — para manter flexibilidade durante a implementação.

#### Tabela: `portfolios`

| Coluna                 | Tipo                       | Constraint                 |
|------------------------|----------------------------|----------------------------|
| `id`                   | `UUID`                     | PK                         |
| `name`                 | `VARCHAR(255)`             | UNIQUE, NOT NULL           |
| `is_active`            | `BOOLEAN`                  | NOT NULL, DEFAULT TRUE     |
| `safe_mode_status`     | `VARCHAR(20)`              | NOT NULL, DEFAULT 'NORMAL' |
| `capital_pooling_mode` | `VARCHAR(20)`              | NOT NULL, DEFAULT 'SHARED' |
| `created_at`           | `TIMESTAMP WITH TIME ZONE` | NOT NULL                   |
| `version`              | `BIGINT`                   | DEFAULT 0                  |

**Indexes:** `idx_portfolios_is_active(is_active)`

#### Tabela: `global_balances` (renomeia `portfolio_balances`)

| Coluna                | Tipo                       | Constraint                |
|-----------------------|----------------------------|---------------------------|
| `portfolio_id`        | `UUID`                     | PK, FK → `portfolios(id)` |
| `available_balance`   | `NUMERIC(30,8)`            | NOT NULL                  |
| `reserved_balance`    | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0       |
| `realized_balance`    | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0       |
| `initial_capital`     | `NUMERIC(30,8)`            | NOT NULL                  |
| `base_currency`       | `VARCHAR(20)`              | NOT NULL                  |
| `total_fees_paid`     | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0       |
| `last_execution_time` | `TIMESTAMP WITH TIME ZONE` | NULLABLE                  |
| `updated_at`          | `TIMESTAMP WITH TIME ZONE` | NOT NULL                  |
| `version`             | `BIGINT`                   | DEFAULT 0                 |

#### Tabela: `margin_accounts`

| Coluna             | Tipo                       | Constraint                      |
|--------------------|----------------------------|---------------------------------|
| `id`               | `UUID`                     | PK                              |
| `portfolio_id`     | `UUID`                     | FK → `portfolios(id)`, NOT NULL |
| `exchange_id`      | `VARCHAR(50)`              | NOT NULL                        |
| `reserved_capital` | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0             |
| `total_fees_paid`  | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0             |
| `is_active`        | `BOOLEAN`                  | NOT NULL, DEFAULT TRUE          |
| `created_at`       | `TIMESTAMP WITH TIME ZONE` | NOT NULL                        |
| `updated_at`       | `TIMESTAMP WITH TIME ZONE` | NOT NULL                        |
| `version`          | `BIGINT`                   | DEFAULT 0                       |

**Indexes:** `UNIQUE(portfolio_id, exchange_id)`

#### Tabela: `dust_accounts`

| Coluna             | Tipo                       | Constraint                            |
|--------------------|----------------------------|---------------------------------------|
| `id`               | `UUID`                     | PK                                    |
| `portfolio_id`     | `UUID`                     | FK → `portfolios(id)`, NOT NULL       |
| `runner_id`        | `UUID`                     | FK → `strategy_runners(id)`, NULLABLE |
| `source_type`      | `VARCHAR(30)`              | NOT NULL                              |
| `original_asset`   | `VARCHAR(20)`              | NOT NULL                              |
| `original_amount`  | `NUMERIC(30,8)`            | NOT NULL                              |
| `converted_amount` | `NUMERIC(30,8)`            | NULLABLE                              |
| `transaction_id`   | `UUID`                     | FK → `transactions(id)`, NULLABLE     |
| `is_resolved`      | `BOOLEAN`                  | NOT NULL, DEFAULT FALSE               |
| `resolved_at`      | `TIMESTAMP WITH TIME ZONE` | NULLABLE                              |
| `created_at`       | `TIMESTAMP WITH TIME ZONE` | NOT NULL                              |

**Indexes:** `idx_dust_portfolio_unresolved(portfolio_id, is_resolved)`, `idx_dust_runner(runner_id)`

#### Tabela: `dead_letter_entries`

| Coluna              | Tipo                       | Constraint                      |
|---------------------|----------------------------|---------------------------------|
| `id`                | `UUID`                     | PK                              |
| `portfolio_id`      | `UUID`                     | FK → `portfolios(id)`, NOT NULL |
| `client_order_id`   | `VARCHAR(255)`             | NULLABLE                        |
| `exchange_order_id` | `VARCHAR(255)`             | NULLABLE                        |
| `raw_payload`       | `TEXT`                     | NOT NULL                        |
| `reason`            | `VARCHAR(50)`              | NOT NULL                        |
| `is_resolved`       | `BOOLEAN`                  | NOT NULL, DEFAULT FALSE         |
| `resolved_by`       | `VARCHAR(255)`             | NULLABLE                        |
| `resolved_at`       | `TIMESTAMP WITH TIME ZONE` | NULLABLE                        |
| `created_at`        | `TIMESTAMP WITH TIME ZONE` | NOT NULL                        |

**Indexes:** `idx_dlq_portfolio_unresolved(portfolio_id, is_resolved)`

#### Tabela: `strategy_runners`

| Coluna                   | Tipo                       | Constraint                      |
|--------------------------|----------------------------|---------------------------------|
| `id`                     | `UUID`                     | PK                              |
| `portfolio_id`           | `UUID`                     | FK → `portfolios(id)`, NOT NULL |
| `short_code`             | `VARCHAR(4)`               | UNIQUE, NOT NULL                |
| `strategy_id`            | `UUID`                     | NOT NULL                        |
| `strategy_name`          | `VARCHAR(255)`             | NOT NULL                        |
| `symbol`                 | `VARCHAR(50)`              | NOT NULL                        |
| `exchange_id`            | `VARCHAR(50)`              | NOT NULL                        |
| `status`                 | `VARCHAR(20)`              | NOT NULL, DEFAULT 'CREATED'     |
| `execution_policy`       | `VARCHAR(20)`              | NOT NULL                        |
| `accounting_policy_type` | `VARCHAR(20)`              | NOT NULL, DEFAULT 'FIFO'        |
| `max_allocation_percent` | `NUMERIC(5,4)`             | NOT NULL                        |
| `max_open_positions`     | `INTEGER`                  | NOT NULL, DEFAULT 1             |
| `max_pending_orders`     | `INTEGER`                  | NOT NULL, DEFAULT 1             |
| `is_reconciling`         | `BOOLEAN`                  | NOT NULL, DEFAULT FALSE         |
| `created_at`             | `TIMESTAMP WITH TIME ZONE` | NOT NULL                        |
| `archived_at`            | `TIMESTAMP WITH TIME ZONE` | NULLABLE                        |
| `version`                | `BIGINT`                   | DEFAULT 0                       |

**Indexes:** `idx_runners_portfolio(portfolio_id)`, `idx_runners_status(status)`, `idx_runners_active_unique` = partial unique on `(strategy_id, symbol, exchange_id, portfolio_id)` WHERE `status NOT IN ('ARCHIVED', 'TERMINATING')`

#### Tabela: `runner_market_data_sources`

| Coluna      | Tipo          | Constraint                            |
|-------------|---------------|---------------------------------------|
| `runner_id` | `UUID`        | FK → `strategy_runners(id)`, NOT NULL |
| `source`    | `VARCHAR(50)` | NOT NULL                              |

**PK:** `(runner_id, source)`

#### Tabela: `positions` (refatorada)

| Coluna          | Tipo                       | Constraint                            |
|-----------------|----------------------------|---------------------------------------|
| `id`            | `UUID`                     | PK                                    |
| `runner_id`     | `UUID`                     | FK → `strategy_runners(id)`, NOT NULL |
| `symbol`        | `VARCHAR(50)`              | NOT NULL                              |
| `status`        | `VARCHAR(20)`              | NOT NULL, DEFAULT 'OPEN'              |
| `quantity`      | `NUMERIC(30,8)`            | NOT NULL                              |
| `average_price` | `NUMERIC(30,8)`            | NOT NULL                              |
| `current_price` | `NUMERIC(30,8)`            | NULLABLE                              |
| `realized_pnl`  | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0                   |
| `opened_at`     | `TIMESTAMP WITH TIME ZONE` | NOT NULL                              |
| `closed_at`     | `TIMESTAMP WITH TIME ZONE` | NULLABLE                              |
| `updated_at`    | `TIMESTAMP WITH TIME ZONE` | NOT NULL                              |
| `version`       | `BIGINT`                   | DEFAULT 0                             |

**Indexes:** `idx_positions_runner(runner_id)`, `idx_positions_status(status)`, `idx_positions_runner_open(runner_id, status)` WHERE `status = 'OPEN'`

#### Tabela: `transactions` (refatorada)

| Coluna              | Tipo                       | Constraint                            |
|---------------------|----------------------------|---------------------------------------|
| `id`                | `UUID`                     | PK                                    |
| `runner_id`         | `UUID`                     | FK → `strategy_runners(id)`, NOT NULL |
| `client_order_id`   | `VARCHAR(255)`             | UNIQUE, NOT NULL                      |
| `exchange_order_id` | `VARCHAR(255)`             | NULLABLE                              |
| `status`            | `VARCHAR(50)`              | NOT NULL                              |
| `type`              | `VARCHAR(20)`              | NOT NULL                              |
| `symbol`            | `VARCHAR(50)`              | NOT NULL                              |
| `quantity`          | `NUMERIC(30,8)`            | NOT NULL                              |
| `executed_quantity` | `NUMERIC(30,8)`            | NULLABLE                              |
| `price`             | `NUMERIC(30,8)`            | NOT NULL                              |
| `executed_price`    | `NUMERIC(30,8)`            | NULLABLE                              |
| `total`             | `NUMERIC(30,8)`            | NOT NULL                              |
| `confidence`        | `NUMERIC(3,2)`             | NULLABLE                              |
| `reasoning`         | `TEXT`                     | NULLABLE                              |
| `target_lot_id`     | `UUID`                     | FK → `positions(id)`, NULLABLE        |
| `requested_at`      | `TIMESTAMP WITH TIME ZONE` | NOT NULL                              |
| `executed_at`       | `TIMESTAMP WITH TIME ZONE` | NULLABLE                              |
| `reject_reason`     | `TEXT`                     | NULLABLE                              |
| `version`           | `BIGINT`                   | DEFAULT 0                             |

**Indexes:** `idx_transactions_runner(runner_id)`, `idx_transactions_client_order_id(client_order_id)`, `idx_transactions_status(status)`, `idx_transactions_type(type)`, `idx_transactions_requested_at(requested_at)`, `idx_transactions_inflight(runner_id, status)` WHERE `status IN ('PENDING', 'SUBMITTED', 'PARTIAL')`

#### Tabela: `transaction_matches` (enriquecida)

| Coluna                 | Tipo                       | Constraint                            |
|------------------------|----------------------------|---------------------------------------|
| `id`                   | `UUID`                     | PK                                    |
| `runner_id`            | `UUID`                     | FK → `strategy_runners(id)`, NOT NULL |
| `buy_transaction_id`   | `UUID`                     | FK → `transactions(id)`, NOT NULL     |
| `sell_transaction_id`  | `UUID`                     | FK → `transactions(id)`, NOT NULL     |
| `matched_quantity`     | `NUMERIC(30,8)`            | NOT NULL, CHECK > 0                   |
| `buy_price`            | `NUMERIC(30,8)`            | NOT NULL                              |
| `sell_price`           | `NUMERIC(30,8)`            | NOT NULL                              |
| `fee_amount`           | `NUMERIC(30,8)`            | NOT NULL, DEFAULT 0                   |
| `fee_asset`            | `VARCHAR(20)`              | NOT NULL                              |
| `fee_type`             | `VARCHAR(20)`              | NOT NULL                              |
| `fee_converted_amount` | `NUMERIC(30,8)`            | NULLABLE                              |
| `pnl_realized`         | `NUMERIC(30,8)`            | NOT NULL                              |
| `created_at`           | `TIMESTAMP WITH TIME ZONE` | NOT NULL                              |

**Indexes:** `UNIQUE(buy_transaction_id, sell_transaction_id)`, `idx_matches_runner(runner_id)`, `idx_matches_buy(buy_transaction_id)`, `idx_matches_sell(sell_transaction_id)`

### 3.7 Matriz de Migração (Delta)

Mapeamento coluna-a-coluna de cada tabela atual para o estado alvo.

**Legenda de ações:**
- **KEEP** — Coluna mantida sem alteração
- **RENAME** — Coluna renomeada (mesmos dados)
- **MOVE** — Coluna migra para outra tabela
- **DROP** — Coluna removida (dados descartados ou absorvidos por outra coluna)
- **SIMPLIFY** — Coluna mantida com redução de colunas auxiliares (remove currency/type)
- **NEW** — Coluna nova (sem correspondência no schema atual)

#### `portfolios` → `portfolios`

| Coluna Atual               | Ação  | Coluna Alvo                         | Notas            |
|----------------------------|-------|-------------------------------------|------------------|
| `id`                       | KEEP  | `id`                                | —                |
| `name`                     | KEEP  | `name`                              | —                |
| `strategy_id`              | MOVE  | → `strategy_runners.strategy_id`    | —                |
| `strategy_name`            | MOVE  | → `strategy_runners.strategy_name`  | —                |
| `symbol`                   | MOVE  | → `strategy_runners.symbol`         | —                |
| `initial_capital_amount`   | MOVE  | → `global_balances.initial_capital` | —                |
| `initial_capital_currency` | MOVE  | → `global_balances.base_currency`   | —                |
| `order_execution_exchange` | MOVE  | → `strategy_runners.exchange_id`    | —                |
| `is_active`                | KEEP  | `is_active`                         | —                |
| `created_at`               | KEEP  | `created_at`                        | —                |
| `version`                  | KEEP  | `version`                           | —                |
| —                          | NEW   | `safe_mode_status`                  | DEFAULT 'NORMAL' |
| —                          | NEW   | `capital_pooling_mode`              | DEFAULT 'SHARED' |

#### `portfolio_market_data_sources` → `runner_market_data_sources`

| Coluna Atual   | Ação  | Coluna Alvo                              | Notas                            |
|----------------|-------|------------------------------------------|----------------------------------|
| `portfolio_id` | MOVE  | → `runner_market_data_sources.runner_id` | FK muda de Portfolio para Runner |
| `source`       | KEEP  | `source`                                 | —                                |

#### `portfolio_balances` → `global_balances`

| Coluna Atual          | Ação   | Coluna Alvo           | Notas                                                        |
|-----------------------|--------|-----------------------|--------------------------------------------------------------|
| `portfolio_id`        | KEEP   | `portfolio_id`        | —                                                            |
| `available_amount`    | RENAME | `available_balance`   | —                                                            |
| `available_currency`  | DROP   | —                     | Absorvido por `base_currency`                                |
| `invested_amount`     | RENAME | `reserved_balance`    | Semântica muda: "investido" → "reservado para ordens em voo" |
| `invested_currency`   | DROP   | —                     | Absorvido por `base_currency`                                |
| `realized_pnl`        | RENAME | `realized_balance`    | —                                                            |
| `last_execution_time` | KEEP   | `last_execution_time` | —                                                            |
| `updated_at`          | KEEP   | `updated_at`          | —                                                            |
| `version`             | KEEP   | `version`             | —                                                            |
| —                     | NEW    | `initial_capital`     | Migrado de `portfolios.initial_capital_amount`               |
| —                     | NEW    | `base_currency`       | Migrado de `portfolios.initial_capital_currency`             |
| —                     | NEW    | `total_fees_paid`     | DEFAULT 0                                                    |

#### `positions` → `positions`

| Coluna Atual             | Ação     | Coluna Alvo     | Notas                                             |
|--------------------------|----------|-----------------|---------------------------------------------------|
| `portfolio_id` (PK)      | DROP     | —               | Substituído por `id` (novo PK) e `runner_id` (FK) |
| `symbol`                 | KEEP     | `symbol`        | —                                                 |
| `quantity_amount`        | SIMPLIFY | `quantity`      | Remove colunas auxiliares                         |
| `quantity_currency`      | DROP     | —               | Inferido do `symbol`                              |
| `average_price_amount`   | SIMPLIFY | `average_price` | —                                                 |
| `average_price_currency` | DROP     | —               | Inferido do `symbol`                              |
| `current_price_amount`   | SIMPLIFY | `current_price` | —                                                 |
| `current_price_currency` | DROP     | —               | Inferido do `symbol`                              |
| `opened_at`              | KEEP     | `opened_at`     | —                                                 |
| `updated_at`             | KEEP     | `updated_at`    | —                                                 |
| `version`                | KEEP     | `version`       | —                                                 |
| —                        | NEW      | `id`            | Novo PK (UUID)                                    |
| —                        | NEW      | `runner_id`     | FK → `strategy_runners(id)`                       |
| —                        | NEW      | `status`        | DEFAULT 'OPEN'                                    |
| —                        | NEW      | `realized_pnl`  | DEFAULT 0                                         |
| —                        | NEW      | `closed_at`     | NULLABLE                                          |

#### `transactions` → `transactions`

| Coluna Atual                 | Ação     | Coluna Alvo         | Notas                                                  |
|------------------------------|----------|---------------------|--------------------------------------------------------|
| `id`                         | KEEP     | `id`                | —                                                      |
| `portfolio_id`               | CHANGE   | `runner_id`         | FK muda de Portfolio para Runner                       |
| `client_order_id`            | KEEP     | `client_order_id`   | Adiciona UNIQUE constraint                             |
| `status`                     | KEEP     | `status`            | —                                                      |
| `type`                       | KEEP     | `type`              | —                                                      |
| `symbol`                     | KEEP     | `symbol`            | —                                                      |
| `quantity_amount`            | SIMPLIFY | `quantity`          | —                                                      |
| `quantity_currency`          | DROP     | —                   | Inferido do `symbol`                                   |
| `quantity_type`              | DROP     | —                   | Removido                                               |
| `executed_quantity_amount`   | SIMPLIFY | `executed_quantity` | —                                                      |
| `executed_quantity_currency` | DROP     | —                   | —                                                      |
| `executed_quantity_type`     | DROP     | —                   | —                                                      |
| `price_amount`               | SIMPLIFY | `price`             | —                                                      |
| `price_currency`             | DROP     | —                   | —                                                      |
| `price_type`                 | DROP     | —                   | —                                                      |
| `executed_price_amount`      | SIMPLIFY | `executed_price`    | —                                                      |
| `executed_price_currency`    | DROP     | —                   | —                                                      |
| `executed_price_type`        | DROP     | —                   | —                                                      |
| `total_amount`               | SIMPLIFY | `total`             | —                                                      |
| `total_currency`             | DROP     | —                   | —                                                      |
| `total_type`                 | DROP     | —                   | —                                                      |
| `fee_amount`                 | DROP     | —                   | Migra para `transaction_matches.fee_amount`            |
| `fee_currency`               | DROP     | —                   | Migra para `transaction_matches.fee_asset`             |
| `fee_type`                   | DROP     | —                   | Migra para `transaction_matches.fee_type`              |
| `requested_at`               | KEEP     | `requested_at`      | —                                                      |
| `executed_at`                | KEEP     | `executed_at`       | —                                                      |
| `reject_reason`              | KEEP     | `reject_reason`     | —                                                      |
| `target_lot_id`              | KEEP     | `target_lot_id`     | FK muda de → `transactions(id)` para → `positions(id)` |
| `version`                    | KEEP     | `version`           | —                                                      |
| —                            | NEW      | `exchange_order_id` | NULLABLE                                               |
| —                            | NEW      | `confidence`        | NULLABLE                                               |
| —                            | NEW      | `reasoning`         | NULLABLE                                               |

#### `transaction_matches` → `transaction_matches`

| Coluna Atual               | Ação  | Coluna Alvo            | Notas                                      |
|----------------------------|-------|------------------------|--------------------------------------------|
| `buy_transaction_id` (PK)  | KEEP  | `buy_transaction_id`   | Deixa de ser PK, passa a UNIQUE constraint |
| `sell_transaction_id` (PK) | KEEP  | `sell_transaction_id`  | Idem                                       |
| `matched_quantity`         | KEEP  | `matched_quantity`     | —                                          |
| `created_at`               | KEEP  | `created_at`           | —                                          |
| —                          | NEW   | `id`                   | Novo PK (UUID)                             |
| —                          | NEW   | `runner_id`            | FK → `strategy_runners(id)`                |
| —                          | NEW   | `buy_price`            | Denormalized                               |
| —                          | NEW   | `sell_price`           | Denormalized                               |
| —                          | NEW   | `fee_amount`           | DEFAULT 0                                  |
| —                          | NEW   | `fee_asset`            | —                                          |
| —                          | NEW   | `fee_type`             | —                                          |
| —                          | NEW   | `fee_converted_amount` | NULLABLE                                   |
| —                          | NEW   | `pnl_realized`         | —                                          |

### 3.8 Estratégia de Migração Flyway

Sequência incremental de migrations para transformar o schema atual (V3) no alvo sem perda de dados. Cada migration é atômica e revertível.

> **Princípio:** Todas as migrations utilizam a abordagem **additive-first** — primeiro adiciona colunas/tabelas novas, depois migra dados, por último remove colunas obsoletas. Isso permite rollback seguro em caso de falha.

#### V4 — Criar tabela `strategy_runners` e `runner_market_data_sources`

**Objetivo:** Criar a infraestrutura do novo agregado antes de qualquer migração de dados.

**Ações:**
1. Criar tabela `strategy_runners` com todos os campos (ver Seção 3.6)
2. Criar tabela `runner_market_data_sources`
3. Popular `strategy_runners` a partir de `portfolios` — para cada portfolio ativo, criar um Runner com:
   - `shortCode`: gerado sequencialmente (ex: "001", "002")
   - `strategyId/Name`: copiados do portfolio
   - `symbol`: copiado do portfolio
   - `exchangeId`: copiado de `orderExecutionExchange`
   - `status`: ACTIVE (para portfolios ativos) ou ARCHIVED (para inativos)
   - `executionPolicy`: SINGLE (default conservador)
   - `accountingPolicyType`: FIFO
   - `maxAllocationPercent`: 1.0 (sem limite inicial)
4. Migrar `portfolio_market_data_sources` → `runner_market_data_sources` (apontando para o Runner correspondente)

**Validação:** `COUNT(strategy_runners) == COUNT(portfolios)` e `COUNT(runner_market_data_sources) == COUNT(portfolio_market_data_sources)`

#### V5 — Refatorar `positions` e `transactions`

**Objetivo:** Adicionar FK para Runner e novos campos antes de remover as colunas antigas.

**Ações:**
1. **Positions:**
   - Adicionar colunas: `id` (UUID, gerado), `runner_id`, `status` (DEFAULT 'OPEN'), `realized_pnl` (DEFAULT 0), `closed_at`
   - Popular `runner_id` a partir do mapeamento `portfolio_id → runner_id` criado na V4
   - Popular `id` com UUID gerado
   - Alterar PK: remover `portfolio_id` como PK, definir `id` como nova PK
   - Adicionar FK: `runner_id → strategy_runners(id)`
2. **Transactions:**
   - Adicionar colunas: `runner_id`, `exchange_order_id`, `confidence`, `reasoning`
   - Popular `runner_id` a partir do mapeamento `portfolio_id → runner_id`
   - Adicionar constraint: UNIQUE(`client_order_id`)
   - Adicionar FK: `runner_id → strategy_runners(id)`

**Validação:** Todos os registros de `positions` e `transactions` possuem `runner_id` NOT NULL

#### V6 — Enriquecer `transaction_matches` e criar novas tabelas do Portfolio

**Objetivo:** Criar tabelas novas do agregado Portfolio e enriquecer TransactionMatch.

**Ações:**
1. **Transaction Matches:**
   - Adicionar colunas: `id` (UUID), `runner_id`, `buy_price`, `sell_price`, `fee_amount`, `fee_asset`, `fee_type`, `fee_converted_amount`, `pnl_realized`
   - Popular `id` com UUID gerado
   - Popular `runner_id` a partir do mapeamento via `buy_transaction_id → transaction.runner_id`
   - Popular `buy_price`/`sell_price` a partir das transações vinculadas (`executedPrice`)
   - Popular `fee_amount`/`fee_asset`/`fee_type` a partir da transaction de venda correspondente (migração dos dados de fee)
   - Calcular e popular `pnl_realized` = `(sell_price - buy_price) × matched_quantity - fee_converted`
   - Remover PK composta, definir `id` como nova PK
   - Adicionar UNIQUE(`buy_transaction_id`, `sell_transaction_id`)
2. **Criar tabela** `global_balances` (ver Seção 3.6)
3. **Criar tabela** `margin_accounts` (ver Seção 3.6)
4. **Criar tabela** `dust_accounts` (ver Seção 3.6)
5. **Criar tabela** `dead_letter_entries` (ver Seção 3.6)
6. **Popular** `global_balances` a partir de `portfolio_balances`:
   - `available_balance` ← `available_amount`
   - `reserved_balance` ← `invested_amount`
   - `realized_balance` ← `realized_pnl`
   - `initial_capital` ← `portfolios.initial_capital_amount` (JOIN)
   - `base_currency` ← `portfolios.initial_capital_currency` (JOIN)
   - `total_fees_paid` ← 0 (sem histórico anterior)

**Validação:** `COUNT(global_balances) == COUNT(portfolio_balances)`, todos os `transaction_matches` possuem `id` e `runner_id`

#### V7 — Limpeza: remover colunas e tabelas obsoletas

**Objetivo:** Remover colunas migradas e tabelas substituídas, após confirmação de que os dados foram migrados corretamente.

**Ações:**
1. **Tabela `portfolios`:** Remover colunas `strategy_id`, `strategy_name`, `symbol`, `initial_capital_amount`, `initial_capital_currency`, `order_execution_exchange`
2. **Tabela `portfolios`:** Adicionar colunas `safe_mode_status` (DEFAULT 'NORMAL'), `capital_pooling_mode` (DEFAULT 'SHARED')
3. **Tabela `positions`:** Remover colunas `quantity_currency`, `average_price_currency`, `current_price_currency`, `portfolio_id` (antigo FK). Renomear `quantity_amount` → `quantity`, `average_price_amount` → `average_price`, `current_price_amount` → `current_price`
4. **Tabela `transactions`:** Remover colunas `portfolio_id`, todas as colunas `*_currency`, todas as colunas `*_type` (15 colunas). Renomear `quantity_amount` → `quantity`, `executed_quantity_amount` → `executed_quantity`, `price_amount` → `price`, `executed_price_amount` → `executed_price`, `total_amount` → `total`, `fee_amount` → removido, `fee_currency` → removido, `fee_type` → removido
5. **Tabela `portfolio_balances`:** DROP (substituída por `global_balances`)
6. **Tabela `portfolio_market_data_sources`:** DROP (substituída por `runner_market_data_sources`)
7. Remover indexes obsoletos: `idx_portfolios_symbol`, `idx_portfolios_strategy_id`, `idx_transactions_portfolio_id`

**Validação:** Nenhuma coluna `*_currency` ou `*_type` restante nas tabelas de Transaction/Position. Nenhum FK referenciando `portfolios.strategy_id`.

#### V8 — Criar indexes de performance

**Objetivo:** Adicionar indexes otimizados para os novos padrões de query.

**Ações:**
1. Indexes parciais para queries frequentes:
   - `idx_positions_runner_open(runner_id, status)` WHERE `status = 'OPEN'`
   - `idx_transactions_inflight(runner_id, status)` WHERE `status IN ('PENDING', 'SUBMITTED', 'PARTIAL')`
   - `idx_runners_active_unique` = partial unique on `(strategy_id, symbol, exchange_id, portfolio_id)` WHERE `status NOT IN ('ARCHIVED', 'TERMINATING')`
   - `idx_dust_portfolio_unresolved(portfolio_id, is_resolved)` WHERE `is_resolved = FALSE`
   - `idx_dlq_portfolio_unresolved(portfolio_id, is_resolved)` WHERE `is_resolved = FALSE`
2. Indexes compostos:
   - `idx_matches_runner(runner_id)`
   - `idx_runners_portfolio(portfolio_id)`
   - `idx_runners_status(status)`

---

## 4. Guia de Migração e Decomposição (Refatoração)

> **Origem:** Migrado da Seção 8 do Blueprint. O conteúdo descreve passos práticos de refatoração, que são responsabilidade do Implementation Guide.

Para transformar a arquitetura atual no modelo do Blueprint, as responsabilidades do `Portfolio` legado serão redistribuídas conforme o mapeamento abaixo:

| Responsabilidade Atual do Portfolio      | Novo Dono (Destino)  | Justificativa Técnica                                                                              |
|:-----------------------------------------|:---------------------|:---------------------------------------------------------------------------------------------------|
| **GlobalBalance (Available, Reserved)**  | **Portfolio**        | Autoridade sobre o saldo real e reservas globais.                                                  |
| **Margem Reservada (Shadow Balance)**    | **Portfolio**        | Mantém o saldo "congelado" enquanto a transação está `PENDING` ou `SUBMITTED`.                     |
| **PnL Consolidado**                      | **Portfolio**        | Visão agregada dos resultados de todos os Runners ativos.                                          |
| **Roteamento de Eventos (Parser)**       | **Portfolio**        | Identifica o Runner proprietário via prefixo do ID e despacha a mensagem.                          |
| **Dead Letter Queue (DLQ)**              | **Portfolio**        | Captura execuções órfãs ou com IDs inválidos para intervenção manual.                              |
| **Transactions + Status Lifecycle**      | **StrategyRunner**   | Gere o ciclo de vida (Pending → Submitted → Filled/Partial) das ordens.                            |
| **TransactionMatches (Matching)**        | **StrategyRunner**   | O matching (FIFO/LIFO/Specific) é uma regra contábil da estratégia.                                |
| **Position (Calculated View)**           | **StrategyRunner**   | A exposição líquida por ativo pertence ao contexto operacional do Runner.                          |
| **Exchange Config (Symbol/Keys)**        | **StrategyRunner**   | Conhece as regras específicas (tick size, min qty) do seu ativo.                                   |
| **Geração de clientOrderId**             | **StrategyRunner**   | Garante a inclusão do `runner_short` e do `transaction_uuid` para roteamento.                      |
| **Locking de Lotes (Provisional)**       | **StrategyRunner**   | Impede que um lote em processo de venda seja usado por outro sinal concorrente.                    |
| **Gestão de Partial Fills**              | **StrategyRunner**   | Controla a contabilidade incremental e solicita ajustes parciais de margem.                        |
| **Watchdog de Timeouts**                 | **StrategyRunner**   | O Runner monitora se suas ordens "em voo" estão demorando mais do que o permitido pela estratégia. |

### Notas de Implementação para a Refatoração:

1. **Desacoplamento de Repositórios**: Iniciar pela criação do `StrategyRunnerRepository`, segregando as tabelas de `Positions` e `Transactions` do domínio financeiro do `Portfolio`.
2. **Protocolo de Identificação**: Implementar o Value Object `ClientOrderId` para centralizar a lógica de geração e parsing do ID de 32/36 caracteres.
3. **Atomicidade na Reserva**: A chamada de `Capital Request` deve ser o único ponto de sincronização impeditivo entre os Agregados para garantir integridade de saldo antes do envio à Exchange.

---

## 5. Protocolo de Comunicação entre Agregados

Esta seção define **como** os dois agregados (Portfolio e StrategyRunner) se comunicam, incluindo interfaces, padrões de entrega, modelo de consistência e mecanismos de proteção contra falhas. É a fundação sobre a qual todas as seções seguintes (6-13) se apoiam.

> **Referência:** Blueprint Seção 7.C (Comunicação entre Agregados: Padrão Híbrido).
> **Notas absorvidas:** #21 (Interface de Comunicação), #22 (Idempotência no Portfolio).
> **Questões endereçadas:** "Comunicação entre Agregados — Detalhes Técnicos", "Consistência de Dados entre Agregados" (IMPLEMENTATION_GUIDE_QUESTOES.md).

### 5.1 Princípio Arquitetural

O sistema adota um **Padrão Híbrido**: operações que requerem consistência forte (reserva de capital) são **síncronas**, enquanto operações de liquidação e estorno (que toleram latência) são **assíncronas**. Isso equilibra segurança financeira com throughput operacional.

```
┌───────────────────┐         ┌────────────────────┐
│  StrategyRunner   │         │    Portfolio       │
│                   │         │                    │
│  ┌─────────────┐  │  sync   │  ┌──────────────┐  │
│  │ Transaction │──┼────────►│  │ GlobalBalance│  │
│  │  (PENDING)  │  │ reserve │  │  (Reserved)  │  │
│  └─────────────┘  │         │  └──────────────┘  │
│                   │         │                    │
│  ┌─────────────┐  │  async  │  ┌──────────────┐  │
│  │ Transaction │──┼────────►│  │ GlobalBalance│  │
│  │  (FILLED)   │  │ confirm │  │ (Realized)   │  │
│  └─────────────┘  │         │  └──────────────┘  │
│                   │         │                    │
│  ┌─────────────┐  │  async  │  ┌──────────────┐  │
│  │ Transaction │──┼────────►│  │ GlobalBalance│  │
│  │ (CANCELED)  │  │ release │  │ (Available)  │  │
│  └─────────────┘  │         │  └──────────────┘  │
└───────────────────┘         └────────────────────┘
```

### 5.2 Interface `CapitalManager`

O Portfolio expõe uma interface única — `CapitalManager` — que centraliza toda a comunicação entre agregados. Esta interface é o **único ponto de acoplamento** entre Runner e Portfolio.

> **Nota de Implementação #21 absorvida.**

#### Definição da Interface

```
interface CapitalManager {

    // ── Síncrono (Request-Response) ──────────────────────────

    ReservationResult reserve(CapitalRequest request)

    // ── Assíncrono (Fire-and-Forget com garantia) ────────────

    void confirmExecution(ExecutionConfirmation confirmation)
    void release(MarginRelease release)
}
```

#### 5.2.1 Operação `reserve` — Capital Request (Síncrono)

**Direção:** Runner → Portfolio
**Natureza:** Síncrona, bloqueante
**Garantia:** Strong Consistency — se retornar OK, a margem está garantida

| Aspecto                         | Decisão                                                                                                                                                                                                                                                                                         |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Quando ocorre**               | Após criação da Transaction PENDING, antes do Order Dispatch                                                                                                                                                                                                                                    |
| **Pré-condições**               | Transaction persistida no DB; `clientOrderId` gerado                                                                                                                                                                                                                                            |
| **Entrada (`CapitalRequest`)**  | `transactionId` (UUID), `runnerId` (UUID), `runnerShortCode` (String), `symbol` (String), `amount` (BigDecimal), `type` (BUY/SELL)                                                                                                                                                              |
| **Saída (`ReservationResult`)** | `status` (APPROVED / REJECTED), `reservationId` (UUID), `rejectionReason` (nullable)                                                                                                                                                                                                            |
| **Timeout**                     | Configurável (default: 5 segundos). Se expirar, Runner trata como REJECTED e aborta                                                                                                                                                                                                             |
| **Thread-safety**               | O método `reserve` é o **ponto de serialização** do GlobalBalance. Implementar com lock pessimista no DB: `UPDATE global_balances SET available_balance = available_balance - :amount, reserved_balance = reserved_balance + :amount WHERE portfolio_id = :id AND available_balance >= :amount` |
| **Validações internas**         | 1. Circuit Breaker: `if (portfolio.safeModeStatus != NORMAL) reject` (Nota #11)<br>2. Limite por Runner: `if (runnerExposure + amount > maxAllocationPercent * totalBalance) reject`<br>3. Saldo: `if (availableBalance < amount) reject`                                                       |

**Cenários de rejeição:**

| Motivo                    | Código                  | Ação do Runner                          |
|---------------------------|-------------------------|-----------------------------------------|
| Safe Mode ativo           | `RISK_VIOLATION`        | Descarta sinal, loga evento             |
| Limite do Runner excedido | `RUNNER_LIMIT_EXCEEDED` | Descarta sinal, loga evento             |
| Saldo insuficiente        | `INSUFFICIENT_FUNDS`    | Descarta sinal, loga evento             |
| Timeout na comunicação    | `TIMEOUT`               | Aborta, marca Transaction como REJECTED |
| Runner desconhecido       | `UNKNOWN_RUNNER`        | Aborta, encaminha para DLQ              |

#### 5.2.2 Operação `confirmExecution` — Confirmação de Execução (Assíncrono)

**Direção:** Runner → Portfolio
**Natureza:** Assíncrona
**Garantia:** At-least-once com idempotência no receptor

| Aspecto                               | Decisão                                                                                                                                                                                                                          |
|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Quando ocorre**                     | Após cada `TransactionMatch` ser persistido (PARTIAL ou FILLED)                                                                                                                                                                  |
| **Entrada (`ExecutionConfirmation`)** | `transactionId` (UUID), `runnerId` (UUID), `matchId` (UUID), `executedQuantity` (BigDecimal), `executedPrice` (BigDecimal), `fee` (Fee VO — amount, asset, type), `totalCost` (BigDecimal), `isFinal` (boolean — true se FILLED) |
| **Efeito no Portfolio**               | Converte margem de Reserved → Realized: `reserved -= totalCost`, `realized += pnlAmount`, `totalFeesPaid += feeConverted`                                                                                                        |
| **Idempotência**                      | Portfolio rastreia `matchId` processados. Se receber duplicate, descarta silenciosamente (Nota #22)                                                                                                                              |
| **Retry**                             | Em caso de falha no processamento, o evento é reenfileirado com backoff exponencial (1s, 2s, 4s, max 30s)                                                                                                                        |
| **Max retries**                       | 5 tentativas. Após esgotar, evento vai para DLQ                                                                                                                                                                                  |

#### 5.2.3 Operação `release` — Rollback de Margem (Assíncrono)

**Direção:** Runner → Portfolio
**Natureza:** Assíncrona com retries garantidos
**Garantia:** At-least-once — capital preso (starvation) é inaceitável

| Aspecto                       | Decisão                                                                                                                                                                                            |
|-------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Quando ocorre**             | Transaction atinge estado terminal REJECTED, CANCELED ou EXPIRED                                                                                                                                   |
| **Entrada (`MarginRelease`)** | `transactionId` (UUID), `runnerId` (UUID), `releaseAmount` (BigDecimal), `reason` (REJECTED / CANCELED / EXPIRED), `executedAmount` (BigDecimal — parcela já executada, para estorno proporcional) |
| **Efeito no Portfolio**       | Devolve margem: `reserved -= releaseAmount`, `available += releaseAmount`                                                                                                                          |
| **Cálculo do estorno**        | `releaseAmount = originalReserved - executedAmount`. Para REJECTED/EXPIRED: `executedAmount = 0` (estorno total)                                                                                   |
| **Idempotência**              | Portfolio rastreia `transactionId` já estornados. Duplicate → descarte silencioso                                                                                                                  |
| **Criticidade**               | Este evento **nunca pode ser perdido**. Se o EventBus falhar, o Runner persiste o evento localmente e reenvia no próximo ciclo                                                                     |
| **Retry**                     | Backoff exponencial sem limite de tentativas. O evento é retentado até sucesso                                                                                                                     |

### 5.3 Mecanismo de Entrega

#### 5.3.1 Arquitetura V1: EventBus em Memória (Monólito)

Na V1 (monólito Spring Boot), a comunicação assíncrona utiliza um **EventBus em memória** (Spring Application Events), sem dependência de broker externo.

```
Runner                          Portfolio
  │                               │
  │── persist Transaction ───────►│ (DB)
  │                               │
  │── reserve(CapitalRequest) ───►│ (método direto, síncrono)
  │◄── ReservationResult ─────────│
  │                               │
  │── dispatch order ────────────►│ (Exchange)
  │                               │
  │── publish(ExecutionConfirmed)►│ (Spring ApplicationEvent, async)
  │                               │
  │── publish(MarginRelease) ────►│ (Spring ApplicationEvent, async)
```

**Implementação Spring:**

| Componente           | Padrão                                                            | Notas                                                                                          |
|----------------------|-------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| `reserve()`          | Chamada direta de método via interface `CapitalManager`           | Injetada via Spring DI. O Runner nunca referencia `Portfolio` diretamente — apenas a interface |
| `confirmExecution()` | `ApplicationEventPublisher.publishEvent(ExecutionConfirmedEvent)` | Listener no Portfolio com `@TransactionalEventListener(phase = AFTER_COMMIT)`                  |
| `release()`          | `ApplicationEventPublisher.publishEvent(MarginReleaseEvent)`      | Idem. Listener garante que o evento só é processado após o commit do Runner                    |
| Retry                | `@Retryable` (Spring Retry) no listener                           | Com backoff exponencial configurável                                                           |
| Fallback (DLQ)       | `@Recover` no listener                                            | Após max retries, persiste na tabela `dead_letter_entries`                                     |

**Decisão: `@TransactionalEventListener(phase = AFTER_COMMIT)`**

O uso de `AFTER_COMMIT` garante que o evento só é disparado **após** o Runner ter commitado sua transação de banco. Isso evita o cenário onde:
1. Runner publica evento de execução
2. Portfolio tenta processar, mas a Transaction ainda não está commitada no DB
3. Portfolio não encontra os dados e falha

**Trade-off:** Se o sistema crashar entre o commit do Runner e o dispatch do evento, o evento é perdido. Isso é aceitável porque o **Boot Sequence** (Blueprint Seção 6.D; Implementation Guide Seção futura) reconcilia esses gaps.

#### 5.3.2 Evolução para Microserviços (V2+)

Para evolução futura, o padrão muda para **Outbox Pattern** com broker durável:

| Aspecto          | V1 (Monólito)                           | V2+ (Microserviços)                                               |
|------------------|-----------------------------------------|-------------------------------------------------------------------|
| **Transporte**   | Spring ApplicationEvents (in-memory)    | RabbitMQ / Kafka                                                  |
| **Durabilidade** | Perdido em crash (reconciliado no boot) | Persistido no broker                                              |
| **Atomicidade**  | `@TransactionalEventListener`           | Outbox Pattern: evento salvo na mesma tx do Runner                |
| **Interface**    | `CapitalManager` (sem alteração)        | `CapitalManager` (sem alteração — a interface isola o transporte) |

A interface `CapitalManager` abstrai o transporte — mudar de EventBus para Outbox+Kafka exige apenas nova implementação, sem alterar o domínio.

### 5.4 Modelo de Consistência

> **Questão endereçada:** "Como garantir consistência entre os dois agregados?" (IMPLEMENTATION_GUIDE_QUESTOES.md)

#### 5.4.1 Classificação por Operação

| Operação                      | Modelo                   | Window de Inconsistência                                | Impacto se Desatualizado                                                                                                                 |
|-------------------------------|--------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| **Reserva de Margem**         | **Strong Consistency**   | 0 ms (síncrono)                                         | N/A — bloqueante                                                                                                                         |
| **Confirmação de Execução**   | **Eventual Consistency** | 0-500 ms (normal), até 30s (com retries)                | GlobalBalance mostra Reserved quando já deveria ser Realized. Impacto: conservador (não libera capital para novos trades prematuramente) |
| **Estorno de Margem**         | **Eventual Consistency** | 0-500 ms (normal), ilimitado (com retries persistentes) | Capital fica "preso" em Reserved. Impacto: Runner não pode fazer novos trades com esse capital                                           |
| **Saldo visível para Runner** | **Eventual**             | Não aplicável — Runner nunca lê o saldo diretamente     | Runner não toma decisões baseadas no saldo (a Strategy decide quanto, o Runner apenas solicita)                                          |

#### 5.4.2 Garantias de Segurança

**Invariante 1 — Saldo nunca fica negativo:**
A operação `reserve` é atômica e síncrona. O Portfolio rejeita qualquer reserva que tornaria `availableBalance < 0`. Como é o único ponto de entrada para reservas, não há race condition.

**Invariante 2 — Capital nunca é perdido:**
Toda margem reservada é rastreada por `transactionId`. O ciclo é fechado:
- `reserve` → cria reserva vinculada ao `transactionId`
- `confirmExecution` → converte reserva em realizado (parcial ou total)
- `release` → devolve reserva ao disponível

Se nenhum dos três eventos chegar (crash total), o **Boot Sequence** (Blueprint Seção 6.D; Implementation Guide Seção futura) reconcilia.

**Invariante 3 — Nenhum evento duplicado altera o saldo:**
A idempotência por `matchId` (confirmação) e `transactionId` (release) garante que reprocessamento não duplica movimentações.

#### 5.4.3 Decisão: Runner NÃO lê o GlobalBalance

O StrategyRunner **nunca consulta** o saldo do Portfolio diretamente. O fluxo é:

1. A **Strategy** decide quanto comprar/vender (baseada em regras próprias)
2. O **Runner** solicita reserva via `CapitalManager.reserve()`
3. O **Portfolio** aprova ou rejeita

Isso elimina a questão de "decisão com saldo desatualizado" — o Runner não precisa de uma visão atualizada do saldo para operar. A validação de saldo ocorre atomicamente no momento da reserva.

### 5.5 Idempotência e Proteção contra Duplicatas

> **Nota de Implementação #22 absorvida.**

#### 5.5.1 Mecanismo no Portfolio

O Portfolio mantém um registro de operações processadas para cada tipo de evento:

| Evento             | Chave de Idempotência  | Armazenamento                                                                   | Ação em Duplicata                                    |
|--------------------|------------------------|---------------------------------------------------------------------------------|------------------------------------------------------|
| `confirmExecution` | `matchId` (UUID)       | Coluna `id` da tabela `transaction_matches` — se o match já existe, é duplicata | Descarte silencioso + log INFO                       |
| `release`          | `transactionId` (UUID) | Campo de status no registro de reserva — se já foi estornado, é duplicata       | Descarte silencioso + log INFO                       |
| `reserve`          | `transactionId` (UUID) | Se já existe reserva para este `transactionId`, é duplicata (cenário de retry)  | Retorna o `ReservationResult` original (idempotente) |

**Decisão: Sem tabela dedicada de idempotência.** A própria estrutura de dados do domínio serve como store de idempotência:
- A existência de um `TransactionMatch` com o `matchId` indica que a execução já foi confirmada
- O status da reserva (já estornada ou não) indica se o release já foi processado
- Isso evita a necessidade de um Redis ou tabela auxiliar de "processed events"

#### 5.5.2 Cenários de Duplicata e Resolução

| Cenário                                              | Causa                                                               | Detecção                                                                                                                 | Resolução                            |
|------------------------------------------------------|---------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|--------------------------------------|
| Retry após timeout de rede                           | Runner não recebeu ACK do reserve                                   | Portfolio encontra reserva existente para o `transactionId`                                                              | Retorna resultado original           |
| Reprocessamento de evento                            | EventBus reentrega após falha do listener                           | Portfolio verifica se `matchId` já existe                                                                                | Descarta silenciosamente             |
| Boot Sequence reconcilia transação já processada     | Runner reenvia confirmação para tx FILLED que Portfolio já liquidou | Portfolio verifica status da reserva                                                                                     | Descarta, responde com status atual  |
| Dois Runners enviam reserve para mesmo transactionId | Bug ou ataque                                                       | `transactionId` é UUID gerado pelo Runner — colisão estatisticamente impossível. Se ocorrer, segundo request é rejeitado | Rejeição com `DUPLICATE_TRANSACTION` |

### 5.6 Roteamento de Eventos da Exchange (Inbound)

Quando a Exchange envia callbacks de execução (via WebSocket), o fluxo de roteamento é:

```
Exchange (WebSocket)
       │
       ▼
  ExchangeAdapter (Listener)
       │
       │ parse clientOrderId
       ▼
  Portfolio (Router)
       │
       │ extract runnerShortCode
       │ lookup Runner by shortCode
       ▼
  StrategyRunner (Processamento)
       │
       │ atualiza Transaction, cria Match
       │ publica ExecutionConfirmedEvent
       ▼
  Portfolio (Listener assíncrono)
       │
       │ converte Reserved → Realized
       ▼
  GlobalBalance atualizado
```

**Responsabilidades no roteamento:**

| Passo                        | Responsável        | Ação                                              | Falha →                                |
|------------------------------|--------------------|---------------------------------------------------|----------------------------------------|
| 1. Receber callback          | ExchangeAdapter    | Deserializa payload da exchange                   | Log ERROR, descarta (exchange reenvia) |
| 2. Extrair `clientOrderId`   | ExchangeAdapter    | Parse do campo no payload                         | DLQ (payload inválido)                 |
| 3. Validar formato           | Portfolio (Router) | `ClientOrderId.parse()` — valida versão e formato | DLQ com reason `INVALID_FORMAT`        |
| 4. Extrair `runnerShortCode` | Portfolio (Router) | `clientOrderId.getRunnerShort()`                  | DLQ com reason `INVALID_FORMAT`        |
| 5. Lookup Runner             | Portfolio (Router) | Busca Runner por `shortCode`                      | DLQ com reason `UNKNOWN_RUNNER`        |
| 6. Validar propriedade       | Portfolio (Router) | Confirma que o Runner pertence a este Portfolio   | DLQ com reason `UNKNOWN_RUNNER`        |
| 7. Despachar para Runner     | Portfolio (Router) | Chamada direta (síncrona) ao Runner               | Log ERROR, retry, DLQ após max retries |

**Decisão: Roteamento síncrono, processamento assíncrono.**
O Portfolio roteia o callback para o Runner de forma síncrona (chamada de método). O Runner processa, persiste o Match, e então publica o evento assíncrono de volta ao Portfolio para liquidação financeira. Isso garante que:
- O roteamento é imediato (sem fila intermediária)
- O processamento contábil do Runner é atômico
- A liquidação no Portfolio é desacoplada

### 5.7 Diagrama de Sequência Consolidado

O fluxo completo de um trade (happy path) em termos de comunicação entre agregados:

```
Strategy    Runner                Portfolio            Exchange
   │          │                       │                    │
   │─signal──►│                       │                    │
   │          │                       │                    │
   │          │── persist PENDING ───►│ (DB)               │
   │          │                       │                    │
   │          │── reserve() ─────────►│                    │
   │          │   [SYNC]              │── lock balance ───►│ (DB)
   │          │◄── APPROVED ──────────│                    │
   │          │                       │                    │
   │          │── dispatch order ─────┼───────────────────►│
   │          │◄── orderAccepted ─────┼────────────────────│
   │          │── update SUBMITTED ──►│ (DB)               │
   │          │                       │                    │
   │          │                       │◄── execution ──────│ (WebSocket)
   │          │                       │                    │
   │          │◄── route callback ────│                    │
   │          │   [SYNC routing]      │                    │
   │          │                       │                    │
   │          │── persist Match ─────►│ (DB)               │
   │          │── update FILLED ─────►│ (DB)               │
   │          │                       │                    │
   │          │── confirmExecution()─►│                    │
   │          │   [ASYNC event]       │── update balance──►│ (DB)
   │          │                       │   Reserved→Realized|
```

### 5.8 Configuração e Timeouts

| Parâmetro                            | Default   | Descrição                                            |
|--------------------------------------|-----------|------------------------------------------------------|
| `capital.request.timeout-ms`         | 5000      | Timeout do `reserve()`. Se expirar, Runner aborta    |
| `event.retry.max-attempts`           | 5         | Máximo de retries para `confirmExecution`            |
| `event.retry.initial-interval-ms`    | 1000      | Intervalo inicial do backoff exponencial             |
| `event.retry.max-interval-ms`        | 30000     | Intervalo máximo do backoff                          |
| `event.retry.multiplier`             | 2.0       | Multiplicador do backoff                             |
| `margin.release.max-attempts`        | unlimited | Release nunca desiste — capital preso é inaceitável  |
| `margin.release.initial-interval-ms` | 1000      | Intervalo inicial do backoff para release            |
| `routing.unknown-runner.action`      | DLQ       | Ação quando Runner não é encontrado: DLQ ou LOG_ONLY |

### 5.9 Implicações Arquiteturais

#### Mapeamento de Consistência por Operação

> **Referência direta:** Blueprint Seção 7.C, tabela de implicações arquiteturais.

| Operação                                    | Acoplamento        | Garantia de Consistência                                                           | Impacto de Falha                                        |
|---------------------------------------------|--------------------|------------------------------------------------------------------------------------|---------------------------------------------------------|
| **Reserva de Margem** (`reserve`)           | Forte (Síncrono)   | **Atômica**: Saldo bloqueado antes da persistência do `SUBMITTED`                  | Bloqueio imediato do sinal por falta de fundos          |
| **Liquidação de Fees** (`confirmExecution`) | Fraco (Assíncrono) | **Eventual** (0-500ms): O `GlobalBalance` atualiza após o processamento do match   | Discrepância temporária resolvida no próximo checkpoint |
| **Estorno (Refund)** (`release`)            | Fraco (Assíncrono) | **Eventual** (sem limite): O capital retorna ao `Available` após o evento de falha | Capital retido temporariamente até ao sucesso do retry  |

#### Decisões de Design

| Decisão                                    | Justificativa                                                             | Trade-off                                                              |
|--------------------------------------------|---------------------------------------------------------------------------|------------------------------------------------------------------------|
| `reserve()` síncrono                       | Capital não pode ser duplo-gasto. O Runner DEVE bloquear até ter garantia | Latência adicional no fluxo de trade (~1-5ms em monólito)              |
| `confirmExecution()` assíncrono            | A liquidação financeira pode atrasar sem afetar a execução do trade       | Window de eventual consistency (~500ms normal)                         |
| `release()` assíncrono sem limite de retry | Capital preso é pior que latência de liberação                            | Em cenário extremo, retries podem acumular                             |
| Runner nunca lê GlobalBalance              | Elimina stale reads e simplifica consistência                             | Runner não sabe "quanto sobra" — mas não precisa                       |
| EventBus in-memory (V1)                    | Sem dependência externa, simplicidade operacional                         | Eventos perdidos em crash (reconciliados no boot)                      |
| Interface `CapitalManager` como abstração  | Permite migrar de monólito para microserviços sem alterar domínio         | Indireção adicional                                                    |
| Idempotência via estrutura de domínio      | Sem Redis/tabela auxiliar; menos infraestrutura                           | Queries de verificação em cada evento (impacto negligível com indexes) |
