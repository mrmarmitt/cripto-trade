# Documentação de Arquitetura: Gestão de Capital e Execução (CTrade) v9

## 1. Visão Geral

O sistema utiliza uma arquitetura baseada em **Injeção de Contexto**. A lógica de decisão (Estratégia) é separada da lógica de custódia e execução (Runner), permitindo flexibilidade total tanto na entrada de ordens quanto na contabilização de saída.

## 2. Hierarquia de Domínios

### A. Portfolio (O Orquestrador)

* **Responsabilidade:** Gestão do *Balance* global, alocação de margem e **roteamento de eventos/transactions** para os Runners proprietários.

### B. StrategyRunner (O Gerenciador de Execução e Contabilidade)

* **Natureza:** Stateful. Une a Estratégia ao Ativo e gerencia o ciclo de vida das operações (Pending → Submitted → Filled/Partial) através de duas políticas fundamentais (**Execution** e **Accounting**).
* **Gestão de Ordens Pendentes:** Além das `Positions`, o Runner mantém o rastro de ordens enviadas mas ainda não executadas. Ele é o responsável por garantir que toda margem "reservada" no Portfolio seja convertida em uma `Positions` ou devolvida em caso de cancelamento, rejeição ou expiração.

#### B.1 Execution Policy (Regra de Entrada/Exposição)

Define como o Runner reage a novos sinais da estratégia:

* **Modo Single:** Ignora novos sinais se já houver qualquer posição aberta. Garante "um tiro por vez".
* **Modo Hedging/Multi:** Permite a abertura de múltiplas `Positions` independentes e simultâneas.
* **Modo Netting/Scaling:** Permite que novos sinais aumentem ou reduzam o volume de uma posição existente.

#### B.2 Accounting Policy (Regra de Saída/Contabilidade)

Define como as `Transactions` de fechamento são casadas com as de abertura:

* **FIFO (First-In, First-Out):** Padrão para Netting.
* **Specific Match:** Padrão para Hedging (vincula a saída a um ID de posição específico).
* **LIFO (Last-In, First-Out):** Alternativa para estratégias específicas.

### C. TradeStrategy (O Motor de Sinais)

* **Natureza:** Stateless. Analisa o mercado e emite `TradeSignals`.

---

## 3. Modelo de Dados e Relacionamentos

| Entidade           | Relacionamento           | Responsabilidade / Atributo                                 |
|--------------------|--------------------------|-------------------------------------------------------------|
| **Portfolio**      | 1 : N (Runners)          | `GlobalBalance`, Auditoria de Margem.                       |
| **StrategyRunner** | 1 : 1 (ExecutionPolicy)  | Define se aceita novos sinais (**Single**, Multi, Netting). |
| **StrategyRunner** | 1 : 1 (AccountingPolicy) | Define a regra de Match (**FIFO**, LIFO, Specific).         |
| **StrategyRunner** | 1 : N (Positions)        | Exposição líquida e PnL da estratégia.                      |

---

## 4. Fluxo de Execução e Materialização (Ciclo de Vida do Sinal)

O processo de transformação de uma análise técnica em uma operação financeira segue um pipeline rigoroso de materialização e validação:

### A. Geração do Sinal (TradeStrategy)

A estratégia processa os dados de mercado e gera um **`StrategyOutputDto`**. Este objeto é o "contrato de intenção" (Buy/Sell/Hold) que contém a quantidade e, opcionalmente, um `targetLotId`.

* **Ação:** Define se a decisão é `SHOULD_BUY`, `SHOULD_SELL` ou `SHOULD_HOLD`.
* **Metadados:** Carrega o `reasoning` (motivação) e `confidence` (confiança de 0.0 a 1.0), essenciais para auditoria.
* **Quantidade:** Especifica a `quantity` absoluta (ex: 0.05 BTC), permitindo que a estratégia sugira o tamanho exato da mão.

### B. Materialização da Decisão (StrategyRunner)

O Runner recebe o DTO e prepara o estado interno antes de qualquer comunicação externa:

