# Guia de Desenvolvimento para Agentes

Este diretorio e a fonte canonica das praticas de desenvolvimento deste repositorio.

Todo agente deve consultar este material antes de implementar, revisar, refatorar ou propor mudancas arquiteturais.

## Protocolo de inicio

1. Leia este arquivo antes de responder a tarefas de implementacao, review, refactor, arquitetura ou documentacao.
2. Consulte os documentos indicados em "Contexto minimo por tarefa".
3. Quando a tarefa envolver implementacao, review, refactor, arquitetura ou documentacao, declare brevemente quais documentos de `/.ai` foram consultados.
4. Se uma instrucao em `AGENTS.md`, `CLAUDE.md` ou outro wrapper divergir de `/.ai`, siga `/.ai` e trate o wrapper como desatualizado.

## Leitura base para tarefas de codigo

1. `project-context.md`
2. `architecture.md`
3. `coding-standards.md`
4. `validation.md`
5. `git-workflow.md`

## Contexto minimo por tarefa

- Implementacao: `project-context.md`, `architecture.md`, `coding-standards.md`, `validation.md` e flow map aplicavel em `flows/`
- Review de PR ou diff: `project-context.md`, `architecture.md`, `coding-standards.md`, `validation.md`, `review.md`, `agents/code-review-agent.md` e flow map aplicavel em `flows/`
- Resolver comentarios de PR: `project-context.md`, `architecture.md`, `coding-standards.md`, `validation.md`, `review.md`, `agents/code-review-agent.md`, `skills/pr-comment-resolver/SKILL.md`
- Criacao de PR: `project-context.md`, `architecture.md`, `coding-standards.md`, `validation.md`, `git-workflow.md`, `skills/pr-creator/SKILL.md`
- Refactor ou mudanca de responsabilidade entre modulos: `project-context.md`, `architecture.md`, `coding-standards.md`, `validation.md`, `change-safety.md` e flow map aplicavel em `flows/`
- Sugestao arquitetural: `project-context.md`, `architecture.md`, `coding-standards.md`, `change-safety.md` e flow map aplicavel em `flows/` quando envolver fluxo existente
- Atualizacao de documentacao: `docs.md`
- Publicacao de findings em PR: `skills/pr-review-publisher/SKILL.md`

## Guias adicionais por tipo de tarefa

- Uso de agentes e skills: consultar `agents/usage.md` para exemplos de comandos e efeitos esperados
- Flow maps: consultar `flows/README.md` para entender fluxos implementados antes de alterar feature ou bug
- Impacto documental: toda alteracao de codigo deve avaliar se `/.ai`, `/.ai/flows` ou `docs/` precisam ser atualizados; quando nao houver impacto, declarar isso no fechamento da tarefa ou PR
- Git workflow: consultar `git-workflow.md` para branch, commit e base de PR
- Review de PR ou diff: ler tambem `review.md`
- Resolver comentarios de PR: ler tambem `skills/pr-comment-resolver/SKILL.md`
- Criacao de PR: ler tambem `skills/pr-creator/SKILL.md`
- Refactor ou mudanca de responsabilidade entre modulos: ler tambem `change-safety.md`
- Atualizacao de documentacao: ler tambem `docs.md`

## Agentes compartilhados

- Os prompts especializados compartilhados entre ferramentas devem ficar em `/.ai/agents/`
- Review de codigo deve usar `/.ai/agents/code-review-agent.md` como complemento operacional de `review.md`
- `AGENTS.md` e `CLAUDE.md` devem apenas apontar para esses arquivos, sem duplicar regra detalhada

## Skills compartilhadas

- Skills especificas do projeto devem ficar em `/.ai/skills/`
- Workflows operacionais reutilizaveis entre Codex e Claude devem usar `/.ai/skills/` como fonte canonica
- Wrappers de ferramenta podem apontar para a skill no projeto, mas nao devem redefinir a politica dela

## Precedencia

- Preservar fronteiras arquiteturais tem prioridade sobre conveniencia local de implementacao.
- Quando houver duvida sobre o dono de uma responsabilidade, preferir manter a decisao no modulo mais interno possivel.
- DTOs, payloads e detalhes de provider nao devem vazar para o dominio.
- `AGENTS.md`, `CLAUDE.md` e outros wrappers devem apenas apontar para `/.ai`; regras detalhadas pertencem aqui.

## Objetivo

Este material existe para alinhar agentes e colaboradores humanos em torno de:

- fronteiras de modulo
- praticas de implementacao
- criterios de review
- validacao minima obrigatoria
- atualizacao consistente de documentacao

