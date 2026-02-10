# Questões Pendentes — Configuration Reference

## 4. Validação Exchange-Specific

**Pergunta:** Onde reside o conhecimento das regras específicas da exchange (minQty, stepSize, tickSize, minNotional) e quem é responsável por validá-las antes do envio de uma ordem?

**Detalhamento necessário:**
- Essas regras são configuradas estaticamente ou buscadas dinamicamente da API da exchange?
- O que acontece se uma regra mudar durante uma sessão de trading?
- Validação acontece no StrategyRunner, no ExchangeAdapter, ou em ambos?
- **Implicação:** Define a necessidade de um `ExchangeRulesRegistry` e o fluxo de atualização das regras.

**Origem:** BLUEPRINT_QUESTOES.md #4
