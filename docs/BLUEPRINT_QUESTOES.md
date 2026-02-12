# Perguntas Arquiteturais Pendentes — Blueprint

## Não excluir, responder quando finalizar o blueprint
2. Gerar o sumário de entidades detalhado (Atributos e Tipos) para o Guia de Implementação?

---

## 🔍 **GAPS DE SEGUNDA CAMADA (NÃO ÓBVIOS)**

### **GAP #4 — Tratamento de Erros de Conversão de Fees**

**Local:** Seção 10.1

**O que está escrito:**
> "O Portfolio realiza a conversão sintética no momento do TransactionMatch, debitando o valor equivalente do AvailableBalance na moeda base caso o saldo do ativo da taxa seja insuficiente."

**O que falta:**
- **E se a API de preço falhar?** A conversão precisa da taxa de câmbio BNB/USDT no momento da execução.
- **Qual é o fallback?** Usar a última cotação conhecida? Rejeitar a ordem? Mover para DLQ?
- **Quem fornece a taxa de câmbio?** Portfolio consulta uma oracle? Exchange? Preço médio do dia?
- **E se a taxa de câmbio mudar entre a execução e a liquidação?** O débito já foi feito. Há ajuste posterior?

**Classificação:** **Decisão arquitetural implícita.**  
**Impacto:** Risco de saldo incorreto se a conversão falhar ou usar preço defasado.

RESPOSTA:

Filosofia de Resiliência Contábil: O Protocolo de Dívida Técnica
No desenvolvimento de um sistema de trading de alta disponibilidade, partimos do princípio de que falhas externas (APIs, Oráculos de Preço e Latência) são inevitáveis, mas a inconsistência de dados é inaceitável.

A nossa abordagem para o tratamento de taxas (Fees) e resíduos (Dust) baseia-se em três pilares fundamentais:

1. A Primazia do Fato Gerador sobre a Estimativa
   Quando uma exchange executa uma ordem e cobra uma taxa em um ativo secundário (como BNB), o sistema prioriza o registro do dado bruto e imutável. Se no momento da execução o serviço de precificação estiver indisponível para realizar a "Conversão Sintética" (transformar a taxa em USDT), o sistema não deve tentar "adivinhar" o preço ou travar a execução. Em vez disso, ele registra a Dívida Técnica de Ativo: o valor exato cobrado pela corretora, acompanhado do timestamp preciso da transação. Isso garante que o fato financeiro real seja capturado sem distorções causadas por falhas de infraestrutura.

2. O Isolamento do Capital de Giro (Margem)
   Para garantir que o robô nunca perca o poder de execução, separamos o Saldo Operacional (USDT) da Conta de Resíduos (DustAccount).

Se uma conversão de taxa falha, o débito fica "pendente" na DustAccount.

Isso impede que o sistema tome decisões baseadas em um saldo de USDT "mascarado" por uma conversão mal sucedida.

O patrimônio líquido (Equity) permanece transparente: o operador vê exatamente quanto tem de dólar e quanto possui de "dívida ou saldo em pó" a ser processado.

3. Reconciliação Histórica Determinística
   Diferente de sistemas que tentam corrigir erros com preços atuais, nossa arquitetura exige a Rastreabilidade Temporal. Como armazenamos o momento exato (timestamp) da falha de conversão, permitimos que um processo de fundo (Worker de Reconciliação) atue de forma cirúrgica. Ele não pergunta "quanto vale o BNB agora?", mas sim "quanto valia o BNB no milissegundo em que este trade aconteceu?". Isso elimina o drift (derivação) financeiro, garantindo que, mesmo que a contabilidade seja finalizada horas depois, o lucro líquido (PnL) da estratégia seja idêntico ao que seria se o sistema estivesse 100% online no momento do trade.


---

### **GAP #5 — Consistência da "Conta de Pó" (Dust Account)**

**Local:** Seção 10.2.C

**O que está escrito:**
> "O Runner deve marcar o lote como Totalmente Fechado e enviar o resíduo para uma conta de 'Ajuste de Arredondamento' no Portfolio."

