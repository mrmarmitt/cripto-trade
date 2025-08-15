# Mock Exchange Integration

Este pacote contém toda a implementação de simulação para desenvolvimento e testes.

## Estrutura do Pacote

```
mock/
├── MockExchangeAdapter.java     # Adapter simulado para operações de trading
├── MockWebSocketAdapter.java    # Adapter simulado para WebSocket
└── README.md                    # Este arquivo
```

## Componentes

### MockWebSocketAdapter
- **Responsabilidade**: Simula conexão WebSocket para desenvolvimento e testes
- **Features**: Simulação de preços, gerenciamento de subscrições, simuladores automáticos
- **Ativação**: `websocket.exchange=MOCK` (padrão)

### MockExchangeAdapter  
- **Responsabilidade**: Simula operações de trading (compra/venda)
- **Features**: Operações simuladas, validação de parâmetros

## Configuração

```yaml
websocket:
  exchange: MOCK  # Usa implementação mock para desenvolvimento
  url: "ws://localhost:8080/mock"
  connection-timeout: PT5S
  max-retries: 3
```

## Exemplo de Uso

```java
@Autowired
private MockWebSocketAdapter mockAdapter;

// O adapter mock é automaticamente ativado quando websocket.exchange=MOCK
// Inicia simuladores automaticamente para desenvolvimento
```

## Simuladores Automáticos

O MockWebSocketAdapter inicia automaticamente simuladores para:
- **BTC/USD**: Preços variando entre $40,000-60,000
- **ETH/USD**: Preços variando entre $2,500-3,500
- **Updates de Ordem**: Status simulados para ordens ativas

## Vantagens para Desenvolvimento

- **Sem dependências externas**: Funciona offline
- **Dados consistentes**: Preços previsíveis para testes
- **Baixa latência**: Respostas instantâneas
- **Debugging**: Logs detalhados de todas as operações

## Futuras Extensões

Para adicionar novos simuladores, siga este padrão:
- Implemente a interface apropriada (WebSocketPort, ExchangePort)
- Adicione configuração condicional com `@ConditionalOnProperty`
- Mantenha a mesma estrutura de callbacks e listeners