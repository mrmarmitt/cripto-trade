# Questoes Sem Classificacao

## Boot Orchestrator - semantica ainda presa no Spring

Proximo PR da trilha do boot:

- mover para o `core` os enums e snapshots que ainda representam semantica de boot:
  - `BootRunStatus`
  - `BootPhaseStatus`
  - `BootRunSnapshot`
  - `BootPhaseSnapshot`
  - `Phase2Mode`
  - `Phase2AccountQueryPolicy`
- manter no `spring-application` apenas o que for detalhe de framework/adaptacao:
  - `@ConfigurationProperties`
  - `ApplicationReadyEvent`
  - metricas
  - `BootFailFastEvent`
- objetivo: consolidar no `core` o estado e a politica do boot, deixando no `spring` apenas lifecycle, wiring e observabilidade tecnica
