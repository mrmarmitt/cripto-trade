# Flow Maps Roadmap

Este arquivo acompanha o plano de desenvolvimento de `/.ai/flows`.

O objetivo da pasta e tornar novas sessoes de IA mais autosuficientes para
features, bugs, reviews e refactors em fluxos existentes. Cada flow map deve
conectar comportamento implementado, codigo principal, invariantes e validacao.

## Status Geral

| Entrega | Status | Resultado esperado |
| --- | --- | --- |
| Estrutura base de `/.ai/flows` | Completo | Indice, template e regra de manutencao dos flow maps. |
| Mapas dos fluxos centrais | Completo | Cobertura dos fluxos mais criticos de sinal, ordem, capital e boot. |
| Regra de impacto documental | Completo | Toda alteracao de codigo deve avaliar se a documentacao base precisa mudar. |
| Mapas de integracao e eventos externos | Pendente | Cobertura de Binance, WebSocket e adapter mock. |
| Mapas operacionais e persistencia | Pendente | Cobertura de DLQ/replay e persistencia dos agregados principais. |

## Entrega 1: Base E Fluxos Centrais

Status: Completo.

Arquivos entregues:

- `README.md`: indice, uso e regras de manutencao.
- `_template.md`: modelo para novos mapas.
- `runner-signal-processing.md`: market data, contexto de estrategia, BUY/SELL,
  reserva, lock e dispatch.
- `order-conciliation.md`: updates de ordem, fills, idempotencia, posicoes,
  matches e eventos financeiros.
- `portfolio-capital.md`: portfolio, `GlobalBalance`, reserva, confirmacao
  financeira, liberacao de margem e DLQ de capital.
- `boot-recovery.md`: readiness, sanity check, zombie detection, reservation TTL
  e recovery de runners.

## Entrega 2: Integracao E Eventos Externos

Status: Pendente.

Ordem recomendada:

1. `binance-user-data-stream.md`
   - Listen key, User Data Stream, eventos de ordem e traducao Binance para
     contratos internos.
   - Deve mostrar como eventos reais chegam ao fluxo de conciliacao.
2. `websocket-event-routing.md`
   - Entrada de eventos, processors/listeners, roteamento para use cases e
     fronteiras entre adapter, Spring e core.
   - Deve deixar claro quem traduz payload externo e quem chama o core.
3. `mock-exchange-runtime.md`
   - Feed simulado, execucao mock, slippage, fees e cenarios de desenvolvimento.
   - Deve explicar como o mock sustenta testes e desenvolvimento local.

## Entrega 3: Operacao E Persistencia

Status: Pendente.

Ordem recomendada:

1. `dead-letter-replay.md`
   - DLQ operacional, payloads, replay, resolucao manual e falhas persistentes.
   - Deve cobrir tanto o caminho de capital quanto o uso operacional em boot.
2. `persistence-adapters.md`
   - Repositorios, mapeamento de agregados principais, fronteiras de persistencia
     e pontos de risco em migracoes/estado.
   - Deve orientar bugs de estado sem transformar o mapa em referencia completa
     de schema.

## Criterios Para Marcar Um Mapa Como Completo

Um flow map pode ser marcado como completo quando contem:

- objetivo do fluxo;
- quando consultar;
- entradas, saidas e efeitos colaterais;
- fluxo principal;
- variacoes e falhas relevantes;
- componentes principais com caminhos reais;
- invariantes que nao podem ser quebradas;
- validacao recomendada;
- fontes usadas para confirmar o comportamento.

## Regra De Manutencao

Ao mudar codigo, avalie impacto em `/.ai/flows`. Atualize o mapa aplicavel
quando a mudanca alterar decisao, contrato, transicao, invariante, idempotencia,
validacao ou comportamento operacional.

Se a mudanca de codigo nao afetar documentacao base, declare isso no fechamento
da tarefa ou no PR.
