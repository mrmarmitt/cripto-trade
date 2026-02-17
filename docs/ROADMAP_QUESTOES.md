# Questões Pendentes — Roadmap

## 7. Acionamento de Stop Loss

**Pergunta:** Como o sistema implementa ordens de stop-loss? Elas são gerenciadas localmente ou delegadas à exchange?

**Detalhamento necessário:**
- Stop-loss é uma funcionalidade do StrategyRunner ou do ExchangeAdapter?
- E se a conexão cair quando o preço atingir o stop?
- Como lidar com slippage no stop-loss?
- **Implicação:** Define se precisa de um `StopLossMonitor` em tempo real e o acoplamento com o WebSocket de market data.

**Origem:** BLUEPRINT_QUESTOES.md #7

---

## 8. Dependências Cruzadas entre Runners

**Pergunta:** Até que ponto os StrategyRunners são isolados uns dos outros? Um Runner pode tomar decisões baseadas nas posições de outro Runner?

**Detalhamento necessário:**
- Um Runner de hedging precisa saber das posições do Runner principal?
- Como implementar alocação de capital entre Runners concorrentes?
- Os Runners compartilham conexões WebSocket para o mesmo símbolo?
- **Implicação:** Define os limites de comunicação entre agregados e a necessidade de um `CrossRunnerCoordinator`.

**Origem:** BLUEPRINT_QUESTOES.md #8

---

## Cross-Exchange Arbitrage

**Pergunta:** O sistema suporta cenários onde um Runner opera em múltiplas exchanges simultaneamente?

**Detalhamento necessário:**
- Um Runner pode ter Positions na Binance e Coinbase ao mesmo tempo?
- Como o Portfolio soma margem entre exchanges diferentes?
- Risco de double counting de saldo?
- **Implicação:** Define se o modelo atual (1 Runner = 1 Exchange) é suficiente ou se precisa de abstração multi-exchange.

**Origem:** QUESTOES_PENDENTES.md #19

---

## Persistência de Market Data para Estratégias

**Pergunta:** Os dados de mercado processados pelas estratégias são persistidos pelo sistema ou apenas passados em tempo real?

**Detalhamento necessário:**
- Estratégias stateless vs stateful em relação a dados históricos?
- Necessidade de banco de séries temporais?
- Impacto no backtesting futuro?
- **Implicação:** Define requisitos de infraestrutura e capacidade das estratégias.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #17

---

## Tratamento de Exchanges com Comportamento Não-Padrão

**Pergunta:** Como adaptar o sistema para exchanges que não seguem o modelo assumido no Blueprint?

**Detalhamento necessário:**
- O modelo atual pressupõe que todas as exchanges implementam `clientOrderId`. E se alguma não suportar?
- E se a exchange tem limites de caracteres menores que o formato proposto (ex: 20 chars)?
- E se a exchange não permite cancelar ordens parciais?
- Como adaptar o protocolo de identificação para exchanges sem suporte a `clientOrderId`?
- **Implicação:** Define se o Blueprint precisa de uma camada de abstração para exchanges não-padrão ou se é uma limitação aceita.

**Origem:** QUESTOES_SEM_CLASSIFICACAO.md #16
