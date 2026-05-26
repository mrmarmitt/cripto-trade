# Guia de Desenvolvimento para Agentes

Este diretorio e a fonte canonica das praticas de desenvolvimento deste repositorio.

Todo agente deve consultar este material antes de implementar, revisar, refatorar ou propor mudancas arquiteturais.

## Ordem de leitura obrigatoria

1. `architecture.md`
2. `coding-standards.md`
3. `validation.md`

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

## Objetivo

Este material existe para alinhar agentes e colaboradores humanos em torno de:

- fronteiras de modulo
- praticas de implementacao
- criterios de review
- validacao minima obrigatoria
- atualizacao consistente de documentacao

