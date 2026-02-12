# Questões Pendentes — Implementation Guide

## 3. Rate Limiting da Exchange

**Pergunta:** Como o sistema gerencia os limites de requisição (rate limits) impostos pela exchange quando múltiplos StrategyRunners compartilham a mesma API key?

**Detalhamento necessário:**
- Rate limits são por conexão WebSocket ou por API key REST?
- Como priorizar ordens sobre requests de market data?
- O sistema precisa saber o "weight" de cada request (ex: Binance)?
- **Implicação:** Define se precisa de um `ExchangeRateLimiter` centralizado ou se cada adapter gerencia seus próprios limites.

**Origem:** BLUEPRINT_QUESTOES.md #3

---

## Migração do Tópico 8 do Blueprint

**Ação necessária:** Mover o conteúdo da seção "8. Guia de Migração e Decomposição (Refatoração)" do BLUEPRINT_V9.md para este documento (Implementation Guide).

**Justificativa:**
- O Blueprint define **o quê** a arquitetura é e **por quê** ela existe — não **como** implementá-la.
- O Guia de Migração descreve passos práticos de refatoração (como decompor o Portfolio atual), o que é responsabilidade do Implementation Guide.
- Após a migração, a seção 8 deve ser removida do Blueprint.

**Origem:** Revisão arquitetural do BLUEPRINT_V9.md

---

## Comunicação entre Agregados — Detalhes Técnicos

**Pergunta:** Como exatamente ocorre a comunicação Portfolio ↔ Runner?

**Detalhamento necessário:**
- São eventos síncronos ou assíncronos?
- Qual garantia de entrega? (at-least-once, exactly-once)
- Há retry mechanism? Timeout?
- Em caso de falha na comunicação, quem é responsável pela reconciliação?
- **Implicação:** Define o padrão de comunicação entre agregados (método direto, eventos de domínio, mensageria).

**Origem:** QUESTOES_PENDENTES.md #2

---

## Consistência de Dados entre Agregados

**Pergunta:** Como garantir consistência entre os dois agregados (Portfolio e StrategyRunner)?

**Detalhamento necessário:**
- Se o Portfolio atualiza o Balance, os Runners são notificados em tempo real?
- Há eventual consistency? Se sim, qual o window máximo aceitável?
- Qual o impacto de um Runner tomar decisão com saldo desatualizado?
- **Implicação:** Define o modelo de consistência (strong vs eventual) e os mecanismos de propagação de estado.

**Origem:** QUESTOES_PENDENTES.md #4

---

## Validação de Trade Parameters — Níveis e Falha

**Pergunta:** Quais são os limites de validação de trade params e quem os define?

**Detalhamento necessário:**
- Esses parâmetros são configurados por Runner, por estratégia ou globalmente?
- Há diferentes níveis de validação (Runner, Portfolio, Exchange)?
- O que acontece se a validação falhar? Retry, alerta ou descarte silencioso?
- **Implicação:** Define a cadeia de validação e o tratamento de sinais inválidos.

**Origem:** QUESTOES_PENDENTES.md #11

---

## Context Injection para Strategy

**Pergunta:** O que exatamente compõe o "contexto" injetado no motor de sinais?

**Detalhamento necessário:**
- Apenas dados de mercado?
- Estado atual do Runner (posições abertas, PnL, histórico)?
- Configurações e limites?
- A estratégia tem acesso ao saldo disponível?
- **Implicação:** Define o contrato do DTO de contexto e o acoplamento entre Runner e Strategy.

**Origem:** QUESTOES_PENDENTES.md #12

---

## Cooldown Implementation

**Pergunta:** Como funciona exatamente o cooldown entre operações?

**Detalhamento necessário:**
- Cooldown é por símbolo, por direção ou global do Runner?
- É resetado após cada ordem ou após cada Position fechada?
- Há diferentes cooldowns para diferentes tipos de operação?
- **Implicação:** Define a granularidade e o ciclo de vida do cooldown dentro da Execution Policy.

**Origem:** QUESTOES_PENDENTES.md #13

---

