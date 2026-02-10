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
