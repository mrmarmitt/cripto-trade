# T16 — ExchangeAdapterDescriptor: Centralizar capabilities por adapter

**Complexidade:** Média  
**Responsável:** Claude  
**Dependências:** T15 (ExchangeOrderPort — seleção de transporte no adapter-binance)  
**Status:** Pendente

---

## Contexto

`InMemoryExchangeAdapterRepository` gerencia capabilities de adapters via 8+ mapas separados, um por tipo de interface:

```
Map<String, ExchangeStreamingPort>      streamingAdapters
Map<String, ExchangeUserStreamPort>     userStreamAdapters
Map<String, UserStreamSessionPort>      userStreamSessionAdapters
Map<String, ExchangeOrderExecutionPort> orderExecutionAdapters
Map<String, ExchangeOrderQueryPort>     orderQueryAdapters
Map<String, ExchangeAccountQueryPort>   accountQueryAdapters
Map<String, ExchangeBootReadinessPort>  bootReadinessAdapters
Map<String, ExchangeOrderPort>          orderPorts
```

O eixo de organização está invertido: a pergunta é "qual exchange implementa X?" quando o modelo mental correto é "o que o Binance consegue fazer?". Isso força o `ExchangeAdapterRepositoryPort` a expor ~20 métodos (`findStreamingByName`, `findOrderExecutionByName`, `registerStreamingAdapter`…) e o registro se baseia em `instanceof` para descobrir as capacidades de cada adapter.

**Separação que guia o refactor:**

| Dado | Onde pertence |
|---|---|
| Capabilities do adapter (streaming, orderPort, REST…) | `ExchangeAdapterDescriptor` |
| Estado de runtime (sessão ativa, despacho bloqueado) | diretamente em `ExchangeAdapterRepositoryPort` |

---

## Design

### `ExchangeAdapterDescriptor` (novo, em core)

Interface em core que agrupa as capabilities de um exchange. Métodos de capability lançam `UnsupportedCapabilityException` quando não disponíveis — o chamador pode checar `hasOrderExecution()` antes ou tratar a exceção.

```java
// core/.../ports/outbound/exchange/ExchangeAdapterDescriptor.java
public interface ExchangeAdapterDescriptor {
    String exchangeName();

    ExchangeStreamingPort streaming();
    ExchangeUserStreamPort userStream();
    UserStreamSessionPort userStreamSession();
    ExchangeOrderPort orderPort();

    // capabilities opcionais — lança UnsupportedCapabilityException se ausente
    ExchangeOrderExecutionPort orderExecution();
    ExchangeOrderQueryPort orderQuery();
    ExchangeAccountQueryPort accountQuery();
    ExchangeBootReadinessPort bootReadiness();
}
```

`UnsupportedCapabilityException` é uma nova unchecked exception em core:
```java
// core/.../exception/UnsupportedCapabilityException.java
public class UnsupportedCapabilityException extends RuntimeException {
    public UnsupportedCapabilityException(String exchangeName, String capability) {
        super("Exchange '" + exchangeName + "' does not support capability: " + capability);
    }
}
```

### `ExchangeAdapterRepositoryPort` simplificado (core)

```java
public interface ExchangeAdapterRepositoryPort {

    // descriptor access
    Optional<ExchangeAdapterDescriptor> findAdapter(String exchangeName);
    boolean hasAdapter(String exchangeName);
    Set<String> getAllExchangeNames();

    // session lifecycle (keyed por UUID, não por exchange)
    void storeActiveSession(UUID connectionId, UserStreamSession session);
    Optional<UserStreamSession> findActiveSession(UUID connectionId);
    void removeActiveSession(UUID connectionId);

    // dispatch control
    void blockDispatch(String exchangeName);
    void unblockDispatch(String exchangeName);
    boolean isDispatchBlocked(String exchangeName);

    // portfolio mapping
    void registerPortfolioByAdapter(String exchangeName, UUID portfolioId);
    Optional<String> findExchangeByPortfolio(UUID portfolioId);
}
```

De ~20 métodos para ~12. Os métodos `register*` individuais desaparecem — a montagem do descriptor acontece no Spring config, não no repositório.

### `InMemoryExchangeAdapterRepository` reescrito (spring-application)

```java
@Repository
public class InMemoryExchangeAdapterRepository implements ExchangeAdapterRepositoryPort {
    private final Map<String, ExchangeAdapterDescriptor> adapters = new ConcurrentHashMap<>();
    private final Map<UUID, UserStreamSession> activeSessions = new ConcurrentHashMap<>();
    private final Set<String> blockedDispatches = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> adapterByPortfolio = new ConcurrentHashMap<>();

    public InMemoryExchangeAdapterRepository(List<ExchangeAdapterDescriptor> descriptors) {
        descriptors.forEach(d -> adapters.put(d.exchangeName().toUpperCase(), d));
    }
    // ...
}
```