## Testing Strategy

**Pergunta:** Como testar essa arquitetura com dois agregados e comunicação assíncrona?

**Detalhamento necessário:**
- Mocks para Portfolio em testes de Runner?
- Testes de integração entre agregados?
- Simulação de falhas de rede entre componentes?
- Testes de carga com múltiplos Runners?
- **Implicação:** Define a estratégia de testes unitários, de integração e de resiliência.

**Origem:** QUESTOES_PENDENTES.md #22

---

## Sincronia de Relógio (Clock Drift)

**Pergunta:** Como o sistema garante a ordem cronológica de eventos quando o servidor e a Exchange apresentam desvios de milissegundos?

**Detalhamento necessário:**
- Se o timestamp do sistema (usado no `clientOrderId`) estiver à frente do timestamp da Exchange, a ordem pode ser rejeitada?
- Como lidar com eventos de execução que chegam via WebSocket em ordem diferente da que ocorreram na Exchange?
- **Implicação:** Define a necessidade de um `TimeService` que sincronize com o NTP da Exchange ou use o `serverTime` da API como referência.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #14


## Limites de Recursos por Runner (Hard/Soft Limits)

**Pergunta:** O teto de capital por Runner (Max Allocation) cobre apenas margem, ou também número de posições e ordens?

**Detalhamento necessário:**
- Ordens `SUBMITTED` contam para o limite de posições?
- Quem valida esses limites? Portfolio no `Capital Request`, ou Runner na `Execution Policy`?
- O limite é dinâmico? Pode ser alterado em tempo real sem reiniciar o Runner?
- Há soft limit com alerta, ou só hard limit com bloqueio?
- **Implicação:** Runners podem consumir recursos além do desejado se apenas margem for limitada.

**Origem:** BLUEPRINT_QUESTOES.md — GAP #6 (movido para Implementation Guide)

---

## Ordem de Processamento Concorrente no Portfolio

**Pergunta:** Como garantir a ordem FIFO de `Capital Requests` em ambiente concorrente?

**Detalhamento necessário:**
- Se 10 Runners chamam `requestCapital` simultaneamente, quem chega "primeiro"?
- O lock do Portfolio é por método ou por transação de banco de dados?
- Há risco de starvation se um Runner for muito lento e outro muito rápido?
- E se um Runner fizer 1000 requests por segundo? Ele domina a fila?
- **Implicação:** Define o mecanismo de serialização (lock otimista, pessimista, fila).

**Origem:** BLUEPRINT_QUESTOES.md — GAP #7 (movido para Implementation Guide)

---

## Testabilidade do Protocolo de Reconciliação

**Pergunta:** Como testar o Boot Sequence (6.D) e cenários de reconciliação em ambiente de desenvolvimento?

**Detalhamento necessário:**
- Como simular uma Exchange que "perdeu" o estado de uma ordem (ordem fantasma)?
- Como testar o cenário de crash entre persistência e dispatch?
- O MockExchangeAdapter precisa de modos de falha configuráveis?
- Testes de integração devem cobrir todos os cenários da tabela de reconciliação (11.3.C)?
- **Implicação:** Define os modos de falha do mock e a cobertura mínima de testes de resiliência.

**Origem:** BLUEPRINT_QUESTOES.md — GAP #13 (movido para Implementation Guide)

---

## Notas de Implementação — Idempotência e Protocolo de Envio

Requisitos técnicos derivados das seções 11.1 e 6.D do Blueprint.

---

#### **1. Persistência Atômica "Pre-Flight"**

* **Requisito**: A transação deve ser salva no banco de dados com status `PENDING` **antes** de qualquer chamada de rede para a Exchange.
* **Implementação**: Utilizar uma transação de banco de dados que englobe:
1. A criação do registro de `Transaction`.
2. A geração do `clientOrderId` único.
3. A reserva de margem no `Portfolio`.

* **Ponto de Atenção**: Se a persistência falhar, o processo deve abortar sem disparar o `Order Dispatch`.

---

#### **2. Implementação do Value Object `ClientOrderId**`