* **Filtro de Exposição (Execution Policy):** O Runner valida o sinal contra seu estado atual (ex: se for *Single* e já houver posição aberta, o sinal é abortado aqui).
* **Criação da Transação PENDING:** O Runner instancia uma `Transaction` com status `PENDING`. Isso materializa a intenção em um registro persistente com preço e quantidade estimados.
* **Roteamento de Lote:** O Runner verifica o `targetLotId` contido no DTO. Se preenchido (UUID), o Runner direciona a ordem para o fechamento de um lote específico (Hedging/Specific Match); se nulo, delega à `AccountingPolicy` (ex: FIFO).
* **Locking (Matching Provisório):** Se for uma ordem de venda (`SHOULD_SELL`), o Runner identifica os lotes de compra (via `targetLotId` ou FIFO) e os marca como **"em processo de fechamento"**. Isso impede que outros sinais concorrentes tentem liquidar os mesmos lotes.
* **Vínculo de Vida:** Todo lock está obrigatoriamente vinculado a uma `TransactionId`. Não existem locks órfãos.
* **Atomicidade:** O processo de busca e marcação de locks ocorre em uma **transação isolada de banco de dados** (Pessimistic Locking no nível da linha do lote) para evitar que dois sinais concorrentes selecionem o mesmo lote no mesmo milissegundo.
* **Sanidade:** Executa o método `validateTradeParams` para garantir que a `confidence` e a `quantity` estejam dentro dos parâmetros operacionais permitidos.


### C. Reserva e Execução (Portfolio & Exchange)

1. **Capital Request:** O Runner solicita ao **Portfolio** a reserva de margem baseada na transação `PENDING`. O Portfolio move o saldo de *Available* para *Reserved*.
2. **Order Dispatch:** Com o estado salvo localmente e a margem garantida, o Runner despacha a ordem para a Exchange utilizando o protocolo de identificação única:
* **Identificação Única (`clientOrderId`):** Utiliza o formato `{r}{runner_short}{t}{timestamp}{s}{sequence}{type}_{transaction_uuid}` (ex: `r01ft1700000000000s001N_8da233214f11b12a`).
* **Validação de Segurança:** O Portfolio atua como *guardrail*, validando se o `runner_short` pertence ao Runner solicitante antes do envio para evitar *spoofing* entre estratégias.

3. **Status SUBMITTED:** Ao receber a confirmação de recebimento da Exchange (OrderID), o Runner atualiza a transação local para o estado `SUBMITTED`, confirmando que a ordem está "em voo".
4. **Transaction Routing (Callback Assíncrono):** O Listener de ordens da exchange captura a execução e a entrega ao **Portfolio**, que atua como o ponto de entrada único. Ele realiza o *parsing* do prefixo do `clientOrderId` para identificar o **StrategyRunner** proprietário e rotear a `Transaction` via evento ou comando, eliminando a necessidade de busca exaustiva no banco de dados e permitindo que o Runner inicie sua contabilidade interna imediatamente.

### D. Reconciliação e Execuções Parciais (Matching & Update)

O Runner altera o status da transação para `FILLED` (ou gerencia o estado `PARTIALLY_FILLED`) e inicia o processamento contábil seguindo estas diretrizes:

1. **Tratamento de Partial Fills (Execuções Parciais):**
* **Contabilidade Incremental:** Para cada evento de execução parcial recebido da Exchange, o Runner registra um `TransactionMatch` proporcional. Isso garante que o PnL seja atualizado em tempo real, mesmo antes da ordem ser 100% completada.
* **Ajuste de Margem Proporcional:** A cada fatia executada, o Runner notifica o **Portfolio** para converter a "Margem Reservada" correspondente em "Margem Realizada".

2. **Efetivação do Match:**
* **Consolidação de Lotes:** Converte os *locks* provisórios criados no passo 4.B em fechamentos definitivos.
* **Identificação de Lote:** Caso o `targetLotId` tenha sido fornecido no sinal original, o match é vinculado obrigatoriamente a este ID; caso contrário, a `AccountingPolicy` (ex: FIFO) determina o destino.

3. **Sincronização Financeira e PnL:**
* **Atualização de Saldo:** O Runner envia o sinal de "Confirmação de Execução" ao Portfolio com o preço e taxas reais.
* **Cálculo de PnL Realizado:** O lucro ou prejuízo é calculado imediatamente após o match e refletido na `Position` da estratégia, atualizando o PnL acumulado.

4. **Tratamento de Exceções e Auditoria:**
* **Dead Letter Queue (DLQ):** Caso o ID retornado pela Exchange seja inválido ou o Runner proprietário não seja localizado pelo Portfolio, a transação é enviada para a DLQ para auditoria manual imediata.
* **Estorno de Margem Remanescente:** Se uma ordem for parcialmente preenchida e o restante cancelado, o Runner instrui o Portfolio a estornar apenas a margem da fatia não executada, garantindo a integridade do saldo.

