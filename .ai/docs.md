# Documentacao

## Quando atualizar

Atualize documentacao quando houver:

- novo modulo, pacote ou fluxo relevante
- novo flow map ou mudanca relevante em fluxo implementado
- mudanca de fronteira arquitetural
- novo contrato importante
- mudanca no processo de validacao
- alteracao de comportamento operacional relevante

Toda alteracao de codigo deve avaliar impacto na documentacao base. Atualize
`/.ai/flows/*`, `/.ai/project-context.md`, `/.ai/architecture.md`,
`/.ai/coding-standards.md`, `/.ai/validation.md` ou `docs/*` quando a mudanca
alterar fluxo, contrato, fronteira, invariante, comportamento operacional ou
validacao. Se nao houver impacto documental, declare isso no fechamento da
tarefa ou no PR.

## Papel de cada documento

- `README.md`: visao geral do projeto, arquitetura resumida e instrucoes principais
- `AGENTS.md` e `CLAUDE.md`: ponte obrigatoria para as regras em `/.ai`
- `/.ai/*`: regras detalhadas e canonicas para agentes e colaboradores
- `/.ai/flows/*`: mapas curtos de fluxos implementados, conectando comportamento,
  codigo principal, invariantes e testes
- `/.ai/tasks/*`: especificacoes tecnicas das tasks; consultadas antes de qualquer implementacao
- `docs/tasks/BACKLOG.md`: visao de roadmap e status de entrega para humanos

## Regra pratica

- Evitar duplicar regra detalhada em varios lugares
- Preferir apontar para `/.ai` como fonte canonica
- Se um documento resumido divergir de `/.ai`, corrigir o documento resumido

