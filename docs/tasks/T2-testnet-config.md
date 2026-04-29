# T2 — Configuração de perfil testnet

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T1  
**Status:** Pendente

---

## Descrição

O adapter Binance atual tem URLs hardcoded para produção (`wss://stream.binance.com:9443`) na classe `Configuration.java`. API keys também não têm ponto de injeção.

Esta tarefa parametriza todas as URLs e credenciais do adapter Binance, cria um perfil Spring para testnet e define o contrato de variáveis de ambiente necessárias para staging.

A Binance testnet usa o domínio `testnet.binance.vision` tanto para WebSocket quanto para REST. As API keys da testnet são distintas das de produção e obtidas em [testnet.binance.vision](https://testnet.binance.vision/).

---

## Decisão de design — o que significa "Binance ativo"

`InMemoryExchangeAdapterRepository.initExchangeAdapters()` instancia todos os adapters incondicionalmente no `@PostConstruct`. Colocar validação rígida de credenciais no construtor do `BinanceExchangeAdapter` quebraria ambientes que só usam MOCK, pois o adapter Binance é instanciado mesmo nesses casos.

**Regra adotada: a presença das variáveis de ambiente é o próprio sinal de ativação.**

```
BINANCE_API_KEY e BINANCE_API_SECRET ambos presentes → adapter Binance registrado e credenciais validadas no startup
um ou ambos ausentes                                 → adapter Binance não registrado; aplicação sobe normalmente
um presente e o outro ausente                        → falha no startup com mensagem clara (configuração incompleta)
```

**Como implementar:** refatorar o `@PostConstruct` para receber adapters por injeção Spring em vez de instanciá-los diretamente. O `BinanceExchangeAdapter` passa a ser um `@Bean` condicional:

```java
// BinanceAdapterConfiguration.java
@Configuration
@ConditionalOnProperty(name = "binance.api-key")  // bean só criado quando a propriedade existir
class BinanceAdapterConfiguration {

    @Bean
    BinanceExchangeAdapter binanceExchangeAdapter(BinanceProperties props) {
        // validação de credenciais acontece aqui, no startup do Spring
        return new BinanceExchangeAdapter(props);
    }
}
```

O repositório recebe os adapters disponíveis por injeção (`@Autowired(required = false)` ou `List<ExchangeStreamingPort>`), registrando apenas os que foram criados.

---

## Escopo técnico

**Arquivos principais:**
- `adapter-binance/src/main/java/com/marmitt/binance/Configuration.java` — remover URLs hardcoded; receber por construtor
- `spring-application/src/main/resources/application.yml` — adicionar bloco `binance:`
- `spring-application/src/main/resources/application-testnet.yml` — novo arquivo de perfil
- `docker-compose.yml` — adicionar variáveis de ambiente para API keys
- `spring-application/src/main/java/com/marmitt/application/spring/config/exchange/BinanceExchangeAdapter.java` — receber configuração por construtor
- `spring-application/src/main/java/com/marmitt/application/spring/config/BinanceAdapterConfiguration.java` — novo; bean condicional via `@ConditionalOnProperty`
- `spring-application/src/main/java/com/marmitt/application/spring/repository/InMemoryExchangeAdapterRepository.java` — receber adapters por injeção em vez de instanciar no `@PostConstruct`

**Variáveis de ambiente a definir:**
```
BINANCE_WS_BASE_URL       wss://stream.binance.com:9443  (padrão produção)
BINANCE_REST_BASE_URL     https://api.binance.com        (padrão produção)
BINANCE_API_KEY           (sem padrão; ausência = adapter não registrado)
BINANCE_API_SECRET        (sem padrão; ausência = adapter não registrado)
```

**Valores para testnet:**
```
BINANCE_WS_BASE_URL       wss://testnet.binance.vision
BINANCE_REST_BASE_URL     https://testnet.binance.vision
```

---

## Critérios de aceitação

1. `Configuration.java` não contém mais nenhuma URL hardcoded; recebe valores por construtor ou propriedades injetadas.
2. `application.yml` define os valores padrão de produção para todas as URLs Binance.
3. `application-testnet.yml` sobrescreve com URLs da testnet; ao ativar o perfil `testnet` (`-Dspring.profiles.active=testnet`), a aplicação usa as URLs corretas sem nenhuma alteração de código.
4. Se `BINANCE_API_KEY` e `BINANCE_API_SECRET` estiverem ambos presentes, o adapter Binance é registrado e as credenciais são validadas no startup (não nulas, não vazias). Se um estiver presente e o outro ausente, a aplicação falha com mensagem clara. Se ambos estiverem ausentes, a aplicação sobe sem registrar o adapter Binance — sem erro.
5. `docker-compose.yml` documenta as variáveis de ambiente esperadas (valores de exemplo, sem secrets reais).
6. Nenhuma URL ou credencial Binance é hardcoded em qualquer arquivo commitado.
7. A aplicação sobe normalmente sem nenhuma variável Binance definida; o adapter MOCK funciona normalmente nesse cenário.
8. `InMemoryExchangeAdapterRepository` não instancia nenhum adapter diretamente; recebe todos por injeção Spring.

## Testes de integração obrigatórios

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Ambas as variáveis presentes e válidas | Adapter Binance registrado no `ExchangeAdapterRepositoryPort`; `hasAdapter("BINANCE") == true` |
| Uma variável presente, outra ausente | Startup falha com `ApplicationContextException` ou similar; mensagem indica qual variável está faltando |
| Ambas as variáveis ausentes | Aplicação sobe; `hasAdapter("BINANCE") == false`; `hasAdapter("MOCK") == true` |
| Perfil `testnet` ativo com variáveis presentes | URLs resolvidas apontam para `testnet.binance.vision` |
