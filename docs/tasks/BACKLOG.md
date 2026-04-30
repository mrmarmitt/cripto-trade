# Backlog — Integração Binance Testnet

Trilha de tarefas para habilitar operação com a API da Binance (testnet), pré-requisito para staging e soak test.

## Status das tarefas

| ID  | Título                          | Complexidade | Responsável | Status        |
|-----|---------------------------------|--------------|-------------|---------------|
| T1  | Transaction Recovery            | Alta         | Mateus      | Concluído     |
| T2  | Configuração de perfil testnet  | Média        | Codex       | Concluído     |
| T3  | HMAC-SHA256 Signing             | Alta         | Claude      | Pendente      |
| T4  | User Data Stream                | Alta         | Claude      | Pendente      |
| T5  | REST API Binance                | Alta         | Claude      | Pendente      |
| T6  | Exchange Symbol Filters         | Média        | Codex       | Pendente      |
| T7  | Boot Readiness real             | Média        | Codex       | Pendente      |
| T8  | Kill Switch                     | Média        | Codex       | Pendente      |
| T9  | Observabilidade Docker Compose  | Média        | Codex       | Pendente      |
| T10 | Otimização da suíte de integração | Média      | Codex       | Pendente      |

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

## Critério de pronto da trilha

- Aplicação conecta na Binance testnet via WebSocket e recebe price updates.
- Ordens são submetidas via REST com assinatura válida.
- Fills, cancels e rejects chegam via User Data Stream e são processados corretamente.
- Boot recovery consulta ordens abertas na testnet via REST.
- Nenhuma ordem é enviada violando filtros de símbolo (stepSize, minNotional, tickSize).
- Kill switch interrompe todos os runners sem efeito financeiro residual.
- Métricas visíveis no Grafana durante staging.