---

## 5. Fluxos de Falha e Resiliência (Fluxo Reverso)

Para garantir a integridade do Balance e das Positions, o sistema implementa protocolos de rollback e limpeza:

### A. Negação de Margem (Portfolio Reject)

Se o `Portfolio` negar o `Capital Request` (por falta de saldo ou violação de risco global):

* **Ação:** O `StrategyRunner` descarta o sinal da estratégia imediatamente.
* **Notificação:** Um evento de `InsufficientFunds` ou `RiskViolation` é gerado para o log, e a `TradeStrategy` permanece em modo de espera pelo próximo ciclo.

### B. Rejeição da Exchange (Order Rejected)

Se a margem foi autorizada pelo Portfolio, mas a corretora rejeitou a ordem (ex: ativo em leilão, erro de limite):

* **Ação:** O `StrategyRunner` notifica o `Portfolio` sobre a falha.
* **Rollback:** O `Portfolio` realiza o **Estorno da Margem** que havia sido pré-alocada para aquela operação.
* **Cleanup:** Nenhuma `Position` é criada ou alterada no banco de dados.

### C. Expiração ou Cancelamento (Order Expired/Canceled)

Para ordens que foram enviadas mas não executadas (Limit Orders):

* **Cleanup de Matches:** O `StrategyRunner` identifica que a `Transaction` não será concluída e invalida qualquer tentativa de "Match" pendente.
* **Liberação de Margem:** O Runner informa ao `Portfolio` que a ordem foi cancelada, permitindo que a margem reservada retorne ao `Balance` disponível.

---

## 6. Ciclo de Vida da Transação e Resiliência

O sistema utiliza uma máquina de estados rigorosa para garantir que cada centavo reservado seja rastreável, mesmo em caso de falha sistêmica.

### A. Mapa de Estados da Transaction

| Estado                 | Significado                                  | Ação de Margem (Portfolio)                               |
|------------------------|----------------------------------------------|----------------------------------------------------------|
| **PENDING**            | Intenção criada; Lotes travados localmente.  | **Reserva:** Saldo movido para *Reserved*.               |
| **SUBMITTED**          | Ordem aceita pela Exchange (OrderID gerado). | **Mantém:** Saldo continua em *Reserved*.                |
| **PARTIAL**            | Execução parcial (fatia).                    | **Conversão:** Parte da margem vira *Realizada*.         |
| **FILLED**             | Execução 100% concluída.                     | **Efetivação:** Toda margem vira *Realizada*.            |
| **CANCELED / EXPIRED** | Ordem interrompida ou vencida.               | **Estorno:** Margem remanescente volta para *Available*. |
| **REJECTED**           | Exchange recusou a ordem de imediato.        | **Estorno:** Margem total volta para *Available*.        |

### B. Matriz de Recuperação e Falhas (Resiliência)

| Evento de Falha       | Responsável pela Detecção | Ação de Recuperação                                                                                                                               |
|-----------------------|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| **Margem Negada**     | Portfolio                 | Interrompe o fluxo no `Capital Request`. O sinal é descartado antes do envio.                                                                     |
| **Ordem Rejeitada**   | Runner (via Portfolio)    | Transação vai para `REJECTED`. Solicita estorno imediato de margem ao Portfolio.                                                                  |
| **Timeout (No Fill)** | StrategyRunner            | Runner envia comando de cancelamento à Exchange e limpa intenção de match/locks.                                                                  |
| **Queda de Conexão**  | Portfolio (Core)          | Ao reconectar, o Listener sincroniza ordens pendentes. O Portfolio roteia as atualizações para os Runners.                                        |
| **Crash do Sistema**  | Portfolio + Runners       | No reinício, Runners buscam ordens em estado `PENDING/SUBMITTED`. Reconciliam com a Exchange; se não encontradas, limpam estado e liberam margem. |

### C. Responsabilidades de Monitorização

* **Watchdog de Timeouts:** O **StrategyRunner** é o dono do relógio. Se uma ordem não transita de `SUBMITTED` para `FILLED` no tempo previsto pela `ExecutionPolicy`, o Runner dispara o fluxo de cancelamento.
* **Integridade de Locks:** Caso uma transação falhe (Rejected/Canceled), o Runner é responsável por desbloquear os lotes de compra que estavam "travados" para aquela venda.

