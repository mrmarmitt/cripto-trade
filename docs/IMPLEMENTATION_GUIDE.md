# Implementation Guide — CTrade

## Propósito

Este documento detalha **como construir** cada componente definido no Blueprint. Enquanto o Blueprint define "o quê" e "por quê", este guia responde "como" — com decisões técnicas de implementação, padrões de código e contratos entre componentes.

## Escopo

- Comunicação entre agregados: síncrona vs assíncrona, garantias de entrega, retry e timeout
- Consistência de dados: eventual consistency, window máximo, impacto de saldo desatualizado
- Granularidade de Position: definição por modo (Hedging vs Netting), composição interna
- Escopo das Accounting Policies: aplicação em aberturas vs fechamentos, mutabilidade dinâmica
- Capital Request: abstração (chamada direta vs serviço), sincronia
- Validação de Trade Parameters: níveis de validação, configuração, tratamento de falha
- Context Injection: composição do contexto injetado na Strategy
- Cooldown: granularidade (por símbolo, direção, global), regras de reset

## Relação com outros documentos

- **Blueprint (BLUEPRINT_V9.md):** Define a arquitetura que este guia implementa
- **Operations Runbook:** Consome as decisões técnicas deste guia para definir procedimentos operacionais
