# Flow Maps

Esta pasta descreve como os fluxos ja implementados funcionam no codigo.
Ela preenche o espaco entre a documentacao de processo/arquitetura e as classes
concretas que executam cada comportamento.

Use estes arquivos quando a tarefa envolver feature, bug, review ou refactor em
um fluxo existente. Eles nao substituem o codigo nem os testes; funcionam como
mapas de entrada para uma nova sessao entender rapidamente:

- qual problema o fluxo resolve;
- quais classes participam;
- quais invariantes nao podem ser quebradas;
- quais testes validam o comportamento;
- onde olhar primeiro no codigo.

## Como Usar

1. Leia `/.ai/README.md` e siga a matriz de contexto minimo.
2. Abra o mapa do fluxo mais proximo da tarefa.
3. Confirme o comportamento no codigo antes de alterar.
4. Atualize o mapa quando a mudanca alterar uma decisao, transicao, contrato,
   regra de idempotencia ou validacao importante.

O plano de expansao e status dos mapas fica em `ROADMAP.md`.

## Fluxos Mapeados

| Fluxo | Quando consultar |
| --- | --- |
| `runner-signal-processing.md` | Processamento de sinais, montagem de contexto de estrategia, criacao de transacoes BUY/SELL e envio de ordens. |
| `order-conciliation.md` | Atualizacoes de ordens vindas da exchange, fills, finalizacao, idempotencia e conciliacao via boot. |
| `portfolio-capital.md` | Portfolio, GlobalBalance, reserva de capital, confirmacao financeira, liberacao de margem e DLQ de capital. |
| `boot-recovery.md` | Sequencia de boot, readiness, sanity check, deteccao de zombies, TTL de reserva e recuperacao de runners. |
| `binance-user-data-stream.md` | Listen key, User Data Stream, `executionReport`, reconexao e entrada de updates Binance na conciliacao. |
| `websocket-event-routing.md` | Entrada WebSocket, eventos raw, handlers de mensagem e notificacao de listeners de preco/ordem. |
| `mock-exchange-runtime.md` | Runtime mock, feed simulado, ordem mock, slippage/fees, overrides e publicacao no pipeline raw. |
| `dead-letter-replay.md` | DLQ operacional, DLQ de capital, replay automatico, resolucao manual e impacto no boot/recovery. |
| `persistence-adapters.md` | Persistencia JDBC dos agregados principais, mappers, migrations, locks, operacoes atomicas e idempotencia. |

## Regras de Manutencao

- Mantenha cada mapa conciso. A intencao e orientar a leitura, nao copiar o
  codigo.
- Prefira caminhos de arquivos, nomes de classes e invariantes verificaveis.
- Inclua testes relevantes sempre que existirem.
- Nao duplique regras detalhadas de `docs/BLUEPRINT.md` ou
  `docs/IMPLEMENTATION_GUIDE.md`; referencie o fluxo implementado.
- Se um comportamento ainda nao existe, deixe isso claro em vez de documentar
  como se estivesse pronto.

## Proximos Mapas Candidatos

Sem candidatos definidos no momento. Adicione novos candidatos aqui quando um
fluxo implementado ainda nao tiver mapa em `/.ai/flows`.
