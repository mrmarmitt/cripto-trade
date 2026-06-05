# Git Workflow

Este documento define a politica canonica de branch, commit e PR para agentes.

## Branch base

- Toda branch de trabalho deve sair de `develop`.
- Todo pull request deve apontar para `develop`.
- Se `develop` nao existir localmente ou remotamente, pare e reporte o bloqueio.
- Nao crie branch de trabalho a partir de `main`, `master` ou branch de feature
  sem pedido explicito.

## Nomenclatura de branches

Use convencao Git Flow com prefixo e slug:

- `feature/<slug>` para implementacao nova ou evolucao funcional.
- `bugfix/<slug>` para correcao de bug em fluxo normal.
- `hotfix/<slug>` para correcao urgente direcionada a producao, somente com
  pedido explicito.
- `docs/<slug>` para mudancas apenas de documentacao.
- `refactor/<slug>` para refactor sem mudanca comportamental pretendida.
- `test/<slug>` para mudancas focadas em teste/validacao.
- `chore/<slug>` para manutencao operacional, build ou tooling.

O slug deve:

- usar lowercase;
- remover acentos;
- usar apenas letras, numeros e `-`;
- separar palavras com `-`;
- ser curto e descritivo;
- evitar nomes genericos como `fix`, `changes`, `update` ou `codex`.

Exemplos:

- `feature/binance-symbol-filters`
- `bugfix/order-conciliation-idempotency`
- `docs/ai-agent-usage`
- `refactor/runner-signal-policy`
- `test/boot-recovery-scenarios`
- `chore/gradle-cache-wrapper`

## Criacao de branch por agentes

- Se o usuario fornecer nome de branch, use-o apenas se seguir esta convencao.
- Se o nome fornecido nao seguir a convencao, sugira o nome correto antes de
  criar a branch.
- Se o usuario nao fornecer nome, derive o prefixo pelo tipo da tarefa e crie um
  slug a partir do objetivo.
- Antes de criar branch, confira `git status --short` e proteja mudancas locais
  nao relacionadas.
- Antes de iniciar desenvolvimento em nova branch, atualize `develop` e crie a
  branch de trabalho a partir dele.

## Rebase com develop

- Antes de iniciar desenvolvimento, atualize `develop`.
- Antes de abrir PR, faca rebase da branch de trabalho sobre `develop`.
- Se houver conflito no rebase, pare e reporte o conflito antes de continuar.
- Nao abra PR enquanto a branch estiver divergente de `develop`.
- Nao use merge de `develop` na branch de trabalho para substituir o rebase,
  salvo pedido explicito.

## Commits

- Commit deve conter apenas mudancas relacionadas ao objetivo.
- Evite `git add .`; stage arquivos explicitamente.
- Use mensagem curta e orientada ao comportamento.
- Conventional commit e recomendado quando encaixar:
  - `feat: ...`
  - `fix: ...`
  - `docs: ...`
  - `refactor: ...`
  - `test: ...`
  - `chore: ...`

## Pull requests

- PR deve ser draft por padrao.
- PR deve apontar para `develop`.
- Antes de abrir PR, a branch deve estar rebased sobre `develop`.
- PR deve reportar validacao executada.
- Nunca fazer merge automaticamente.