* **Formato**: `{r}{runner_short}{t}{timestamp}{s}{sequence}{type}_{transaction_uuid}`.
* **Constraint**: O `transaction_uuid` (parte final do ID) deve ser o elo imutável entre o banco de dados e a Exchange.
* **Lógica de Parsing**: O `Portfolio` deve implementar um parser para este ID para rotear callbacks de volta ao Runner proprietário sem consultar o banco (usando o `runner_short`).

---

#### **3. Estratégia de Retry e Timeout**

* **No Acknowledgment**: Se uma chamada de API retornar *Timeout* ou *Connection Closed*, o sistema **não deve** reenviar a ordem imediatamente.
* **Fluxo de Recuperação**:
1. Marcar a transação local como `SUBMITTED` (assumindo que "pode" ter chegado na exchange).
2. Disparar uma consulta (GET Order) usando o `clientOrderId` para verificar se a ordem existe no Order Book da corretora.
3. Somente se a corretora retornar "Order Not Found", a transação local pode ser invalidada para permitir um novo sinal.

---

#### **4. Boot Sequence (Safe Mode)**

* **Verificação de Limbo**: Ao iniciar, o Runner deve buscar todas as ordens em estado `PENDING` ou `SUBMITTED` no banco de dados.
* **Sincronização Obrigatória**: Para cada ordem encontrada, o Runner deve consultar a Exchange via `clientOrderId`:
* **Existe**: Atualiza o banco para o status real (Filled/Canceled).
* **Não Existe**: Marca como `EXPIRED` localmente e libera a margem no `Portfolio`.


* **Bloqueio de Novos Sinais**: O flag `isReconciling` deve permanecer `true` até que todas as ordens no "limbo" sejam resolvidas.

---

## Notas de Implementação — Fees e Cancelamento Parcial

Requisitos técnicos derivados das seções 10.1 e 11.2 do Blueprint.

---

#### **5. Gestão de Fees (Taxas) Proporcionais e Cross-Currency**

* **Captura via Evento**: O listener de execução deve extrair o objeto `fee` (amount e asset) diretamente do payload da Exchange no momento do evento `PARTIAL` ou `FILLED`.
* **Cálculo de PnL Líquido**: A fórmula de atualização da `Position` deve ser: `PnL_Liquido = (Preço_Execução - Preço_Abertura) * Quantidade - Fees_Acumuladas`.
* **Conversão de Moeda (Fee Asset)**: Como lidar com taxas pagas em ativos diferentes (ex: BNB)?
  * O sistema deve converter o valor da taxa para a moeda base do Portfolio para fins de balanço, ou manter o saldo negativo no ativo da taxa até a reconciliação?
  * Em qual momento a conversão ocorre? No instante da execução ou no fechamento do dia?
  * Quem é responsável por obter a taxa de câmbio atual (Runner ou Portfolio)?
  * Como registrar contabilmente um ativo que está sendo consumido como taxa?
  * O que acontece se não houver saldo suficiente no ativo da taxa?
* **Sincronização com Portfolio**: O comando de confirmação de execução enviado ao Portfolio deve conter obrigatoriamente o par `(amount, asset)` da taxa para dedução imediata do `GlobalBalance`.

---

#### **6. Máquina de Estados: Transição Parcial para Cancelado**

* **Estado Terminal Híbrido**: Definir se usaremos um status único `PARTIALLY_FILLED_CANCELED` ou se manteremos `CANCELED` com um flag `hasExecutions: true` para sinalizar que houve movimentação financeira.
* **Cálculo de Estorno (Refund)**: A lógica de liberação de margem no Portfolio após um cancelamento parcial deve ser: `Estorno = Margem_Reservada_Original - (Quantidade_Executada * Preço_Real_Execução)`.
* **Impacto no Preço Médio**: Garantir que a `Position` ignore o volume cancelado. O `averagePrice` deve ser recalculado apenas com o que foi efetivamente registrado no `TransactionMatch`.

---

#### **7. Governança de Locks em Cancelamentos**