### D. Protocolo de Recuperação Pós-Crash (Boot Sequence)

Quando o sistema reinicia, ele deve entrar em **"Safe Mode"** antes de retomar as operações. O processo segue uma hierarquia de baixo para cima (Infra → Portfolio → Runner):

#### 1. Orquestração da Recuperação

O processo é **descentralizado**. O `Portfolio` inicializa o estado do caixa, mas cada `StrategyRunner` é responsável por reconciliar suas próprias operações "em voo".

#### 2. Passo a Passo do Boot:

1. **Portfolio - Sincronização de Caixa:** O Portfolio conecta-se às Exchanges para atualizar o `GlobalBalance` (Available/Total). Ele assume que qualquer margem em `Reserved` no banco de dados deve ser reavaliada pelos Runners.
2. **Runner - Identificação do Limbo:** Cada Runner busca no seu `StrategyRunnerRepository` todas as transações nos estados `PENDING`, `SUBMITTED` ou `PARTIAL`.
3. **Runner - Consulta à Exchange:**
   * O Runner usa o `clientOrderId` (que contém o UUID da transação) para consultar o status real na Exchange.
   * **Cenário A (Ordem existe):** O Runner atualiza o status local (ex: de `SUBMITTED` para `FILLED` ou `CANCELED`) e notifica o Portfolio para converter/estornar a margem.
   * **Cenário B (Ordem NÃO existe):** Se a Exchange não reconhece o ID, o Runner assume que o sistema caiu antes do envio ou a Exchange também reiniciou e perdeu o estado. A transação é marcada como `EXPIRED` e a margem é devolvida ao Portfolio.
4. **Runner - Destravamento de Lotes:** Após reconciliar a transação, os locks de lotes associados são limpos conforme a Seção 9.1.

#### 3. Proteção contra Novos Trades (Circuit Breaker)

Enquanto um `StrategyRunner` não finalizar sua reconciliação (Passo 3), ele mantém um flag interno `isReconciling = true`.

* Neste estado, o Runner **rejeita automaticamente** qualquer novo `TradeSignal` da estratégia.
* Isso evita o "Double Spending" de margem e garante que novos trades só ocorram após a limpeza do estado anterior.

---

## 7. Aggregate Boundaries (DDD)

Para garantir escalabilidade e evitar bloqueios excessivos no banco de dados, o sistema divide as responsabilidades em dois Agregados principais:

### A. Aggregate Root: Portfolio

O Portfolio é o mestre financeiro da conta.

* **Entidades Internas:** `Balance`, `MarginAccount`.
* **Responsabilidade:** Garantir a consistência atômica do saldo global. Nenhuma margem é reservada sem que o estado interno do Portfolio seja atualizado.
* **Persistência:** Possui seu próprio repositório (`PortfolioRepository`). Salva apenas dados de saldo e configurações globais de risco.

### B. Aggregate Root: StrategyRunner

O Runner é o mestre operacional de uma estratégia específica.

* **Entidades Internas:** `Position`, `Transaction`, `TransactionMatch`.
* **Value Objects:** `ExecutionPolicy`, `AccountingPolicy`.
* **Responsabilidade:** Garantir a integridade do ciclo de vida das ordens e a acurácia do PnL da estratégia. Ele referencia o `PortfolioId`, mas não altera o saldo diretamente.
* **Persistência:** Possui seu próprio repositório (`StrategyRunnerRepository`). Isso permite salvar milhões de transações de um robô de alta frequência sem travar a tabela de saldos do Portfolio.

### C. Comunicação entre Agregados
O Runner referencia o Portfolio por ID. A sincronização financeira (reserva/estorno de margem, atualização de PnL) ocorre via eventos ou mensagens após o processamento interno do Runner.

A reconciliação financeira segue o modelo de Reserva e Confirmação:

* O **Portfolio** bloqueia o capital (Margem Reservada) no momento do Capital Request.
* O **Runner** processa a execução assíncrona.
* O **Runner** envia um sinal de 'Confirmação de Execução' ao Portfolio, que então converte a 'Margem Reservada' em 'Margem Realizada', ajustando o Balance de acordo com o preço real de execução.

---

## 8. Guia de Migração e Decomposição (Refatoração)

