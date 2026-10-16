# T25 — Safe Mode Automático

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T21 (Discord notifications), T23 (signal counter — opcional para trigger por HOLD rate), **T45** (caminho de ativação do Safe Mode)  
**Status:** Pendente

> **Escopo ampliado (2026-10-16, T39):** além dos gatilhos operacionais, esta task passa a cobrir os
> gatilhos 2–4 do BLUEPRINT §11.1.A ainda inexistentes: **rejeições consecutivas** (circuit breaker por
> runner com retomada após cooldown, IG §11.6), **divergência crítica de saldo** (`FAIL_DEFICIT` da
> sanidade de boot hoje só loga — passa a acionar HALT) e **anomalia de latência** (HALT com warm-up
> automático, IG §11.5.3). Todos pela porta `ManageSafeModePort` com `AUTOMATIC`; o por runner usa o
> halt do runner (T8), não o Safe Mode.

> **Atualização (2026-10-16, T43):** o drawdown 24h (equity marcado a mercado) já aciona HALT
> automático pela mesma porta. O gatilho "capital disponível < X% do inicial" abaixo ficou coberto por
> ele e não deve ser implementado separadamente; esta task trata só dos gatilhos operacionais (DLQ,
> WebSocket, watchdog).

> **Atualização (2026-10-16, T45):** a porta existe: `ManageSafeModePort.change(portfolioId,
> SafeModeOrigin.AUTOMATIC, new ChangeSafeModeRequest(HALT, motivo, false))`. Gatilho automático só
> consegue acionar `HALT` (o domínio recusa níveis acima) e não rebaixa nível manual mais alto; a
> retomada é sempre manual. A API manual é `GET/PUT /api/admin/portfolios/{id}/safe-mode`.

> **Correção (2026-10-15):** a seção "Safe Mode atual" abaixo estava factualmente errada. Não existe
> ativação manual por API: `Portfolio.setSafeModeStatus` não tem chamador e o endpoint
> `PUT /api/admin/portfolios/{id}/safe-mode` não existe. O campo é `SafeModeStatus` (enum de níveis),
> não booleano. O caminho de ativação (manual e porta para gatilhos automáticos) é entregue pela
> **T45**; esta task passa a apenas plugar os gatilhos nessa porta. O gatilho "capital disponível <
> X% do inicial" não substitui o breaker de drawdown da **T43** — unificar os motivos/porta entre as duas.

---

## Descrição

O Safe Mode hoje é ativado exclusivamente de forma manual via API. Em um sistema autônomo, falhas podem escalar antes que o operador tome ciência — DLQ acumulando, capital reservado preso em transações travadas, reconnects frequentes. O Safe Mode automático age como fusível: detecta condições de risco e para novas operações antes que o problema menor vire perda de capital.

---

## Contexto técnico

### Safe Mode atual

- `Portfolio.safeModeStatus` — enum `SafeModeStatus` (`NORMAL`, `HALT`, `CANCEL_ALL`, `PANIC_SELL`)
- Quando ativo: `CapitalReservationPolicy` bloqueia toda nova reserva de capital → nenhum BUY é executado
- Ativação manual via `PUT /api/admin/portfolios/{id}/safe-mode` — entregue pela T45
- Escopo: por portfolio

### Invariante relevante

> *"Safe Mode deve impedir novas reservas quando ativo."* — `/.ai/flows/portfolio-capital.md`

A lógica de bloqueio já existe. O que falta é o trigger automático.

---

## Condições de disparo sugeridas

Cada condição é configurável e pode ser habilitada/desabilitada individualmente:

| Condição | Threshold padrão | Racional |
|---|---|---|
| DLQ não resolvida > N entradas | `dlq.safe-mode.threshold: 3` | DLQ indica falha persistente de reconciliação — risco de estado inconsistente |
| Capital disponível < X% do inicial | `capital.safe-mode.min-percent: 10` | Proteção contra drawdown excessivo |
| N reconexões em Y minutos | `websocket.safe-mode.reconnects: 5` em `5m` | Instabilidade de conexão — fills podem não chegar |
| Runner com watchdog sem resolver em Z ciclos | `watchdog.safe-mode.stuck-cycles: 3` | Transação presa que a exchange também não conhece |

