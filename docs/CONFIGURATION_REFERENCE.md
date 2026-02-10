# Configuration Reference — CTrade

## Propósito

Este documento serve como **referência centralizada** para todas as configurações do sistema. Define onde cada parâmetro é configurado, quais são os valores válidos e como as configurações impactam o comportamento dos componentes.

## Escopo

- Symbol Config: tick size, min/max quantity, lot size, precisão decimal
- Exchange Config: API keys, endpoints, rate limits, timeouts de conexão
- Origem dos dados: configurações estáticas vs carregadas dinamicamente da exchange
- Validação pré-envio: quais regras são verificadas antes de despachar uma ordem

## Relação com outros documentos

- **Blueprint (BLUEPRINT_V9.md):** Define quem é dono de cada configuração (Runner vs Portfolio)
- **Implementation Guide:** Detalha como as configurações são consumidas no código
- **Operations Runbook:** Usa esta referência para troubleshooting e ajustes em produção