Para transformar a arquitetura atual no modelo desse documento, as responsabilidades do `Portfolio` legado serão redistribuídas conforme o mapeamento abaixo:

| Responsabilidade Atual do Portfolio   | Novo Dono (Destino) | Justificativa Técnica                                                                              |
|:--------------------------------------|:--------------------|:---------------------------------------------------------------------------------------------------|
| **Balance (Available, Allocated)**    | **Portfolio**       | Autoridade sobre o saldo real e reservas globais.                                                  |
| **Margem Reservada (Shadow Balance)** | **Portfolio**       | Mantém o saldo "congelado" enquanto a transação está `PENDING` ou `SUBMITTED`.                     |
| **PnL Consolidado**                   | **Portfolio**       | Visão agregada dos resultados de todos os Runners ativos.                                          |
| **Roteamento de Eventos (Parser)**    | **Portfolio**       | Identifica o Runner proprietário via prefixo do ID e despacha a mensagem.                          |
| **Dead Letter Queue (DLQ)**           | **Portfolio**       | Captura execuções órfãs ou com IDs inválidos para intervenção manual.                              |
| **Transactions + Status Lifecycle**   | **StrategyRunner**  | Gere o ciclo de vida (Pending → Submitted → Filled/Partial) das ordens.                            |
| **TransactionMatches (Matching)**     | **StrategyRunner**  | O matching (FIFO/LIFO/Specific) é uma regra contábil da estratégia.                                |
| **Position (Calculated View)**        | **StrategyRunner**  | A exposição líquida por ativo pertence ao contexto operacional do Runner.                          |
| **Exchange Config (Symbol/Keys)**     | **StrategyRunner**  | Conhece as regras específicas (tick size, min qty) do seu ativo.                                   |
| **Geração de clientOrderId**          | **StrategyRunner**  | Garante a inclusão do `runner_short` e do `transaction_uuid` para roteamento.                      |
| **Locking de Lotes (Provisional)**    | **StrategyRunner**  | Impede que um lote em processo de venda seja usado por outro sinal concorrente.                    |
| **Gestão de Partial Fills**           | **StrategyRunner**  | Controla a contabilidade incremental e solicita ajustes parciais de margem.                        |
| **Watchdog de Timeouts**              | **StrategyRunner**  | O Runner monitora se suas ordens "em voo" estão demorando mais do que o permitido pela estratégia. |
### Notas de Implementação para a Refatoração:

1. **Desacoplamento de Repositórios**: Iniciar pela criação do `StrategyRunnerRepository`, segregando as tabelas de `Positions` e `Transactions` do domínio financeiro do `Portfolio`.
2. **Protocolo de Identificação**: Implementar o Value Object `ClientOrderId` para centralizar a lógica de geração e parsing do ID de 32/36 caracteres.
3. **Atomicidade na Reserva**: A chamada de `Capital Request` deve ser o único ponto de sincronização impeditivo entre os Agregados para garantir integridade de saldo antes do envio à Exchange.

---

### 9. Governança de Locks e Concorrência

Para resolver as brechas de "travamentos infinitos" e disputas de sinais:

#### 9.1. Ciclo de Vida do Lock

O Lock não possui um timer independente, ele herda o destino da Transação:

1. **Liberação por Sucesso:** Quando a Transação atinge `FILLED`, o lock é convertido em um `TransactionMatch` definitivo (baixa no estoque).
2. **Liberação por Falha:** Se a Transação for `REJECTED`, `CANCELED` ou `EXPIRED`, o Runner dispara o gatilho de *Unlock* imediato, devolvendo os lotes ao estado "Disponível".
3. **Timeout de Transação (Watchdog):** Como definido na seção 6.C, o Watchdog cancela ordens travadas. Ao cancelar a ordem, o fluxo de "Liberação por Falha" é ativado, garantindo que nenhum lote fique preso por erro de rede ou software.

#### 9.2. Prevenção de Deadlocks

Para evitar que o sistema trave quando múltiplos sinais chegam simultaneamente:

* **Fila por Runner:** Cada `StrategyRunner` processa seus sinais de forma sequencial ou utiliza controle de concorrência otimista.
* **Fail-Fast em Lotes Presos:** Se um sinal chega solicitando um `targetLotId` que já possui um lock ativo de outra transação, o sinal é rejeitado imediatamente (`Error: LotAlreadyLocked`), evitando esperas circulares.
