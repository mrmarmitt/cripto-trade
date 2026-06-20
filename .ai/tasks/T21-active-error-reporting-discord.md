# T21 — Active Error Reporting via Discord

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T17 (Loki + Grafana já no stack)  
**Status:** Em andamento — Camada 1 (código) entregue; Camadas 2 e 3 (Grafana/logs) pendentes

> **Progresso Camada 1:** `ErrorNotificationPort` no core + `DiscordWebhookNotificationAdapter`
> (no-op quando `DISCORD_WEBHOOK_URL` vazio, envio assíncrono e tolerante a falha), com disparo
> em `CapitalEventListener` (DLQ persistido) e `BootAlertListener` (boot fail-fast). Atende os
> critérios de aceitação 1-4. Critérios 5-6 (alert rules Grafana e link Loki provisionado)
> dependem das Camadas 2/3.

---

## Descrição

A aplicação roda de forma autônoma por longos períodos. Quando um erro terminal ocorre (DLQ, boot fail-fast) ou uma anomalia de comportamento é detectada, o operador só descobre se abrir o Grafana ativamente. Sem notificação push, erros podem se acumular silenciosamente.

Esta tarefa fecha o loop de observabilidade: o operador é notificado no Discord com contexto suficiente para agir — sem precisar abrir nenhum dashboard.

---

## Escopo técnico

O problema é dividido em duas camadas independentes:

```
Camada 1 — Erros terminais (código)
  CapitalEventListener.@Recover  ─┐
  BootAlertListener               ├─→ ErrorNotificationPort → DiscordWebhookAdapter → Discord
  (futuros pontos de disparo)    ─┘

Camada 2 — Anomalias comportamentais (Loki → Grafana → Discord)
  LogQL query (WARN rate, reconnect freq.)  →  Grafana Alert Rule  →  Discord Contact Point
```

---

## Camada 1 — Notificações de erro terminal

### 1.1 Port no core

Criar `core/.../ports/outbound/ErrorNotificationPort.java`:

```java
public interface ErrorNotificationPort {
    void notifyError(ErrorNotificationEvent event);
}
```

`ErrorNotificationEvent` deve conter: `type` (enum: DLQ_PERSISTED, BOOT_FAIL_FAST), `title`, `description`, `correlationId` (nullable), `runnerId` (nullable), `dlqId` (nullable), `occurredAt`.

### 1.2 Adapter Discord no spring-application

Criar `spring-application/.../adapter/DiscordWebhookNotificationAdapter.java`:

- Implementa `ErrorNotificationPort`
- Faz `POST` para `DISCORD_WEBHOOK_URL` (env var) com payload JSON Discord Embed
- Campo `url` da embed aponta para query Loki filtrada pelo `correlationId` quando disponível
- Falha silenciosa com log ERROR local se o webhook não responder — nunca propaga exceção para o caller
- URL configurável via `application.yml`: `notification.discord.webhook-url=${DISCORD_WEBHOOK_URL:}`
- Se `webhook-url` estiver vazio, adapter é no-op (sem quebrar a aplicação quando Discord não configurado)

Formato da mensagem Discord (embed):

```
[TIPO] Título do erro
───────────────────────
Reason:       DLQ_PERSISTED / BOOT_FAIL_FAST
Runner:       <runnerId>
DLQ ID:       <dlqId>
CorrelationId: <correlationId>
Loki:         [ver logs](<link para Grafana Explore filtrado>)
Timestamp:    2026-06-16T14:32:00Z
```

### 1.3 Pontos de disparo

**`CapitalEventListener.@Recover` (`persistCapitalDlq`):**
- Após persistir o DLQ entry com sucesso, chamar `errorNotificationPort.notifyError(...)`
- Incluir `dlqId`, `reason`, `runnerId` do evento

**`BootAlertListener` (`onBootFailFast`):**
- Após logar `BOOT_FAILFAST_ALERT`, chamar `errorNotificationPort.notifyError(...)`
- Incluir `phase`, `code`, `message` do `BootFailFastEvent`

### 1.4 Injeção

- `DiscordWebhookNotificationAdapter` registrado como `@Component`
- `ErrorNotificationPort` injetado por constructor nos listeners
- Se `DISCORD_WEBHOOK_URL` não configurado, bean é registrado como no-op via `@ConditionalOnProperty`

---

## Camada 2 — Alertas comportamentais (Grafana → Discord)

### 2.1 Contact point Discord no Grafana

