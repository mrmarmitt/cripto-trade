# T9 — Observabilidade Docker Compose

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** nenhuma  
**Status:** Pendente

---

## Descrição

As métricas Micrometer já estão implementadas e exportadas pelo sistema (boot phases, portfolio metrics, fail-fast events). O que está faltando é a stack de coleta e visualização para o ambiente de staging.

Sem observabilidade, o soak test não tem valor diagnóstico: não é possível identificar drift de PENDING, crescimento de DLQ ou degradação de latência de conciliação ao longo do tempo.

Esta tarefa adiciona Prometheus e Grafana ao `docker-compose.yml` e configura o Spring para expor métricas no formato Prometheus.

---

## Escopo técnico

**Stack a adicionar:**

```
Spring Boot (Micrometer)  →  Prometheus  →  Grafana
:8080/actuator/prometheus      :9090           :3000
```

**Mudanças no `docker-compose.yml`:**
- Adicionar serviço `prometheus` com `prom/prometheus:latest`
- Adicionar serviço `grafana` com `grafana/grafana:latest`
- Configurar `prometheus.yml` com scrape config apontando para a aplicação
- Volumes persistentes para dados do Prometheus e dashboards do Grafana

**Mudanças no `application.yml`:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus, metrics
  endpoint:
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
```

**Dashboard mínimo no Grafana (provisioned automaticamente):**

| Painel                          | Métrica                                              |
|---------------------------------|------------------------------------------------------|
| Boot runs por status            | `boot.run.total` by `status`                        |
| Duração por fase de boot        | `boot.phase.duration` by `phase`, `status`          |
| Runners por status              | Customizar query via StrategyRunner (se exposto)    |
| Volume de DLQ aberto            | `dead_letter_entries` (se métrica existir)           |
| Transações PENDING acumuladas   | `transactions_by_status{status="PENDING"}`           |
| Latência de conciliação         | `conciliation.duration` (se existir)                |

Caso uma métrica ainda não exista, documentar no dashboard como "a implementar" e deixar o painel com a query correta mas sem dado.

**Arquivos a criar:**
- `docker-compose.yml` — adicionar serviços prometheus e grafana
- `docker/prometheus/prometheus.yml` — configuração de scrape
- `docker/grafana/provisioning/datasources/prometheus.yml` — datasource automático
- `docker/grafana/provisioning/dashboards/ctrade.json` — dashboard base

---

## Critérios de aceitação

1. `docker-compose up -d` sobe Postgres, Prometheus e Grafana sem erros.
2. `http://localhost:9090/targets` mostra a aplicação Spring como target `UP`.
3. `http://localhost:3000` abre o Grafana com datasource Prometheus já configurado (sem configuração manual).
4. O dashboard CTrade está disponível no Grafana após o primeiro boot com dados.
5. O painel "Boot runs por status" exibe contador após executar o boot uma vez.
6. A aplicação Spring expõe `/actuator/prometheus` acessível externamente ao container.
7. Os dados do Prometheus e Grafana são persistidos em volumes Docker (não se perdem no restart dos containers).
8. O `docker-compose.yml` não quebra o ambiente de desenvolvimento local (sem Prometheus/Grafana) — os serviços são opcionais e a aplicação sobe normalmente sem eles.