---

## Solução proposta

### 1. Port no core

Criar `SafeModeEvaluationPort` — avaliado por um componente externo (Spring) que tem acesso às métricas e ao estado do sistema:

```java
public interface SafeModeActivationPort {
    void activateSafeMode(UUID portfolioId, SafeModeReason reason);
}
```

`SafeModeReason`: enum com `DLQ_THRESHOLD`, `CAPITAL_THRESHOLD`, `WEBSOCKET_INSTABILITY`, `WATCHDOG_STUCK`.

### 2. Use case de ativação automática

Criar `AutoSafeModeUseCase` no core:
- Recebe `portfolioId` e `reason`
- Verifica se Safe Mode já está ativo (idempotente)
- Ativa via `Portfolio.enableSafeMode()`
- Loga: `autoSafeMode: activated portfolioId={} reason={}`
- Publica `SafeModeActivatedEvent` para notificação

### 3. Avaliador no Spring (scheduler)

Criar `SafeModeEvaluator` com `@Scheduled(fixedDelay = 30s)`:

- **Condição DLQ**: consulta `DeadLetterEntryRepositoryPort.countUnresolved(portfolioId)` — se > threshold, ativa
- **Condição capital**: consulta `GlobalBalance.availableBalance / initialCapital` — se < threshold, ativa
- **Condição reconnect**: consulta rate de eventos `WebSocketFailedEvent` nos últimos N minutos via contador em memória
- **Condição watchdog**: detectado no `RecoverStaleTransactionsUseCase` quando mesma transação aparece em múltiplos ciclos sem `RECOVERED`

### 4. Notificação Discord

`SafeModeActivatedEvent` chama `ErrorNotificationPort` (T21) com mensagem:

```
⚠️ SAFE MODE ATIVADO
Portfolio: <portfolioId>
Motivo:    DLQ_THRESHOLD (5 entradas não resolvidas)
Ação:      Novas ordens BUY bloqueadas
```

### 5. Desativação

Desativação permanece **sempre manual** — operador confirma que o problema foi resolvido antes de retomar operação. Endpoint existente: `PUT /api/admin/portfolios/{id}/safe-mode`.

---

## Configuração (application.yml)

```yaml
safe-mode:
  auto:
    enabled: true
    evaluation-interval-ms: 30000
    dlq:
      enabled: true
      threshold: 3
    capital:
      enabled: true
      min-available-percent: 10
    websocket:
      enabled: true
      max-reconnects-per-window: 5
      window-minutes: 5
    watchdog:
      enabled: true
      max-stuck-cycles: 3
```

---

## Arquivos a modificar / criar

| Arquivo | Mudança |
|---|---|
| `core/.../ports/inbound/SafeModeActivationPort.java` | Novo — contrato de ativação |
| `core/.../usecase/portfolio/AutoSafeModeUseCase.java` | Novo — lógica de ativação com log e evento |
| `core/.../domain/portfolio/SafeModeReason.java` | Novo — enum de razões |
| `spring-application/.../bootstrap/SafeModeEvaluator.java` | Novo — scheduler com avaliação das condições |
| `spring-application/src/main/resources/application.yml` | Configuração das condições e thresholds |
| `spring-application/.../handler/BootAlertListener.java` | Escutar `SafeModeActivatedEvent` → Discord |

---

## Critérios de aceitação

1. Com 3+ DLQ entries não resolvidas, Safe Mode é ativado automaticamente em até 30s.
2. Após ativação, novas ordens BUY são bloqueadas pela `CapitalReservationPolicy` existente.
3. Mensagem Discord enviada com motivo e portfolioId.
4. Segunda avaliação com Safe Mode já ativo não duplica evento nem log.
5. Desativação manual via `PUT /api/admin/portfolios/{id}/safe-mode` continua funcionando.
6. Cada condição pode ser desabilitada individualmente via config sem alterar as demais.
7. `safe-mode.auto.enabled: false` desabilita o avaliador inteiro sem afetar Safe Mode manual.
