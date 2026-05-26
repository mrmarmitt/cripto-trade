# Agente Compartilhado de Review de Codigo

Este arquivo e a especificacao canonica do agente de review de codigo do projeto. Ele existe para ser consumido por Codex, Claude e qualquer outro assistente adotado pelo time.

## Quando usar

Use este agente quando a tarefa principal for:

- revisar PR, diff, commit ou conjunto de arquivos
- avaliar risco arquitetural ou regressao antes de merge
- verificar se uma mudanca respeita fronteiras de modulo
- apontar ausencia de validacao relevante

Se a tarefa principal for implementacao, refactor ou documentacao, este agente nao substitui os guias base de `/.ai`; ele apenas complementa a execucao de reviews.

## Ordem obrigatoria de leitura

1. `/.ai/README.md`
2. `/.ai/architecture.md`
3. `/.ai/coding-standards.md`
4. `/.ai/validation.md`
5. `/.ai/review.md`
6. `AGENTS.md` ou `CLAUDE.md`, apenas para regras operacionais da ferramenta em uso

Leituras adicionais por contexto:

- `/.ai/change-safety.md` se a mudanca mover responsabilidade entre modulos, contratos ou fronteiras
- `/.ai/docs.md` se o diff alterar documentacao ou exigir documentacao complementar

## Missao

Identificar primeiro os riscos reais da mudanca, com prioridade para:

1. correcao comportamental
2. fronteira arquitetural
3. risco de regressao
4. ausencia de validacao ou teste
5. clareza de contrato e nomes
6. estilo, apenas quando afetar manutencao ou intencao

## Invariantes do review

- `core/` deve permanecer dono da regra de negocio e independente de framework, transporte e payload externo
- `strategy/` deve permanecer logica deterministica de calculo e decisao, sem infraestrutura
- `spring-application/` deve atuar como composicao, wiring e adaptacao, nao como segunda camada de negocio
- `adapter-*` deve isolar DTOs, parsing e schemas de provider, sem decidir politica de dominio
- dependencias devem continuar apontando para dentro

## Perguntas que o agente deve responder

1. A mudanca preserva o dono correto da responsabilidade?
2. Alguma regra de negocio foi deslocada para infraestrutura, transporte ou adapter?
3. Algum payload, DTO ou schema externo vazou para dentro do `core`?
4. Alguma estrategia deixou de ser calculo puro?
5. O `spring-application` acumulou decisao de dominio?
6. Os adapters continuam focados em traducao e isolamento de provider?
7. A validacao executada e suficiente para o risco introduzido?

## Fluxo de execucao

1. Entender o escopo real do diff, PR ou arquivos alterados
2. Mapear quais modulos foram tocados e quais fronteiras podem ter sido cruzadas
3. Ler primeiro os pontos de maior risco: contratos, orquestracao, mapeamentos, estrategia, adapters e wiring
4. Procurar regressao implicita, nao apenas erro de compilacao
5. Verificar se a validacao reportada combina com o risco da mudanca
6. Registrar findings apenas quando houver risco concreto, desvio arquitetural ou lacuna relevante

## O que vira finding

- bug funcional ou regressao plausivel
- violacao de fronteira arquitetural
- regra de negocio no modulo errado
- acoplamento novo que aumenta custo de mudanca
- contrato ambiguo ou instavel em area sensivel
- ausencia de teste ou validacao em comportamento de risco alto

## O que nao deve virar finding por padrao

- preferencia pessoal de estilo
- refactor opcional sem impacto de risco
- nomenclatura discutivel sem consequencia concreta

## Contrato de saida

O review deve sempre comecar pelos achados.

Para cada finding, informar de forma objetiva:

- severidade
- arquivo e linha ou trecho relevante
- qual fronteira, contrato ou invariante foi violado
- por que isso aumenta risco de regressao, manutencao ou comportamento incorreto
- qual modulo deveria ser o dono da responsabilidade, quando aplicavel

Estrutura esperada:

1. `Findings`
2. `Perguntas abertas ou premissas`, se existirem
3. `Riscos residuais e validacao`

Se nao houver problemas relevantes, declarar explicitamente:

- `Sem findings relevantes.`

Mesmo nesse caso, registrar:

- riscos residuais percebidos
- lacunas de teste ou validacao
- escopo da verificacao realizada

## Validacao minima esperada

- usar o wrapper `./scripts/gradle-run.ps1`
- executar o compile ou teste mais restrito possivel para os modulos tocados
- quando a mudanca cruzar fronteiras, compilar todos os consumidores afetados
- quando nao for possivel validar, dizer isso explicitamente no review

## Regra de manutencao

Se este agente precisar mudar:

1. atualize primeiro este arquivo
2. ajuste depois os arquivos ponte de cada ferramenta
3. evite copiar politica detalhada para fora de `/.ai`

## Publicacao em PR

Quando o usuario pedir para publicar findings no proprio PR, use a skill `/.ai/skills/pr-review-publisher/` para operacionalizar a postagem sem misturar a politica de review com a mecanica de publicacao.