* **Liberação de Inventário**: Se uma ordem de venda parcial for cancelada, os lotes de compra (Buy Lots) que estavam travados (locked) para a fatia não executada devem ser desbloqueados instantaneamente.
* **Atomicidade**: O processo de mudar a transação para `CANCELED` e desbloquear os lotes deve ocorrer dentro da mesma transação de banco de dados para evitar "lotes fantasmas".

---

## Notas de Implementação — Reconciliação e Boot Sequence

Requisitos técnicos derivados das seções 6.D e 11.3 do Blueprint.

---

#### **8. O Algoritmo de "Sanity Check" no Boot**

* **Requisito:** Antes de liberar o `isReconciling`, o Runner deve comparar o `GlobalBalance` reportado pela Exchange com o `Available + Reserved` do `Portfolio` local.
* **Desvio Permitido:** Definir uma margem de erro decimal (ex: 1e-8) para ignorar divergências desprezíveis de arredondamento.
* **Bloqueio Hard:** Se a Exchange reportar menos saldo do que o `Portfolio` julga ter (sem ordens em voo), o `Circuit Breaker` deve ser ativado imediatamente.

#### **9. Identificação de "Zumbis" da Exchange**

* **Problema:** E se existir uma ordem na Exchange que o sistema local **não possui** (ex: ordem feita manualmente via app da corretora)?
* **Implementação:** O `Portfolio` (Core) deve listar todas as ordens abertas na Exchange durante o boot. Qualquer ordem cujo `clientOrderId` não siga o padrão `{r}{runner_short}...` ou não conste no banco deve ser enviada para auditoria (DLQ) para evitar que consuma margem "invisível".

#### **10. Timestamp de Corte**

* **Critério:** Durante a reconciliação, o sistema deve ignorar ordens da Exchange criadas **antes** do início do histórico local do Runner para evitar processar trades antigos de outras sessões.

---

## Notas de Implementação — Circuit Breaker e Defesa de Capital

Requisitos técnicos derivados da seção 12.1 do Blueprint.

---

#### **11. Ponto de Injeção de Parada (Gatekeeper)**

* **Requisito:** A verificação do estado do Circuit Breaker deve ser a primeira instrução dentro do método `requestCapital` no Portfolio.
* **Lógica:** `if (portfolio.isHalted()) throw new RiskViolationException();`.

#### **12. Persistência do Estado de Alerta**

* **Requisito:** O estado de "Halted" do Portfolio deve ser persistido no banco de dados para que um restart do sistema não "esqueça" que o circuit breaker foi atingido.

#### **13. Monitoramento de "Kill-Switch" Externo**

* **Implementação:** O sistema deve ouvir um tópico de mensagens ou possuir um endpoint de saúde que, se sinalizado, aciona o cancelamento em massa (Panic Sell / Cancel All) em caso de emergência externa.

---

## Notas de Implementação — Alavancagem e Margem

Requisitos técnicos derivados da seção 12.2 do Blueprint.

---

#### **14. Sincronização de Alavancagem na Exchange**

* **Requisito:** Antes de enviar a primeira ordem de um Runner, o sistema deve garantir que o comando `SET_LEVERAGE` foi executado com sucesso na Exchange para o símbolo correspondente.
* **Implementação:** Adicionar um passo de "Handshake de Risco" no boot do Runner para configurar o modo de margem (Isolated vs Cross) e o nível de alavancagem.
* **Detecção de Alteração Externa:** Se um administrador altera a alavancagem diretamente no site da Exchange, o sistema detecta?
* **Persistência:** A alavancagem é estado do Runner ou do Portfolio?
* **Rejeição:** Qual é o fluxo se a Exchange rejeitar uma tentativa de alteração de alavancagem?

#### **15. Monitoramento de Margem de Manutenção**

* **Desafio:** O valor da margem necessária muda conforme o preço do ativo oscila (Mark Price).
* **Solução:** O `Portfolio` deve possuir um worker de background (ou ouvir websockets de conta) que atualiza o `MarginUsage` global em tempo real. Se o uso de margem ultrapassar 90%, o Circuit Breaker Global deve impedir novas aberturas.

