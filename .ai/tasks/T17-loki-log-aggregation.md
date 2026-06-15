# T17 — Loki: Agregação de Logs

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T9 (Prometheus + Grafana já no docker-compose)  
**Status:** Concluído

---

## Descrição

O sistema emite logs estruturados com `clientOrderId`, `correlationId`, `exchangeName` e `runnerId` via MDC, mas esses logs só existem no stdout do container. Sem agregação, um ERROR em produção exige acesso ao terminal do container para ser investigado.

Esta tarefa adiciona Loki ao stack de observabilidade, permitindo:
- Busca de logs por `clientOrderId` ou `runnerId` diretamente no Grafana
- Alertas quando a taxa de eventos `ERROR` ou `WARN` ultrapassa um threshold
- Correlação de logs com métricas Prometheus na mesma janela temporal

---

## Escopo técnico

**Stack a adicionar:**

```
Spring Boot (logback + loki4j)  →  Loki  →  Grafana (Explore + Dashboard)
                                    :3100        :3000
```

Usar `loki4j` appender (push direto do JVM) em vez de Promtail, para evitar montagem de volume de log.

---

### 1. Dependência no `spring-application/build.gradle`

```groovy
implementation 'com.github.loki4j:loki-logback-appender:1.5.2'
```

---

### 2. `logback-spring.xml` — appender Loki

Criar ou atualizar `spring-application/src/main/resources/logback-spring.xml`:

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
  <http>
    <url>http://loki:3100/loki/api/v1/push</url>
  </http>
  <format>
    <label>
      <pattern>app=ctrade,host=${HOSTNAME},level=%level</pattern>
      <readMarkers>true</readMarkers>
    </label>
    <message class="com.github.loki4j.logback.JsonLayout">
      <includeKeyValue>true</includeKeyValue>  <!-- expõe campos MDC como labels -->
    </message>
  </format>
</appender>

<root level="INFO">
  <appender-ref ref="CONSOLE" />
  <appender-ref ref="LOKI" />
</root>
```

Campos MDC já disponíveis: `correlationId`, `exchangeName`, `connectionId` (ver `ProcessMessageEventListener`).

---

### 3. `docker-compose.yml` — serviço Loki

```yaml
loki:
  image: grafana/loki:2.9.0
  ports:
    - "3100:3100"
  command: -config.file=/etc/loki/local-config.yaml
  volumes:
    - loki_data:/loki
  networks:
    - ctrade-network

volumes:
  loki_data:
```

---

### 4. Datasource Loki no Grafana (provisioned)

Criar `docker/grafana/provisioning/datasources/loki.yml`:

```yaml
apiVersion: 1
datasources:
  - name: Loki
    type: loki
    url: http://loki:3100
    access: proxy
    isDefault: false
```

---

### 5. Dashboard mínimo de logs no Grafana

Painel sugerido (LogQL):

| Painel | Query |
|---|---|
| Stream de ERRORs | `{app="ctrade", level="ERROR"}` |
| Taxa de ERRORs por minuto | `rate({app="ctrade", level="ERROR"}[1m])` |
| Logs por clientOrderId | `{app="ctrade"} \| json \| clientOrderId="<valor>"` |
| Logs por runnerId | `{app="ctrade"} \| json \| runnerId="<valor>"` |

---

### 6. Alerta de threshold de erros

Criar alert rule no Grafana:
- Condição: `rate({app="ctrade", level="ERROR"}[5m]) > 0.1` (mais de 6 ERRORs por minuto)
- Ação: anotação visual no dashboard (notificador pode ser configurado posteriormente)

---

## Critérios de aceitação

1. `docker-compose up -d` sobe Loki sem erros.
2. Grafana exibe datasource Loki ativo (verde) em `http://localhost:3000/datasources`.
3. Após 1 minuto de aplicação rodando, logs aparecem no Grafana Explore com query `{app="ctrade"}`.
4. Busca por `clientOrderId` de uma ordem real retorna os logs correspondentes (NEW → SUBMITTED → FILLED).
5. Um evento ERROR gerado manualmente aparece no painel "Stream de ERRORs" em até 10 segundos.
6. A aplicação Spring sobe normalmente mesmo se Loki estiver fora do ar (appender não-blocante, falha silenciosa).
7. Logs locais (stdout) continuam funcionando independentemente do Loki.
