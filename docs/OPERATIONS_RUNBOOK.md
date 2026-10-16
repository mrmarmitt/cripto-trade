# Operations Runbook — CTrade

## Propósito

Este documento define **como operar, monitorar e manter** o sistema em produção. É o guia de referência para quem precisa entender o comportamento do sistema em runtime, responder a incidentes e garantir a saúde operacional.

## Escopo

- SLA e requisitos não-funcionais: latência máxima, throughput por Runner, tempo de reconciliação
- Audit Trail: eventos a serem logados, retenção, rastreabilidade de decisões e transições de estado
- Security Boundaries: isolamento entre Runners, limites de recursos, proteção de conexões
- Testing Strategy: mocks entre agregados, testes de integração, simulação de falhas, testes de carga
- Gestão de WebSocket: conexões compartilhadas vs por Runner, rate limits, reconexão

## Relação com outros documentos

- **[Blueprint](BLUEPRINT.md):** Define a arquitetura que este runbook monitora
- **Implementation Guide:** Fornece as decisões técnicas que impactam o comportamento operacional

## Pré-requisitos de conta na exchange

### Binance: pagamento de taxas com BNB desligado (T46)

A opção **"Usar BNB para pagar taxas"** deve ficar **desligada** na conta (testnet e produção). O
sistema contabiliza a comissão cobrada no ativo recebido (base na compra, cotação na venda) e não
converte fee de terceiro ativo. Se a opção for ligada, cada fill chega com fee em BNB: o sistema
segue operando, mas a fee fica fora do PnL e é registrada em log `ERROR` com
`unsupported BUY fee asset` / `unsupported SELL fee asset` — sinal para desligar a opção.

