# Refatoração de Configurações - Resumo

## Problema Identificado

As propriedades no `application-mock.yml` não estavam especializadas por tipo de feed:
- `message-delay` aplicava a todos os feeds, mas só faz sentido para strict e random
- `price-simulation` aplicava globalmente, mas só é usada pelo random feed  
- A propriedade `websocket` parecia não ser utilizada corretamente

## Solução Implementada

### 1. Especialização por Tipo de Feed

**Antes (genérico):**
```yaml
mock:
  exchange:
    message-delay:
      price-updates: 1000    # aplicava a todos
    price-simulation:
      volatility: 0.02       # aplicava a todos  
```

**Depois (especializado):**
```yaml
mock:
  exchange:
    feed:
      strict:
        message-delay:
          price-updates: 1000    # específico para strict
      random:
        message-delay:
          price-updates: 800     # específico para random (mais rápido)
        price-simulation:
          volatility: 0.02       # apenas para random
      real:
        # sem configurações artificiais - usa timing real
```

### 2. Arquivos de Configuração Especializados

Criados arquivos específicos para cada caso de uso:

- **`application-mock-strict.yml`** - Feed usando arquivos mock-data
- **`application-mock-random.yml`** - Feed com dados aleatórios 
- **`application-mock-real-feed.yml`** - Feed conectando a exchanges reais

### 3. Propriedades Claramente Documentadas

**Propriedades por Tipo de Feed:**

| Propriedade | Strict | Random | Real | Descrição |
|-------------|--------|--------|------|-----------|
| `data-folder` | ✅ | ✅ | ❌ | Pasta dos arquivos mock |
| `message-delay` | ✅ | ✅ | ❌ | Timing de simulação |
| `price-simulation` | ❌ | ✅ | ❌ | Parâmetros de volatilidade |
| `url` | ❌ | ❌ | ✅ | WebSocket da exchange real |
| `exchange` | ❌ | ❌ | ✅ | Nome da exchange real |

**Propriedades Comuns:**
- `orders` - Comportamento de execução de ordens (todos os feeds)
- `market-conditions` - Slippage, liquidez (strict e random apenas)
- `websocket` - Configuração do MockWebSocketAdapter (todos os feeds)

### 4. Refatoração do Código

**MockExchangeProperties.java:**
- Moveu `message-delay` para dentro de `StrictFeed` e `RandomFeed`
- Moveu `price-simulation` para dentro de `RandomFeed`  
- Adicionou métodos de conveniência para backward compatibility
- Removeu propriedades duplicadas do nível raiz

**Métodos de Conveniência:**
```java
public MessageDelay getMessageDelay() {
    if (feed.getStrict().isEnable()) {
        return feed.getStrict().getMessageDelay();
    } else if (feed.getRandom().isEnable()) {
        return feed.getRandom().getMessageDelay();
    }
    return new MessageDelay(); // fallback
}
```

### 5. Esclarecimento da Propriedade WebSocket

**Antes:** Parecia não ser utilizada
**Depois:** Claramente documentada como configuração do MockWebSocketAdapter

```yaml
websocket:
  exchange: MOCK                     # Usa MockWebSocketAdapter sempre
  url: "ws://mock.exchange.local"    # Placeholder para logs
  connection-timeout: 5000           # Timeout para conexão
  max-retries: 3                     # Retry para reconexão  
```

**Para feed real:** A URL real está em `mock.exchange.feed.real.url`

## Benefícios

### 🎯 **Clareza**
- Cada feed type tem suas configurações específicas
- Propriedades irrelevantes não aparecem onde não se aplicam
- Documentação clara do que cada propriedade faz

### ⚡ **Performance**  
- Random feed pode ter timings mais rápidos (800ms vs 1000ms)
- Real feed não tem delays artificiais
- Configurações otimizadas por caso de uso

### 🛠️ **Manutenibilidade**
- Fácil adicionar novas propriedades específicas por feed
- Backward compatibility mantida
- Configurações bem organizadas e documentadas

### 📚 **Usabilidade**
- Arquivos de exemplo específicos para cada cenário
- Comentários explicativos em cada configuração
- Guia claro de qual arquivo usar para qual propósito

## Como Usar

### Desenvolvimento Local (Dados Mock)
```bash
./gradlew bootRun --args="--spring.config.additional-location=application-mock-strict.yml"
```

### Desenvolvimento com Variações
```bash  
./gradlew bootRun --args="--spring.config.additional-location=application-mock-random.yml"
```

### Desenvolvimento com Dados Reais
```bash
./gradlew bootRun --args="--spring.config.additional-location=application-mock-real-feed.yml"
```

## Resultado Final

✅ **Propriedades especializadas** por tipo de feed  
✅ **Configurações otimizadas** para cada caso de uso  
✅ **Documentação clara** e completa  
✅ **Backward compatibility** mantida  
✅ **Arquivos de exemplo** para todos os cenários  
✅ **Sem duplicação** de configurações