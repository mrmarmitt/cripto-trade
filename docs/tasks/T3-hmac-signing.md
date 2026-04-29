# T3 — HMAC-SHA256 Signing

**Complexidade:** Alta  
**Responsável:** Claude  
**Dependências:** T2  
**Status:** Pendente

---

## Descrição

Toda chamada autenticada na Binance — tanto via REST quanto via WebSocket API — exige uma assinatura HMAC-SHA256 gerada a partir dos parâmetros da requisição e da API secret.

O `OrderProcessor.java` atual tem um TODO explícito para signing. Sem esta tarefa, nenhuma ordem pode ser enviada, nenhuma query autenticada pode ser feita e o User Data Stream não pode ser iniciado (listen key requer autenticação).

Esta tarefa implementa a camada de signing centralizada que será usada por T4 e T5.

---

## Escopo técnico

**Regras de signing da Binance:**

Para REST:
1. Montar a query string com todos os parâmetros, incluindo `timestamp` (epoch ms) e `recvWindow` (opcional, padrão 5000ms).
2. Calcular `signature = HMAC_SHA256(api_secret, query_string)`.
3. Anexar `signature` ao final da query string.
4. Enviar `X-MBX-APIKEY: <api_key>` no header.

Para WebSocket API (order.place, order.cancel, etc.):
1. Montar o objeto `params` com todos os campos, incluindo `timestamp` e `apiKey`.
2. Calcular `signature = HMAC_SHA256(api_secret, canonical_string)` onde canonical string é construída a partir dos params em ordem alfabética.
3. Inserir `signature` dentro de `params`.

**Arquivos a criar/modificar:**
- `adapter-binance/src/main/java/com/marmitt/binance/auth/BinanceRequestSigner.java` — novo, responsável único pelo signing
- `adapter-binance/src/main/java/com/marmitt/binance/processor/send/OrderProcessor.java` — usar o signer
- `adapter-binance/src/main/java/com/marmitt/binance/processor/send/CancelOrderProcessor.java` — usar o signer

**Restrições de implementação:**
- `BinanceRequestSigner` não deve ter estado mutável; recebe api_secret por construtor.
- O api_secret nunca deve ser logado. Logar apenas o resultado de signing ou `[REDACTED]`.
- `timestamp` deve ser gerado no momento do signing, não antes (drift de clock invalida a requisição).
- Considerar `recvWindow` configurável para ambientes com clock menos preciso.

---

## Critérios de aceitação

1. `BinanceRequestSigner` implementa signing HMAC-SHA256 conforme documentação da Binance para REST e WebSocket API.
2. `OrderProcessor` usa o signer e não contém mais o TODO de autenticação.
3. `CancelOrderProcessor` usa o signer da mesma forma.
4. O API secret nunca aparece em logs; tentativa de log resulta em `[REDACTED]`.
5. Signing com `api_secret` vazio ou nulo lança exceção com mensagem clara no startup, não em tempo de requisição.
6. O `timestamp` é gerado no momento da assinatura e não reaproveitado entre chamadas.

## Testes de integração obrigatórios

Usar MockWebServer (OkHttp3) ou WireMock para simular a API da Binance sem depender de conectividade externa.

| Cenário | Verificações obrigatórias |
|---------|--------------------------|
| Requisição REST assinada com vetor de teste fixo da documentação Binance | Signature gerada bate com o valor esperado documentado; header `X-MBX-APIKEY` presente na requisição capturada pelo mock server |
| Requisição WebSocket de ordem assinada | Payload JSON contém `apiKey`, `signature` e `timestamp` nos campos corretos; signature válida para o conjunto de params |
| API secret ausente no startup | Aplicação falha no startup com mensagem clara antes de qualquer requisição ser feita |
| Duas chamadas consecutivas ao signer | `timestamp` é diferente entre as duas chamadas (não reaproveitado) |
