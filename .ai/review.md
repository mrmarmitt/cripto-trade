# Heuristicas de Review

## Ordem de prioridade

1. Correcao comportamental
2. Fronteira arquitetural
3. Risco de regressao
4. Ausencia de validacao ou teste
5. Clareza de contrato e nomes
6. Estilo, quando afetar manutencao
   
## O que procurar primeiro

- Regra de negocio fora do modulo dono
- Vazamento de payload ou schema de provider para dentro do `core`
- Controller, service ou adapter decidindo politica de dominio
- Estrategia deixando de ser calculo puro
- Acoplamento novo entre modulos sem justificativa forte

## Como registrar achados

- Comecar pelos findings, ordenados por severidade
- Explicar qual fronteira ou invariante foi violado
- Explicar o risco de longo prazo ou de regressao
- Dizer qual modulo deveria ser o dono da responsabilidade

## Quando nao houver problemas

Se nenhum problema relevante for encontrado:

- dizer explicitamente que nao ha findings
- registrar riscos residuais
- registrar lacunas de teste ou validacao, se existirem