**O que falta:**
- **Essa conta existe onde?** É uma entrada contábil no `GlobalBalance`? Uma subconta? Um ativo separado?
- **O resíduo pode ser reutilizado?** Ou é perda permanente?
- **Quem audita o acúmulo de pó?** Se milhares de trades gerarem micro-resíduos, isso pode somar valor relevante.
- **Há um processo de "limpeza" do pó?** Ex: venda automática quando acumular X, ou doação contábil?

**Classificação:** **Especificação incompleta.**  
**Impacto:** Implementações ad-hoc, inconsistência entre Runners.

---

---

### **GAP #9 — Limpeza de Locks em Crash Durante Lock**

**Local:** Seção 9.1 + Seção 6.D

**O que está escrito:**
> "Todo lock está obrigatoriamente vinculado a uma TransactionId. Não existem locks órfãos."

**O que está coberto:**
- Liberação por sucesso (FILLED)
- Liberação por falha (REJECTED/CANCELED/EXPIRED)
- Timeout via Watchdog

**O que ainda falta:**
- **Cenário: Runner adquire lock, persiste transação, e CRASHA antes de fazer Capital Request.**
- O lock está no banco, a transação está `PENDING`, mas o Runner morreu.
- O Watchdog do Runner não roda (Runner está morto).
- **Quem limpa esse lock?** O Boot Sequence (6.D) resolve, mas:
    - Precisa ser explícito: "Locks de transações PENDING sem ordem na Exchange são liberados no reboot."
    - Atualmente está implícito (6.D.2.B + 9.1.2).

**Classificação:** **Implícito, deveria ser explícito.**  
**Impacto:** Baixo, mas pode gerar dúvida no implementador.

---

### **GAP #10 — Consistência entre MarginAccount e GlobalBalance**

**Local:** Seção 12.2.C

**O que está escrito:**
> "MarginAccount rastreia o MaintenanceMargin e o LiquidationPrice."

**O que falta:**
- **Como o Portfolio atualiza o MaintenanceMargin em tempo real?**
- O preço do ativo oscila, a margem de manutenção sobe/desce.
- O Portfolio precisa de um **worker de precificação** ou ouvir WebSocket de mark price.
- **Isso é responsabilidade do Portfolio ou do ExchangeAdapter?**
- **A atualização é síncrona (bloqueia novos trades) ou assíncrona?**

**Classificação:** **Funcionalidade não modelada.**  
**Impacto:** Risco de margem insuficiente não detectada até o Capital Request.

---

### **GAP #11 — Cancelamento em Massa (Kill Switch)**

**Local:** Seção 12.1 (Circuit Breaker)

**O que está escrito:**
> "Intervenção Manual (Override): operador pode forçar o estado de 'Safe Mode'."

**O que falta:**
- **O que "Safe Mode" faz exatamente?**
    - Apenas rejeita novos sinais?
    - Ou também tenta cancelar ordens SUBMITTED?
    - Ou tenta fechar posições abertas?
- **Há um comando de "Panic Sell" separado?**
- **Esse comando é assíncrono?** O operador precisa esperar todas as ordens serem canceladas?
- **Como garantir que o kill switch não seja acionado acidentalmente?**

**Classificação:** **Especificação incompleta.**  
**Impacto:** Em emergência real, comportamento indefinido.

---

### **GAP #12 — Tratamento de Exchanges sem clientOrderId**

**Local:** Nenhuma. **Ausente por decisão de escopo.**

**O que está no ROADMAP_QUESTOES.md:**
> "O modelo atual pressupõe que todas as exchanges implementam clientOrderId. E se alguma não suportar?"

**O que falta no BLUEPRINT:**
- **Uma nota explícita:** "Este blueprint assume exchanges que suportam clientOrderId. Exchanges sem este recurso não são suportadas na v1."
- Atualmente isso não está documentado. Quem ler o Blueprint pode assumir suporte universal.

**Classificação:** **Premissa não documentada.**  
**Impacto:** Expectativa incorreta sobre o escopo do sistema.


