# Skills Compartilhadas

Este diretorio concentra skills especificas deste projeto.

## Objetivo

- manter workflows reutilizaveis versionados junto com o repositorio
- permitir wrappers leves para Codex e Claude sem duplicar politica
- separar politica do projeto de configuracoes globais por maquina

## Regra

- a implementacao canonica da skill deve viver aqui
- wrappers de `C:\Users\mrmar\.codex\skills\` e `.claude/agents/` devem apenas apontar para estas skills
- quando uma skill mudar, atualize primeiro o arquivo em `/.ai/skills/`
