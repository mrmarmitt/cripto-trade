# Documentacao

## Quando atualizar

Atualize documentacao quando houver:

- novo modulo, pacote ou fluxo relevante
- mudanca de fronteira arquitetural
- novo contrato importante
- mudanca no processo de validacao
- alteracao de comportamento operacional relevante

## Papel de cada documento

- `README.md`: visao geral do projeto, arquitetura resumida e instrucoes principais
- `AGENTS.md` e `CLAUDE.md`: ponte obrigatoria para as regras em `/.ai`
- `/.ai/*`: regras detalhadas e canonicas para agentes e colaboradores

## Regra pratica

- Evitar duplicar regra detalhada em varios lugares
- Preferir apontar para `/.ai` como fonte canonica
- Se um documento resumido divergir de `/.ai`, corrigir o documento resumido

