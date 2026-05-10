# Padroes de Implementacao

## Diretrizes gerais

- Implementar no modulo dono da responsabilidade.
- Preferir nomes que expressem dominio, nao tecnologia.
- Evitar classes que misturem orquestracao, calculo e integracao externa.
- Manter entradas e saidas explicitas.

## DTOs e mapeamento

- DTO de provider pertence a `adapter-*`
- DTO de transporte deve ficar na borda apropriada, nao no dominio
- O `core` deve operar com modelos de dominio ou contratos estaveis
- Mapeamentos entre schema externo e dominio devem acontecer nas bordas

## Ports e use cases

- Ports devem expressar o que o negocio precisa, nao como a tecnologia funciona
- Use cases devem encapsular a intencao do negocio
- Interfaces devem ser pequenas e orientadas ao caso de uso

## Strategy

- Estrategias devem ser previsiveis e faceis de testar
- Entrada e saida da estrategia devem ser objetivas
- Nao esconder efeitos colaterais dentro de calculo

## Spring application

- Controllers devem delegar rapidamente
- Services de aplicacao nao devem acumular regra de negocio
- Wiring e adaptacao devem ser mais importantes que decisao de dominio

## Adapters

- Isolar completamente detalhes de provider
- Traduzir schema externo antes de tocar contratos internos
- Nao propagar nomes, enums ou formatos externos para o dominio sem necessidade real

## Mudancas seguras

- Fazer a menor mudanca coerente com a responsabilidade correta
- Evitar refactors transversais sem necessidade comprovada
- Se um nome ou contrato ficar ambiguo, preferir esclarecer agora em vez de empurrar a ambiguidade para frente

