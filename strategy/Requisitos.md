# FEITO:
------------------------------------------------------------------------------------------------------------------------
# PENDENTE:

1. No módulo `strategy`, crie uma estrutura de pacotes para armazenar interfaces e DTOs necessários para a criação de estratégias de trading.

    **Estrutura de Interfaces e DTOs:**

    1.  **Interfaces de Estratégia:**
        * Crie uma interface chamada `TradingStrategy` que define o contrato para qualquer estratégia de trading.
        * A interface deve ter um método principal, como `executeStrategy()`, que recebe os dados de mercado e retorna um resultado.
        * O retorno deste método deve ser um DTO que o módulo de aplicação irá processar.

    2.  **DTOs para Estratégia:**
       * Crie classes de DTOs específicas para o módulo `strategy`, que serão usadas pela `TradingStrategy`.
       * Exemplo: `StrategyInputData` (para os dados de entrada, como preço e volume) e `StrategyOutputResult` (para o resultado da estratégia, como a decisão de compra, venda ou espera).
       * Esses DTOs devem ser mapeados a partir dos DTOs do `adapter-common` no módulo de aplicação.

    **Requisitos para a Estratégia:**
    
    * **Acesso a Dados:** A estratégia deve ser capaz de receber todas as informações necessárias via parâmetros, permitindo que execute a lógica da operação.
      * **Retorno Explícito:** A interface deve garantir que o método de execução sempre tenha um retorno, permitindo que o módulo de aplicação decida o que fazer com a resposta.
      * **Configuração Dinâmica:** A implementação da estratégia deve ser capaz de receber parâmetros de configuração no construtor.
      * **Classes de Configuração:** A estratégia pode ter sua própria classe de configuração, o que permite o gerenciamento de parâmetros externos (como de um banco de dados ou requisição REST).

    **Restrições:**
    
    * **Nenhuma biblioteca de conexão** deve ser incluída neste módulo.
      * **Nenhum teste unitário** deve ser criado.
      * **O código deve ser estritamente contido dentro do módulo `strategy`**.
      * **Não implemente nenhuma estratégia específica**, apenas a estrutura de interfaces e DTOs.


------------------------------------------------------------------------------------------------------------------------