De 8+ mapas para 1.

### Descriptors em spring-application

Cada config de exchange cria um bean `ExchangeAdapterDescriptor` (classe concreta em spring-application ou em adapter-binance) montado a partir dos beans de adapter existentes:

```java
// BinanceMarketStreamConfiguration
@Bean
public ExchangeAdapterDescriptor binanceAdapterDescriptor(
    BinanceMarketStreamAdapter marketStream,
    BinanceUserStreamAdapter userStream,
    BinanceUserStreamSessionAdapter userStreamSession,
    BinanceOrderAdapter orderAdapter) {
    return DefaultExchangeAdapterDescriptor.builder("BINANCE")
            .streaming(marketStream)
            .userStream(userStream)
            .userStreamSession(userStreamSession)
            .orderPort(orderAdapter)
            .orderExecution(marketStream)
            .orderQuery(marketStream)
            .accountQuery(marketStream)
            .bootReadiness(marketStream)
            .build();
}
```

`DefaultExchangeAdapterDescriptor` é uma implementação concreta de `ExchangeAdapterDescriptor` em spring-application (ou um record em core com builder).

---

## Etapas de implementação

### 1. Novos tipos em core
- `ExchangeAdapterDescriptor` (interface)
- `UnsupportedCapabilityException` (unchecked exception)
- Atualizar `ExchangeAdapterRepositoryPort` (simplificar)

### 2. `DefaultExchangeAdapterDescriptor` em spring-application
- Implementação concreta de `ExchangeAdapterDescriptor` com builder
- Campos `@Nullable` para capabilities opcionais; getter lança `UnsupportedCapabilityException` quando null

### 3. Reescrever `InMemoryExchangeAdapterRepository`
- Um único `Map<String, ExchangeAdapterDescriptor>`
- Constructor recebe `List<ExchangeAdapterDescriptor>` (Spring auto-injeta)

### 4. Criar beans `ExchangeAdapterDescriptor` nos configs Spring
- `BinanceMarketStreamConfiguration` → `binanceAdapterDescriptor`
- `MockAdapterConfiguration` → `mockAdapterDescriptor`
- `CoinbaseAdapterConfiguration` → `coinbaseAdapterDescriptor`

### 5. Atualizar callers de `ExchangeAdapterRepositoryPort`
Os callers atuais passam de `findStreamingByName(exchange).orElseThrow(...)` para
`findAdapter(exchange).orElseThrow(...).streaming()`. Arquivos afetados:
- `core/.../usecase/websocket/ConnectMarketStreamUseCase`
- `core/.../usecase/websocket/ConnectUserStreamUseCase`
- `core/.../handler/connection/PostConnectionEstablishHandler`
- `spring-application/.../infrastructure/connection/LinearBackoffReconnectionStrategy`
- `spring-application/.../infrastructure/exchange/OrderDispatchAdapter`
- Use cases de boot que usam `findBootReadinessByName`
- Use cases de portfolio que usam `getAllExchangeNames`

### 6. Atualizar stubs de teste
Todos os stubs que implementam `ExchangeAdapterRepositoryPort` precisam implementar
apenas os novos métodos (~12 ao invés de ~20). Arquivos afetados:
- `ConnectionLostOrderDispatchGuardTest`
- `LinearBackoffReconnectionStrategyTest`
- `OrderDispatchAdapterTest`
- Testes de integração que usam stub do repositório

---

## Critérios de aceitação

1. `InMemoryExchangeAdapterRepository` tem um único `Map<String, ExchangeAdapterDescriptor>`
2. `ExchangeAdapterRepositoryPort` não expõe mais métodos `register*` nem `findXxxByName` individuais
3. Cada exchange (Binance, Mock, Coinbase) registra um único `ExchangeAdapterDescriptor` bean
4. `UnsupportedCapabilityException` é lançada com mensagem descritiva quando capability ausente
5. Todos os testes existentes passam sem alterar comportamento observável
6. Compilação limpa em todos os módulos

---

## Invariantes preservados

- Sessões ativas (`UserStreamSession`) continuam gerenciadas pelo repositório (keyed por UUID)
- Dispatch blocking continua no repositório (estado de runtime, não capability estática)
- `WebSocketPortRegistryPort` não é afetado — gerencia `WebSocketPort` físicos, não capabilities de negócio
- Nenhuma mudança em comportamento de despacho, conexão ou reconexão — apenas estrutura de acesso ao adapter
