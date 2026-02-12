# Perguntas Arquiteturais Pendentes — Blueprint

## Não excluir, responder quando finalizar o blueprint
2. Gerar o sumário de entidades detalhado (Atributos e Tipos) para o Guia de Implementação?

---

## 🔍 **GAPS DE SEGUNDA CAMADA (NÃO ÓBVIOS)**

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


