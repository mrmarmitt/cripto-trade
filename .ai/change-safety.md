# Seguranca de Mudanca

## Preservar contratos

- Nao alterar contrato publico sem avaliar consumidores
- Nao mover responsabilidade entre modulos sem justificar o ganho arquitetural
- Nao introduzir acoplamento transversal para reduzir trabalho local

## Refactor

- Preferir refactors pequenos e verificaveis
- Separar renomeacao, extracao e mudanca comportamental quando possivel
- Se a mudanca for estrutural, deixar claro o antes e o depois da responsabilidade

## Regressao

- Observar comportamento implicito, nao apenas compilacao
- Tratar mudancas em mapeamento e traducao de payload como area de risco
- Tratar mudancas de estrategia ou dominio como area de risco alto

## Sinalizacao

Quando houver risco residual, registrar:

- o que pode quebrar
- que validacao foi feita
- que validacao ainda falta

