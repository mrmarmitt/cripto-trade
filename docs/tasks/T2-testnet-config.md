# T2 — Configuração de perfil testnet

**Complexidade:** Média  
**Responsável:** Codex  
**Dependências:** T1  
**Status:** Concluído

---

## Descrição

O adapter Binance atual tem URLs hardcoded para produção (`wss://stream.binance.com:9443`) na classe `Configuration.java`. API keys também não têm ponto de injeção.

Esta tarefa parametriza todas as URLs e credenciais do adapter Binance, cria um perfil Spring para testnet e define o contrato de variáveis de ambiente necessárias para staging.

A Binance testnet usa o domínio `testnet.binance.vision` tanto para WebSocket quanto para REST. As API keys da testnet são distintas das de produção e obtidas em [testnet.binance.vision](https://testnet.binance.vision/).

---

## Decisão de design — o que significa "Binance ativo"

`InMemoryExchangeAdapterRepository.initExchangeAdapters()` instancia todos os adapters incondicionalmente no `@PostConstruct`. Colocar validação rígida de credenciais no construtor do `BinanceExchangeAdapter` quebraria ambientes que só usam MOCK, pois o adapter Binance é instanciado mesmo nesses casos.

**Regra adotada: a presença de qualquer uma das variáveis de credencial é o sinal de ativação; ambas devem estar presentes para a configuração ser válida.**

```
Ambas ausentes          → adapter Binance não registrado; aplicação sobe normalmente
Ambas presentes e válidas → adapter Binance registrado; credenciais validadas no startup
Apenas uma presente     → falha no startup com mensagem clara (configuração incompleta)
```

**Como implementar:** `BinanceAdapterConfiguration` usa `@ConditionalOnExpression` que ativa o bean quando pelo menos uma das propriedades está definida. Dentro do bean, `@Validated` com `@NotBlank` em ambos os campos garante que a configuração parcial falha com mensagem clara no startup:

```java
// BinanceAdapterConfiguration.java
@Configuration
// Ativa quando qualquer uma das credenciais está presente — a validação interna cobre o caso parcial
@ConditionalOnExpression("!'${binance.api-key:}'.isBlank() || !'${binance.api-secret:}'.isBlank()")
class BinanceAdapterConfiguration {

    @Bean
    @Validated  // dispara ConstraintViolationException no startup se algum campo falhar
    BinanceExchangeAdapter binanceExchangeAdapter(BinanceProperties props) {
        return new BinanceExchangeAdapter(props);
    }
}

// BinanceProperties.java
@ConfigurationProperties(prefix = "binance")
class BinanceProperties {
    @NotBlank(message = "binance.api-key é obrigatório quando binance.api-secret está definido")
    private String apiKey;

    @NotBlank(message = "binance.api-secret é obrigatório quando binance.api-key está definido")
    private String apiSecret;

    // wsBaseUrl, restBaseUrl com defaults de produção
}
```

O repositório passa a receber os adapters por injeção (`List<ExchangeStreamingPort>`) em vez de instanciá-los diretamente, registrando apenas os que o Spring criou.

**Wiring dos demais adapters:** `MockExchangeAdapter` e `CoinbaseExchangeAdapter` também precisam ser convertidos em `@Bean` dentro de suas respectivas `@Configuration` classes. MOCK deve ser sempre registrado (sem condicional); Coinbase pode seguir o mesmo padrão condicional de Binance quando chegar a hora. O `@PostConstruct` do repositório deixa de existir.

---

## Escopo técnico

**Arquivos a criar:**
- `spring-application/.../config/exchange/BinanceAdapterConfiguration.java` — bean condicional com `@ConditionalOnExpression`
- `spring-application/.../config/exchange/BinanceProperties.java` — `@ConfigurationProperties` com `@NotBlank` em ambos os campos de credencial
- `spring-application/src/main/resources/application-testnet.yml` — perfil testnet

**Arquivos a modificar:**
- `adapter-binance/.../Configuration.java` — remover URLs hardcoded; receber por construtor
- `spring-application/.../config/exchange/BinanceExchangeAdapter.java` — receber configuração por construtor
- `spring-application/.../config/exchange/MockExchangeAdapter.java` — passar a ser registrado por `MockAdapterConfiguration`
- `spring-application/.../repository/InMemoryExchangeAdapterRepository.java` — remover `@PostConstruct`; receber `List<ExchangeStreamingPort>` por injeção
- `spring-application/src/main/resources/application.yml` — adicionar bloco `binance:` com defaults de produção
- `docker-compose.yml` — documentar variáveis de ambiente esperadas (valores de exemplo, sem secrets reais)

**Variáveis de ambiente:**
```
BINANCE_API_KEY           (sem padrão; ausência = adapter não registrado)
BINANCE_API_SECRET        (sem padrão; ausência = adapter não registrado)
```

As URLs não são variáveis de ambiente — são gerenciadas via YAML. O `application.yml` define os defaults de produção e o `application-testnet.yml` sobrescreve com URLs da testnet ao ativar o perfil `testnet`. Expor URLs como env vars criaria um problema de precedência: env vars (rank 6) sobrescrevem profile YAMLs (rank 7), tornando o perfil testnet ineficaz.

---

## Critérios de aceitação

1. `Configuration.java` não contém mais nenhuma URL hardcoded; recebe valores por construtor ou propriedades injetadas.
2. `application.yml` define os valores padrão de produção para todas as URLs Binance.
3. `application-testnet.yml` sobrescreve com URLs da testnet; ao ativar o perfil `testnet` (`-Dspring.profiles.active=testnet`), a aplicação usa as URLs corretas sem nenhuma alteração de código.
4. Se ambas as credenciais estiverem presentes e não vazias, o adapter Binance é registrado no startup. Se apenas uma estiver presente, a aplicação falha no startup com mensagem que identifica qual campo está faltando. Se ambas estiverem ausentes, a aplicação sobe sem registrar o adapter Binance e sem erro.
5. `docker-compose.yml` documenta as variáveis de ambiente esperadas (valores de exemplo, sem secrets reais).
6. Nenhuma URL ou credencial Binance é hardcoded em qualquer arquivo commitado.
7. A aplicação sobe normalmente sem nenhuma variável Binance definida; o adapter MOCK funciona normalmente nesse cenário.
8. `InMemoryExchangeAdapterRepository` não instancia nenhum adapter diretamente; recebe todos por injeção Spring. Isso inclui MOCK e Coinbase — todos os adapters são beans Spring declarados em suas respectivas `@Configuration` classes.

---

## Testes de integração obrigatórios

| Cenário                                                  | Verificações obrigatórias                                                                                                                                                                                 |
|----------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Ambas as credenciais presentes e válidas                 | `hasAdapter("BINANCE") == true`; `hasAdapter("MOCK") == true`                                                                                                                                             |
| `BINANCE_API_KEY` presente, `BINANCE_API_SECRET` ausente | Startup falha; mensagem de erro referencia `binance.api-secret`                                                                                                                                           |
| `BINANCE_API_SECRET` presente, `BINANCE_API_KEY` ausente | Startup falha; mensagem de erro referencia `binance.api-key`                                                                                                                                              |
| Ambas as credenciais ausentes                            | Aplicação sobe; `hasAdapter("BINANCE") == false`; `hasAdapter("MOCK") == true`                                                                                                                            |
| Perfil `testnet` ativo com credenciais presentes         | A URL efetiva usada pelo URL builder do adapter (WebSocket e REST) contém `testnet.binance.vision`; verificar via campo exposto no adapter ou propriedade resolvida — não apenas o binding da propriedade |