#### **16. Arredondamento de Margem (Safety Buffer)**

* **Requisito:** No `Capital Request`, o Portfolio deve sempre reservar uma pequena porcentagem a mais (ex: 1.01 * Margem_Calculada) para cobrir variações de preço entre o envio e a execução (slippage), evitando rejeições por "insufficient margin" na Exchange.

---

## Notas de Implementação — Precisão Decimal e Arredondamento

Requisitos técnicos derivados da seção 10.2 do Blueprint.

---

#### **17. Centralização da RoundingPolicy**

* **Requisito:** Criar um serviço ou Value Object `AssetFormat` que receba a `quantity` bruta e retorne a `quantity` formatada conforme o `stepSize` da Exchange.
* **Teste Unitário Obrigatório:** Validar se `0.1 + 0.2` resulta rigorosamente em `0.3`, e não em `0.30000000000000004` (erro clássico de Double).

#### **18. Conversão de Tipos na I/O**

* **Entrada (API/WS):** Converter strings da Exchange para `Decimal` imediatamente no recebimento.
* **Saída (JSON):** Converter `Decimal` para string na saída para garantir que a Exchange receba o número de casas decimais exato, sem notação científica.

#### **19. Validação de `minNotional**`

* **Desafio:** Além da precisão, as exchanges exigem um valor mínimo total (ex: 5 USDT).
* **Implementação:** O Runner deve validar se `(Quantidade_Arredondada * Preço) > minNotional` antes de iniciar o `Capital Request`.

---

## Notas de Implementação — Alocação de Capital e Governança de Concorrência

Requisitos técnicos derivados da seção 12.3 do Blueprint.

---

#### **20. Atomicidade no `requestCapital**`

* **Requisito:** O método de reserva de margem deve ser **Thread-Safe**. Em implementações Java/C#, usar `synchronized` ou `locks` semafóricos. Em Node.js, garantir que a operação de `check-and-reserve` seja atômica no banco de dados (ex: `UPDATE Balance SET available = available - X WHERE available >= X`).

#### **21. Ordem de Processamento de Sinais**

* **Desafio:** Se dois Runners recebem sinais ao mesmo tempo, quem chega primeiro ao Portfolio?
* **Solução:** A latência de rede interna e a velocidade de processamento do Runner determinam a ordem. Não deve haver lógica de "favorecimento" no nível de transporte.

#### **22. Monitoramento de "Rejection Rate"**

* **Implementação:** O Portfolio deve registrar quantas vezes cada Runner teve capital negado. Taxas altas de rejeição indicam que o Runner está "mal calibrado" para o tamanho da conta ou que o limite por Runner está muito baixo.

---

## Questões Adicionais de Implementação

---

## Tratamento de Ordens Pós-Mercado (Expiração)

**Pergunta:** Como o sistema implementa a detecção e o tratamento de ordens expiradas?

**Localização atual:** Blueprint seções 5.C, 6.A (estado EXPIRED) e 6.C (Watchdog)

**Detalhamento necessário:**
- Quem detecta que uma ordem expirou? O Watchdog do Runner ou um evento da Exchange?
- Qual é a diferença entre CANCELED (intencional) e EXPIRED (tempo esgotado) no impacto ao PnL?
- Há um tempo máximo configurável para ordens ficarem em SUBMITTED?
- O que acontece com a estratégia após uma expiração? Ela é notificada?
- **Implicação:** Define o mecanismo de detecção de expiração e o fluxo de notificação.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #12

---

## Precisão e Arredondamento na Camada de Apresentação

**Pergunta:** Como os valores são formatados para exibição em APIs, interfaces e relatórios?

**Localização atual:** Blueprint seção 10.2 (focado na borda com Exchange, não na apresentação)

**Detalhamento necessário:**
- O arredondamento para exibição é diferente do arredondamento para execução?
- Como evitar que o usuário veja valores como "0.0000000001 BTC" por acúmulo de erros de precisão?
- Há truncamento em relatórios e exports?
- **Implicação:** Define a política de formatação na camada de apresentação (REST API, UI).

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #14


