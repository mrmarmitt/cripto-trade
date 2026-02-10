# Perguntas Arquiteturais Pendentes — Blueprint

## **1. TAXAS (FEES) - Modelagem de Domínio**
**Pergunta:** Como o sistema modela e contabiliza as taxas cobradas pela exchange? Elas impactam apenas o PnL da estratégia, apenas o Balance global, ou ambos?

**Detalhamento necessário:**
- Onde as fees são capturadas no fluxo? (No momento da execução? No callback da exchange?)
- Como são armazenadas? (Valor absoluto? Percentual? Moeda da fee?)
- Impacto no cálculo de PnL: O PnL bruto vs líquido é conceito do domínio?
- Impacto no Balance: As fees são deduzidas do saldo disponível imediatamente ou contabilizadas separadamente?
- **Implicação arquitetural:** Define se `Fee` é um Value Object, se entra no `TransactionMatch`, e se o Portfolio precisa de um campo `totalFeesPaid`.

---

## **2. IDEMPOTÊNCIA - Protocolo de Comunicação**
**Pergunta:** Qual o protocolo para garantir que operações não sejam duplicadas em cenários de retry (rede instável, timeouts, restart do sistema)?

**Detalhamento necessário:**
- O `clientOrderId` é suficiente como chave de idempotência na exchange?
- E se o sistema crashar entre "gerar clientOrderId" e "persistir Transaction como SUBMITTED"?
- Como lidar com o caso onde a exchange aceitou a ordem mas o acknowledgment se perdeu?
- **Implicação arquitetural:** Define a necessidade de um `IdempotencyStore` (Redis?) e o padrão de reconciliação (idempotent receiver).

---

## **5. CANCELAMENTO DE ORDENS PARCIAIS - Edge Case Comum**
**Pergunta:** Quando uma ordem é parcialmente executada e o restante é cancelado, como o sistema modela essa transação do ponto de vista contábil e de estados?

**Detalhamento necessário:**
- Uma Transaction pode ter múltiplos status? (ex: PARTIALLY_FILLED e depois CANCELED)
- Como calcular o preço médio (average price) da posição nesse caso?
- As fees da parte cancelada são cobradas?
- **Implicação arquitetural:** Define se o estado da Transaction é um estado atômico ou uma composição de sub-estados.

---

## **6. FONTE DA VERDADE NA RECONCILIAÇÃO - Filosofia de Recuperação**
**Pergunta:** Durante o processo de reconciliação pós-crash, quando há divergência entre o estado local e o estado na exchange, qual é considerada a fonte da verdade?

**Detalhamento necessário:**
- Quais critérios determinam se confiamos mais no estado local ou no da exchange? (timestamp, quantidade de confirmações, etc.)
- Como lidar com o caso onde a exchange também reiniciou e perdeu estado?
- Qual o protocolo para "ordens fantasmas" (existem localmente mas não na exchange)?
- **Implicação arquitetural:** Define o algoritmo de reconciliação e o nível de agressividade (auto-correção vs intervenção manual).

---

## **10. CIRCUIT BREAKER GLOBAL - Defesa do Capital**
**Pergunta:** Que mecanismos existem para proteger o capital global em caso de comportamento anômalo do sistema ou do mercado?

**Detalhamento necessário:**
- Com base em quais métricas o sistema deve parar automaticamente? (drawdown máximo, número de rejeições consecutivas, etc.)
- Quem tem autoridade para acionar o circuit breaker? (Portfolio automático, intervenção manual?)
- Após acionado, qual o protocolo de retomada?
- **Implicação arquitetural:** Define o sistema de monitoramento global e os pontos de injeção para parada de emergência.

---

## **12. MODELAGEM DE LEVERAGE/MARGIN - Domínio Complexo**
**Pergunta:** Como o sistema modela operações com alavancagem? A margem é um conceito do Portfolio, do Runner, ou de ambos?

**Detalhamento necessário:**
- Como calcular a margem necessária para uma ordem com leverage?
- Quem valida os limites de leverage por símbolo?
- Como lidar com margin calls (se a exchange fornecer essa informação)?
- **Implicação arquitetural:** Define se `MarginAccount` é uma entidade separada e como ela se relaciona com `Balance`.

---

## **13. PRECISÃO DECIMAL E ARREDONDAMENTO (Rounding Policy)**
**Pergunta:** Como o sistema lida com a precisão decimal divergente entre Ativos, Corretoras e Linguagem de Programação (ex: Double vs BigDecimal)?

**Detalhamento necessário:**
- Se a estratégia manda comprar `0.0012345678` BTC, mas a Exchange só aceita 6 casas decimais, onde ocorre o truncamento?
- Como evitar que sobras de "pó" (dust) fiquem presas em lotes devido a arredondamentos divergentes no momento do match?
- **Implicação arquitetural:** Define se o sistema usará uma biblioteca de alta precisão em todo o domínio e onde reside a `RoundingPolicy`.

---

## **18. ALOCAÇÃO DE CAPITAL ENTRE RUNNERS CONCORRENTES**
**Pergunta:** Como o Portfolio aloca capital quando múltiplos Runners solicitam margem simultaneamente?

**Detalhamento necessário:**
- Política de alocação: FIFO (ordem de chegada), por prioridade, proporcional?
- Possibilidade de starvation de alguns Runners?
- Necessidade de "capital pool" reservado por Runner?
- **Implicação arquitetural:** Define o comportamento do Portfolio sob contenção e a política de fairness entre Runners.

---

## **PRIORIZAÇÃO SUGERIDA:**

### **Críticas para V1** (responder antes de codificar):
1. Taxas (Fees) - Impacta modelo de domínio
2. Idempotência - Impacta robustez
5. Cancelamento parcial - Edge case comum
6. Fonte da verdade - Impacta recuperação

### **Importantes para V1.5** (pode ser refinado durante implementação):
10. Circuit breaker
12. Modelagem de leverage