Adicionar em `docker/grafana/provisioning/alerting/` (ou via UI e exportar como JSON):

```yaml
contactPoints:
  - name: discord
    receivers:
      - type: discord
        settings:
          url: ${DISCORD_WEBHOOK_URL}
```

### 2.2 Alert rules sugeridas

| Alert | Query LogQL | Condição |
|---|---|---|
| Alta taxa de WARN | `rate({app="ctrade", level="WARN"}[5m])` | > 0.5/s por 2 min |
| Spike de ERROR | `rate({app="ctrade", level="ERROR"}[1m])` | > 0.1/s |
| Reconnect frequente | `rate({app="ctrade"} \|= "reconnect"[5m])` | > 3/min |

Canal de destino: `#alerts-behavior` (separado de `#alerts-error` para triagem).

---

## Camada 3 — Melhoria de logs sentinel existentes

A exploração do código revelou que vários logs já atuam como sentinels de fluxo, mas com qualidade inconsistente. Logs sem campos estruturados não são detectáveis via LogQL e precisam ser enriquecidos antes de criar alert rules sobre eles.

### 3.1 Logs a enriquecer (baixa qualidade atual)

| Log atual | Arquivo | Campos a adicionar |
|---|---|---|
| `bootSequence.phase1: completed exchanges={}` | `RunBootSequenceUseCase` | `runId` |
| `bootSequence.phase2.sanity: completed` | `RunBootSequenceUseCase` | `runId`, `portfolios` (count) |
| `bootSequence.phase2.zombie: completed` | `RunBootSequenceUseCase` | `runId`, `zombies` (count) |
| `bootSequence.phase2.ttl: completed` | `RunBootSequenceUseCase` | `runId`, `expired` (count) |
| `Processing SUCCESS - type={}` | `ProcessMessageEventListener` | `correlationId` (já no MDC, incluir no msg) |

### 3.2 Logs de alta qualidade (já prontos para alert rules)

Estes não precisam de mudança — já têm campos suficientes para `absent_over_time()`:

- `bootRecovery: completed runnerId=... hasErrors=...` — `RunnerBootRecoveryUseCase`
- `bootSequence: completed runId=... portfolios=... runners=...` — `RunBootSequenceUseCase`
- `BUY fill positionId=... runnerId=... fillPrice=...` — `BuyFillHandler`
- `SELL match persisted matchId=... status=...` — `SellFillHandler`
- `executionConfirmedReaction: matchId=... isFinal=...` — `ExecutionConfirmedReaction`
- `marginReleasedReaction: transactionId=... reason=...` — `MarginReleasedReaction`
- `capital reserved transactionId=...` — `ProcessTradeSignalUseCase`
- `PENDING->SUBMITTED transactionId=... exchangeOrderId=...` — `ConciliationOrderUpdate`

### 3.3 Alert rules de ausência baseadas nos logs existentes

Com os logs de alta qualidade já presentes, adicionar ao Grafana:

| Alert | Query LogQL | Janela | Canal |
|---|---|---|---|
| Nenhum fill confirmado | `absent_over_time({app="ctrade"} \|= "executionConfirmedReaction:" \| json \| isFinal="true" [10m])` | 10 min | `#alerts-behavior` |
| Boot recovery com erros | `{app="ctrade"} \|= "bootRecovery: completed" \| json \| hasErrors="true"` | on occurrence | `#alerts-behavior` |
| Nenhuma ordem submetida | `absent_over_time({app="ctrade"} \|= "PENDING->SUBMITTED" [30m])` | 30 min | `#alerts-behavior` |

---

## Evolução futura (fora de escopo agora)

- `GitHubIssueNotificationAdapter` implementando o mesmo `ErrorNotificationPort` — criação automática de issue com stack trace e link Loki ao persistir DLQ

---

## Critérios de aceitação

1. Ao esgotar retries de um capital event, uma mensagem aparece no canal Discord em < 10s com dlqId e correlationId.
2. Ao ocorrer `BootFailFastEvent`, mensagem aparece no Discord antes do processo encerrar.
3. Se `DISCORD_WEBHOOK_URL` não estiver configurado, a aplicação sobe normalmente sem erro.
4. Falha no webhook Discord não propaga exceção nem interrompe o fluxo principal.
5. Grafana contact point Discord ativo e ao menos uma alert rule disparando para o canal `#alerts-behavior`.
6. Link na mensagem Discord abre o Grafana Explore filtrado pelo `correlationId` do evento.