## Notas de Implementação — Comunicação entre Agregados

Requisitos técnicos derivados da seção 7.C do Blueprint.

---

#### **23. Interface de Comunicação**

* **Requisito:** O `Portfolio` deve expor uma interface `CapitalManager` com métodos síncronos (`reserve`) e assíncronos (`confirmExecution`, `release`).
* **Implementação:** Em arquiteturas monolíticas, usar um `EventBus` em memória (como o do Spring ou MediatR). Em microserviços, usar RabbitMQ ou Kafka com o padrão **Outbox Pattern** para garantir que a atualização da Transação e o disparo do evento ocorram na mesma transação de banco de dados.

#### **24. Idempotência no Portfolio**

* **Regra:** O Portfolio deve rastrear o status da margem por `transaction_uuid`. Se receber dois eventos de execução para o mesmo ID, o segundo deve ser descartado silenciosamente para evitar dupla liquidação.

## Notas de Implementação — Concorrência e Processamento do Runner

Requisitos técnicos derivados da seção 13 do Blueprint.

---

#### **25. Implementação da Mailbox**

* **Requisito:** Utilizar um padrão de Actor (ex: Akka, Proto.Actor) ou uma fila `Channel` (Go/C#) com limite de 1 elemento.
* **Ação:** O processador deve usar um `TryEnqueue`. Se falhar (fila cheia), logar como `SignalDiscardedByCongestion`.

#### **26. Monitoramento de Backpressure**

* **Métrica:** Registrar o tempo de processamento de cada sinal (do recebimento à persistência do `SUBMITTED`).
* **Alerta:** Se o tempo médio de processamento exceder o intervalo de geração de sinais da estratégia, o Circuit Breaker deve sugerir a revisão da lógica da estratégia ou infraestrutura.

#### **27. Lock de Interface (UI/Admin)**

* **Regra:** Comandos manuais enviados via Portfolio/Admin (ex: Force Cancel) têm **prioridade máxima** e devem "furar a fila" ou interromper o processamento do sinal atual para garantir a segurança do capital.

## Notas de Implementação — Ciclo de Vida do Runner

Requisitos técnicos derivados da seção 14 do Blueprint.

---

#### **28. Factory de Runners**

* **Requisito:** Implementar um `RunnerFactory` que valide as permissões do `Portfolio` antes de instanciar o Runner (ex: o Portfolio tem limite para mais um robô?).
* **Implementação:** O Runner deve receber suas políticas via construtor para garantir imutabilidade durante a execução.

#### **29. Soft Delete vs Archive**

* **Regra:** Nunca deletar um registro de `StrategyRunner` do banco de dados. Usar um campo `archived_at` para garantir que o histórico de `Transactions` e `PnL` permaneça íntegro para relatórios fiscais e de performance.

#### **30. Health Check de Ativação**

* **Verificação:** Ao passar para `ACTIVE`, o Runner deve obrigatoriamente realizar um `ping` na API da Exchange e validar o `tickSize` do ativo. Se falhar, o status deve retroceder para `HALTED` com erro de configuração.


## Notas de Implementação — Preço Médio e Posição

Requisitos técnicos derivados da seção 10.3 do Blueprint.

---

#### **31. Precisão no Cálculo Ponderado**

* **Requisito:** O cálculo intermediário (`Preço * Quantidade`) deve usar precisão estendida (ex: 18 casas decimais) antes do arredondamento final para evitar a perda de centavos em posições massivas.

#### **32. Sincronização de Cache**

* **Implementação:** Se o sistema usar um cache em memória para os Runners, o `averagePrice` no cache deve ser invalidado ou atualizado imediatamente após a escrita no banco de dados para evitar que a estratégia tome decisões baseadas em um custo médio defasado.

#### **33. Tratamento de "Reset" de Posição**

* **Regra:** Quando a quantidade da posição chega a zero, o `averagePrice` deve ser zerado ou setado para `null`. O histórico de preço médio daquela operação deve ser movido para a entidade de `TradeHistory`.

