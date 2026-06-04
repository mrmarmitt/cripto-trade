# Guia de Desenvolvimento para Agentes

Este diretorio e a fonte canonica das praticas de desenvolvimento deste repositorio.

Todo agente deve consultar este material antes de implementar, revisar, refatorar ou propor mudancas arquiteturais.

## Protocolo de inicio

1. Leia este arquivo antes de responder a tarefas de implementacao, review, refactor, arquitetura ou documentacao.
2. Consulte os documentos indicados em "Contexto minimo por tarefa".
3. Quando a tarefa envolver implementacao, review, refactor, arquitetura ou documentacao, declare brevemente quais documentos de `/.ai` foram consultados.
4. Se uma instrucao em `AGENTS.md`, `CLAUDE.md` ou outro wrapper divergir de `/.ai`, siga `/.ai` e trate o wrapper como desatualizado.

## Leitura base para tarefas de codigo

1. `architecture.md`
2. `coding-standards.md`
3. `validation.md`

## Contexto minimo por tarefa

- Implementacao: `architecture.md`, `coding-standards.md`, `validation.md`
- Review de PR ou diff: `architecture.md`, `coding-standards.md`, `validation.md`, `review.md`, `agents/code-review-agent.md`
- Refactor ou mudanca de responsabilidade entre modulos: `architecture.md`, `coding-standards.md`, `validation.md`, `change-safety.md`
- Sugestao arquitetural: `architecture.md`, `coding-standards.md`, `change-safety.md`
- Atualizacao de documentacao: `docs.md`
- Publicacao de findings em PR: `skills/pr-review-publisher/SKILL.md`

## Guias adicionais por tipo de tarefa

- Review de PR ou diff: ler tambem `review.md`
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

