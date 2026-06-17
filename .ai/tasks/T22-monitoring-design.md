# T22 — Estudo e Desenho do Sistema de Monitoramento

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T17 (Loki + Grafana), T21 (Discord notifications)  
**Status:** Concluído

---

## Descrição

A aplicação possui observabilidade reativa (logs, métricas de boot, dashboards passivos), mas não tem um sistema de monitoramento ativo definido: o que precisa ser observado, com qual frequência, o que constitui uma anomalia e qual ação deve ser disparada.

Esta tarefa é um **estudo e especificação** — não implementação. O output é um documento de monitoramento que define por fluxo:

- O que monitorar (indicador)
- Como monitorar (LogQL, PromQL, ou health check)
- Com qual frequência / janela de tempo
- O que constitui uma anomalia (threshold ou ausência)
- Qual trigger disparar (Discord `#alerts-error`, `#alerts-behavior`, ou nenhum)

O estudo deve ser baseado nos fluxos documentados em `/.ai/flows/`.

---

## Fluxos a analisar

| Fluxo | Arquivo de referência |
|---|---|
| Processamento de sinais e ordens BUY/SELL | `/.ai/flows/runner-signal-processing.md` |
| Conciliação de ordens e fills | `/.ai/flows/order-conciliation.md` |
| Capital, reserva e DLQ financeiro | `/.ai/flows/portfolio-capital.md` |
| Boot e recovery de runners | `/.ai/flows/boot-recovery.md` |
| User Data Stream Binance | `/.ai/flows/binance-user-data-stream.md` |
| Roteamento de eventos WebSocket | `/.ai/flows/websocket-event-routing.md` |
| Dead Letter replay operacional | `/.ai/flows/dead-letter-replay.md` |

---

## Perguntas a responder por fluxo

Para cada fluxo, o estudo deve responder:

1. **O que pode silenciosamente parar de funcionar sem gerar ERROR?**  
   Ex: runner parado de receber sinais, WebSocket conectado mas sem mensagens.

2. **Qual é a cadência normal esperada?**  
   Ex: fills ocorrem? Com que frequência? A ausência por X minutos é suspeita?

3. **Qual log sentinel de alta qualidade já existe para esse fluxo?** (ver T21 Camada 3)

4. **O que ainda não tem indicador e precisaria de métrica nova ou log novo?**

5. **Qual é o trigger apropriado?**
   - `#alerts-error` — problema que exige ação imediata
   - `#alerts-behavior` — anomalia que merece investigação
   - Nenhum — ruído esperado em staging

---

## Output esperado

Um documento `/.ai/monitoring-spec.md` com a tabela de monitoramento completa:

```
| Fluxo | Indicador | Tipo | Query | Janela | Condição de disparo | Canal | Prioridade |
```

E uma seção de **gaps de instrumentação**: indicadores desejados que ainda não têm log ou métrica disponível (insumo para tasks futuras de instrumentação).

---

## Critérios de aceitação

1. Todos os fluxos de `/.ai/flows/` foram analisados.
2. Cada fluxo tem ao menos um indicador de saúde ativo definido.
3. Os logs sentinel existentes (mapeados em T21) estão referenciados nas queries onde aplicável.
4. Gaps de instrumentação estão listados separadamente — sem inventar indicadores que não existem.
5. O documento está em `/.ai/monitoring-spec.md`.
