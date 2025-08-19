# Mock Exchange Integration

Este pacote contém toda a implementação de simulação para desenvolvimento e testes, fornecendo uma alternativa completa às integrações reais para facilitar o desenvolvimento local.

## Estrutura do Pacote

```
mock/
├── MockExchangeAdapter.java     # Adapter simulado para operações de trading
├── MockWebSocketAdapter.java    # Adapter simulado para WebSocket com simuladores
└── README.md                    # Este arquivo
```

## Componentes

### MockWebSocketAdapter
- **Responsabilidade**: Simula conexão WebSocket completa para desenvolvimento e testes
- **Features**: 
  - Simulação automática de preços em tempo real
  - Gerenciamento de subscrições mock
  - Simuladores automáticos para múltiplos pares
  - Observer pattern para notificações
  - Logs detalhados para debugging
- **Arquitetura**: Extende `AbstractWebSocketAdapter` seguindo padrões da infraestrutura
- **Ativação**: `websocket.exchange=MOCK` (padrão)

### MockExchangeAdapter  
- **Responsabilidade**: Simula operações de trading (compra/venda)
- **Features**: 
  - Operações simuladas com validação
  - Simulação de diferentes cenários de mercado
  - Integração com sistema de auditoria

## Configuração

### Profile Mock (Padrão)
```yaml
# application.yml (profile padrão)
websocket:
  exchange: MOCK  # Usa implementação mock para desenvolvimento
  url: "ws://localhost:8080/mock"  # URL mock para logging
  connection-timeout: PT5S
  max-retries: 3
  auto-reconnect: true
```

### Ativação Automática
- Profile padrão → `MockWebSocketAdapter` é carregado automaticamente
- Não requer configuração adicional
- Inicia simuladores automaticamente ao conectar

## Simuladores Automáticos

O MockWebSocketAdapter inicia automaticamente simuladores para:
- **BTC/USD**: Preços variando entre $40,000-60,000 com flutuações realistas
- **ETH/USD**: Preços variando entre $2,500-3,500 com correlação ao BTC
- **Updates de Ordem**: Status simulados baseados em ordens ativas
- **Price Updates**: Notificações automáticas via Observer pattern

## Arquitetura

### Integração com Infrastructure
```java
// Herança da infraestrutura comum
public class MockWebSocketAdapter extends AbstractWebSocketAdapter {
    // Implementação específica mock
}
```

### Observer Pattern
- Usa `PriceUpdateListener` para notificações automáticas
- Integração transparente com `WebSocketService`
- Cache automático via `PriceCacheService`

## Exemplo de Uso

```java
@Autowired
private ExchangeWebSocketAdapter webSocketAdapter; // Injeta MockWebSocketAdapter

// Simuladores iniciam automaticamente
webSocketAdapter.connect(); // Inicia simulação de preços

// Listeners são notificados automaticamente
// Cache é populado com preços simulados
```

## Vantagens para Desenvolvimento

### Produtividade
- **Desenvolvimento offline**: Sem dependências de APIs externas
- **Start rápido**: Sem configuração de credenciais ou conectividade
- **Dados consistentes**: Preços previsíveis para testes reprodutíveis

### Testing
- **Cenários controlados**: Simulação de diferentes condições de mercado
- **Logs detalhados**: Debug facilitado com logs estruturados
- **Performance**: Respostas instantâneas sem latência de rede

### Integração
- **API consistente**: Mesma interface que adaptadores reais
- **Switch transparente**: Mudança de profile sem alteração de código
- **Observer pattern**: Notificações automáticas como em produção

## Extensibilidade

### Adicionar Novos Pares
```java
// Em MockWebSocketAdapter.startSimulators()
simulators.put("ADA/USD", createPriceSimulator("ADA/USD", 0.5, 2.0));
```

### Novos Tipos de Simulação
```java
// Implementar novos simuladores seguindo o padrão
private Runnable createOrderStatusSimulator() {
    return () -> {
        // Lógica de simulação de status
        notifyOrderUpdate(orderUpdate);
    };
}
```

### Integração com Testes
```java
@TestConfiguration
public class MockTestConfig {
    @Bean
    @Primary
    public ExchangeWebSocketAdapter mockAdapter() {
        return new MockWebSocketAdapter(/* config para teste */);
    }
}
```