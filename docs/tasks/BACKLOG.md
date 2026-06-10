# Backlog — Integração Binance Testnet

Trilha de tarefas para habilitar operação com a API da Binance (testnet), pré-requisito para staging e soak test.

## Status das tarefas

| ID  | Título                          | Complexidade | Responsável | Status        |
|-----|---------------------------------|--------------|-------------|---------------|
| T1  | Transaction Recovery            | Alta         | Mateus      | Concluído     |
| T2  | Configuração de perfil testnet  | Média        | Codex       | Concluído     |
| T3  | HMAC-SHA256 Signing             | Alta         | Claude      | Concluído     |
| T4  | User Data Stream                | Alta         | Claude      | Concluído     |
| T5  | REST API Binance                | Alta         | Claude      | Concluído     |
| T6  | Exchange Symbol Filters         | Média        | Codex       | Concluído     |
| T7  | Boot Readiness real             | Média        | Codex       | Concluído     |
| T8  | Kill Switch                     | Média        | Codex       | Concluído     |
| T9  | Observabilidade Docker Compose  | Média        | Codex       | Concluído     |
| T10 | Otimização da suíte de integração | Média      | Codex       | Pendente      |
| T11 | Order Quantity Normalization Before Persist | Média | Codex  | Pendente      |
| T12 | Migração User Data Stream → WebSocket API Binance | Alta | Claude | Concluído |
| T13 | Order Placement via WebSocket API Binance | Alta | Claude | Concluído |
| T14 | Refactor Fronteiras Arquiteturais: Transporte e Negócio | Alta | Claude | Concluído |
| T15 | ExchangeOrderPort: Mover seleção de transporte para adapter-binance | Média | Claude | Concluído |
| T16 | ExchangeAdapterDescriptor: Centralizar capabilities por adapter | Média | Claude | Pendente |

## Ordem de execução

```
T1  Transaction Recovery (em andamento)
        ↓
T2  Testnet config          ← desbloqueia tudo
        ↓
T3  HMAC signing            ← base para T4 e T5
       ↓
T4  User Data Stream        T5  REST API
       ↓                         ↓
T6  Symbol Filters  ←────────────┘
       ↓
T7  Boot Readiness real
       ↓
T8  Kill Switch     T9  Observabilidade
       ↓
   staging começa
```

## Ordem de execução

```
T8  Kill Switch     T9  Observabilidade
       ↓
T10  Otimização da suíte de integração
       ↓
T11  Order Quantity Normalization      ← pré-requisito para produção
       ↓
T12  Migração User Data Stream → WS API  ← desbloqueio testnet
       ↓
T13  Order Placement via WS API          ← elimina REST para ordens
       ↓
T14  Refactor fronteiras: transporte     ← reconexão, reações de negócio, HttpClientPort
       ↓
T15  ExchangeOrderPort                  ← seleção de transporte no adapter-binance
       ↓
T16  ExchangeAdapterDescriptor          ← capabilities por adapter, repositório simplificado
       ↓
   produção
```

## Critério de pronto da trilha

- Aplicação conecta na Binance testnet via WebSocket e recebe price updates.
- Ordens são submetidas via REST com assinatura válida.
- Fills, cancels e rejects chegam via User Data Stream e são processados corretamente.
- Boot recovery consulta ordens abertas na testnet via REST.
- Nenhuma ordem é enviada violando filtros de símbolo (stepSize, minNotional, tickSize).
- Kill switch interrompe todos os runners sem efeito financeiro residual.
- Métricas visíveis no Grafana durante staging.
