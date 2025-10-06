# FEITO:
------------------------------------------------------------------------------------------------------------------------
# PENDENTE:

Criar o useCase de conexão do websocket.
Esse useCase deve receber uma implementacao do WebSocketPort.
Esse useCase deve chamar a função para estabelecer a conexao.
Esse useCase deve validar se a conexao foi estabelecida com sucesso.
Esse useCase vai registrar um consumidor generico que tambem deve ser implementado.
Ao final do processo, a função execute do useCase deve retornar o websocket conectado para que outra parte do sistema gerencie as conexões ativas

Pode alterar a interface WebSocketPort se necessário.
Sugestão de nome para o useCase -> EstablishWebSocketConnectionUseCase ou qualquer coisa nesse sentido.


    **Restrições:**
    
    * **Nenhuma biblioteca de conexão** deve ser incluída neste módulo.
      * **Nenhum teste unitário** deve ser criado.
      * **O código deve ser estritamente contido dentro do módulo `core`**.
      * **Não implemente nenhuma estratégia específica**, apenas a estrutura de interfaces e DTOs.

No módulo `core`, crie a estrutura de um caso de uso para estabelecer uma conexão WebSocket.

**Componentes Necessários:**

1.  **Interface `WebSocketPort`:**
    * Crie uma interface que define o contrato para a porta de conexão WebSocket.
    * Ela deve ter um método, por exemplo, `connect()`, que aceita um consumidor de mensagens.
    * O retorno deste método deve ser a própria conexão estabelecida, para que possa ser gerenciada externamente.

2.  **Interface `MessageConsumer`:**
    * Crie uma interface que define o contrato de um consumidor de mensagens genérico.
    * Esta interface deve ter um método, como `onMessage(message: String)`, para processar as mensagens recebidas do WebSocket.

3.  **Caso de Uso `EstablishWebSocketConnectionUseCase`:**
    * Crie a classe do caso de uso.
    * O construtor deve receber uma implementação de `WebSocketPort` via injeção de dependência.
    * Crie um método `execute()` que será o ponto de entrada principal.
    * Dentro do método `execute()`, o caso de uso deve:
      a. Receber uma implementação de `MessageConsumer` como parâmetro.
      b. Chamar o método `connect()` da `WebSocketPort`, passando o `MessageConsumer`.
      c. Retornar a conexão estabelecida.

**Requisitos e Fluxo:**

* O caso de uso deve ser o orquestrador do processo de conexão.
* O caso de uso não deve ter lógica de negócio, apenas a responsabilidade de conectar e registrar o consumidor.
* A validação de sucesso da conexão será tratada pelo `WebSocketPort`. O caso de uso simplesmente retorna o objeto de conexão.

**Restrições:**

* **Nenhuma biblioteca de conexão** deve ser incluída neste módulo.
* **Nenhum teste unitário** deve ser criado.
* **O código deve ser estritamente contido dentro do módulo `core`**.
* **Não implemente o `WebSocketPort`**, `MessageConsumer` ou qualquer outra classe concreta, apenas as interfaces e o caso de uso.

------------------------------------------------------------------------------------------------------------------------