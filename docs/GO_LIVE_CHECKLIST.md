# Go-Live Checklist (Produção)

## Objetivo

Consolidar os principais critérios para sair do ambiente de simulação (MOCK) e operar com segurança em produção, mantendo aderência à proposta arquitetural do **Blueprint** e às decisões táticas do **Implementation Guide (IG)**.

## Premissa Atual

- O sistema está funcional com a exchange `MOCK` (fluxo BUY/PARTIAL/SELL/REJECT/BOOT).
- Este checklist cobre os pontos de atenção para produção, além da integração real com exchange.

## Checklist de Prontidão

1. Testes automatizados mínimos
- Cobrir cenários críticos com testes unitários e de integração:
  - BUY completo
  - BUY parcial com evolução para FILLED
  - SELL parcial e SELL final
  - REJECTED com rollback/release esperado
  - Boot recovery (zombie, limbo, ttl, saneamento)

2. Idempotência forte no processamento de ordens
- Garantir dedupe confiável para evitar dupla conciliação em mensagens repetidas.
- Recomendação prática: chave de dedupe baseada em `exchangeOrderId + status + executedQty + timestamp`.

3. Fronteiras transacionais consistentes
- Validar que cada caminho crítico (persistência, conciliação, reserva/liberação de capital, publicação de eventos) está protegido por fronteira transacional coerente.
- Evitar estados intermediários parcialmente persistidos.

4. Operação de DLQ (não só detecção)
- Ter meios de diagnóstico e remediação:
  - consultar pendências
  - reprocessar com segurança
  - encerrar pendências com trilha de auditoria
- Definir runbook objetivo para incidentes de DLQ.

5. Observabilidade e alertas
- Métricas e alarmes mínimos:
  - runners em `HALTED`
  - falha de boot por fase
  - volume/idade de transações `PENDING`
  - entradas DLQ abertas
  - erros de conciliação por exchange

6. Guardrails de risco
- Confirmar limites operacionais por portfolio e por runner.
- Implementar/validar kill switch global.
- Aplicar circuit breaker por exchange em falhas recorrentes.

7. Precisão financeira e regras de exchange
- Validar cálculo monetário ponta a ponta:
  - arredondamento
  - fee
  - realized/unrealized PnL
- Validar filtros reais da exchange:
  - `stepSize`
  - `minNotional`
  - `tickSize`

8. Segurança e segredos
- Remover credenciais hardcoded e adotar gestão de segredos.
- Definir política de rotação de credenciais.
- Restringir endpoints administrativos e sensíveis.

9. Confiabilidade operacional
- Ter estratégia de backup/restore validada.
- Garantir migrações versionadas com rollback seguro.
- Definir procedimentos claros para restart e recuperação.

10. Staging e soak test antes de produção
- Rodar ambiente de staging com comportamento próximo ao real.
- Executar testes de carga e falhas controladas (latência, timeout, duplicidade, indisponibilidade).
- Só promover após estabilidade observada por janela mínima definida.

## Alinhamento com Blueprint e IG

- **Blueprint**: este checklist reforça os princípios de consistência de domínio, isolamento de responsabilidades, recuperação de falhas e segurança operacional.
- **IG**: este checklist complementa as decisões de implementação com critérios de qualidade operacional e governança para produção.
- Regra prática: toda evolução desse checklist deve manter rastreabilidade com os conceitos e invariantes já definidos no Blueprint/IG.


## Status de Aderencia (2026-03-07)

- Testing Roadmap Fase 0: CONCLUIDA no branch `develop`.
- Testing Roadmap Fase 1A: CONCLUIDA no branch `develop`.

Evidencias de Fase 0 em testes automatizados:
- `core/src/test/java/com/marmitt/core/domain/portfolio/GlobalBalanceInvariantsTest.java`
- `core/src/test/java/com/marmitt/core/domain/runner/StrategyRunnerInvariantsTest.java`
- `core/src/test/java/com/marmitt/core/domain/runner/TransactionInvariantsTest.java`
- `core/src/test/java/com/marmitt/core/application/usecase/runner/orderconciliation/ConciliationOrderUpdateIdempotencyTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/BootOrchestratorBootMinimumTest.java`

Evidencias de Fase 1A em testes automatizados:
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/ProcessTradeSignalMockIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/OrderLifecycleMockIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/OrderTerminationConciliationIntegrationTest.java`
- `spring-application/src/test/java/com/marmitt/application/spring/bootstrap/RunnerBootRecoveryIntegrationTest.java`

### Impacto no Go-Live

- Item 1 (Testes automatizados minimos): PARCIAL (AVANCADO).
- Baseline de invariantes e integracao core com MOCK cobertos (Fases 0 e 1A).
- Ainda faltam fases de robustez e operacao (Fases 1B, 2, 3 e 4) e integracao real com exchange.

### Proximo passo recomendado

1. Avancar Fase 1B (controles deterministicos no MOCK para duplicidade/concorrencia/falha).
2. Entrar na Fase 2 (resiliencia sob concorrencia e eventos repetidos).
3. Atualizar este checklist a cada fase concluida do Testing Roadmap.
