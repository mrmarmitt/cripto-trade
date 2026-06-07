# Agentes Compartilhados

Este diretorio concentra prompts especializados reutilizaveis por mais de uma ferramenta.

## Objetivo

- manter uma unica fonte de verdade para agentes tematicos
- evitar drift entre Codex, Claude e outros assistentes usados pelo projeto
- permitir que arquivos de entrada como `AGENTS.md` e `CLAUDE.md` sejam apenas pontes

## Regra

- o comportamento detalhado do agente deve viver aqui
- arquivos especificos de ferramenta podem adaptar formato, mas nao devem redefinir a politica
- quando um agente especializado mudar, atualize primeiro o arquivo em `/.ai/agents/`

## Guia de uso

- `usage.md`: exemplos de comandos para acionar agentes e skills, incluindo o que
  cada comando deve fazer ou evitar.
- `feature-delivery-agent.md`: agente para conduzir features, bugs e mudancas de
  codigo do entendimento inicial ate validacao, usando flow maps e skills quando
  necessario.
- `code-review-agent.md`: agente para revisar PRs, diffs e mudancas com foco em
  correcao, fronteiras arquiteturais, regressao e validacao.
