# Questões Pendentes — Configuration Reference

## 4. Validação Exchange-Specific

**Pergunta:** Onde reside o conhecimento das regras específicas da exchange (minQty, stepSize, tickSize, minNotional) e quem é responsável por validá-las antes do envio de uma ordem?

**Detalhamento necessário:**
- Essas regras são configuradas estaticamente ou buscadas dinamicamente da API da exchange?
- O que acontece se uma regra mudar durante uma sessão de trading?
- Validação acontece no StrategyRunner, no ExchangeAdapter, ou em ambos?
- **Implicação:** Define a necessidade de um `ExchangeRulesRegistry` e o fluxo de atualização das regras.

**Origem:** BLUEPRINT_QUESTOES.md #4

---

## Defaults do Circuit Breaker e Risk Properties

**Pergunta:** Quais são os valores default sensatos para os parâmetros configuráveis do Circuit Breaker Global (seção 11.1 do Blueprint)?

**Detalhamento necessário:**
- **Max Daily Drawdown:** Qual percentual de queda do `GlobalBalance` aciona o bloqueio? (ex: 5%? 10%?)
- **Rejeições Consecutivas:** Quantos estados `REJECTED` em qual intervalo de tempo? (ex: 3 em 1 minuto?)
- **Divergência Crítica de Saldo:** Qual limite de diferença entre saldo local e exchange? (ex: > 0.01 BTC?)
- **Timeout de Retomada por Latência:** Quantos minutos de estabilidade antes de permitir warm-up? (ex: 5 min?)
- **Implicação:** Define os valores iniciais do `RiskProperties` e se devem variar por exchange ou por Runner.

**Origem:** Blueprint seção 11.1 (valores marcados como configuráveis)

---

## Gestão de Configuração em Múltiplos Níveis

**Pergunta:** Como o sistema organiza a hierarquia de configurações que envolvem exchange, símbolo, Runner e estratégia?

**Detalhamento necessário:**
- Onde vivem as configurações que misturam (exchange, símbolo, Runner, estratégia)?
- Como uma configuração global (ex: "drawdown máximo 15%") é herdada/sobrescrita por um Runner específico?
- É possível alterar parâmetros de um Runner em tempo real sem reiniciá-lo?
- Como garantir consistência de configuração entre múltiplas instâncias do mesmo Runner?
- O que acontece se a configuração local diverge da configuração real da Exchange?
- **Implicação:** Define a hierarquia de configuração (global → exchange → runner → estratégia) e o suporte a hot-reload.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #6
