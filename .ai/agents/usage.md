# Uso de Agentes e Skills

Este guia mostra como pedir trabalho para uma IA neste repositorio e o que cada
tipo de comando deve acionar. Ele nao substitui `/.ai/README.md`; use este arquivo
como catalogo pratico de comandos.

## Regra geral

- Peca o resultado desejado de forma explicita.
- Diga quando a IA deve publicar, responder, resolver threads, fazer commit ou
  abrir PR. Sem pedido explicito, essas acoes devem parar em rascunho/resumo.
- Para qualquer tarefa de codigo, a IA deve comecar lendo `/.ai/README.md` e o
  contexto minimo indicado ali.
- Agentes definem comportamento; skills operacionalizam workflows especificos.

## Comandos de review

### Revisar sem publicar

Comando:

```text
Use o code-review-agent para revisar o PR #32.
```

Deve fazer:

- ler `/.ai/agents/code-review-agent.md` e os documentos exigidos por ele;
- buscar diff/contexto do PR;
- reportar findings na conversa, ordenados por severidade;
- nao publicar comentarios no GitHub.

### Revisar e publicar findings

Comando:

```text
Use o code-review-agent para revisar o PR #32 e publique os findings no PR.
```

Deve fazer:

- executar o fluxo de review normal;
- usar `/.ai/skills/pr-review-publisher/SKILL.md` para publicar;
- evitar duplicar comentarios existentes;
- reportar o que foi publicado e o que foi ignorado.

### Revisar diff local

Comando:

```text
Use o code-review-agent para revisar meu diff local antes do commit.
```

Deve fazer:

- inspecionar o diff local;
- aplicar as regras de review do projeto;
- apontar riscos e lacunas de validacao;
- nao alterar arquivos, salvo se o usuario pedir explicitamente correcoes.

## Comandos para comentarios de PR

### Resolver comentarios sem publicar respostas

Comando:

```text
Resolva os comentarios do PR #32, mas deixe as respostas em rascunho.
```

Deve fazer:

- usar `/.ai/skills/pr-comment-resolver/SKILL.md`;
- ler threads com contexto completo;
- classificar comentarios acionaveis, ambiguos, duplicados ou resolvidos;
- implementar os fixes selecionados;
- validar as mudancas;
- entregar rascunhos de resposta para cada thread enderecada.

### Resolver comentarios e responder no PR

Comando:

```text
Resolva os comentarios do PR #32, suba o codigo e responda os threads no GitHub.
```

Deve fazer:

- executar o workflow de `pr-comment-resolver`;
- implementar e validar os fixes;
- publicar respostas somente nos threads enderecados;
- resolver threads apenas se o usuario tambem pedir isso explicitamente ou se o
  comando deixar claro que deve "resolver os threads";
- reportar links/ids das respostas quando disponiveis.

### Apenas responder um comentario

Comando:

```text
Responda ao comentario do PR #32 explicando por que nao vamos mover essa regra para o adapter.
```

Deve fazer:

- localizar o comentario/thread;
- redigir uma resposta alinhada com `/.ai/architecture.md`;
- publicar somente se o comando pedir "responda" no PR; caso contrario, deixar
  o texto em rascunho.

## Comandos de implementacao

### Implementar uma mudanca

Comando:

```text
Implemente suporte a validacao de symbol filters no adapter Binance.
```

Deve fazer:

- ler `project-context.md`, `architecture.md`, `coding-standards.md` e
  `validation.md`;
- consultar docs profundas do fluxo afetado quando houver;
- implementar no modulo dono da responsabilidade;
- validar com o comando mais restrito possivel;
- resumir arquivos alterados e validacao.

### Implementar e abrir PR

Comando:

```text
Implemente a mudanca, faca commit, suba a branch e abra um PR draft.
```

Deve fazer:

- usar `/.ai/skills/pr-creator/SKILL.md` para a etapa de publicacao;
- implementar e validar;
- conferir `git status` e escopo do commit;
- criar ou usar branch conforme `/.ai/git-workflow.md`, por exemplo
  `feature/<slug>`;
- criar nova branch a partir de `develop` atualizado;
- commitar apenas as mudancas relacionadas;
- fazer rebase da branch sobre `develop` antes de abrir PR;
- subir branch e abrir PR draft;
- apontar o PR para `develop`;
- nao fazer merge.

### Criar PR com mudancas atuais

Comando:

```text
Crie um PR draft com as mudancas atuais.
```

Deve fazer:

- usar `/.ai/skills/pr-creator/SKILL.md`;
- conferir `git status`;
- identificar mudancas relacionadas e nao relacionadas;
- criar ou usar branch conforme `/.ai/git-workflow.md`;
- validar com `./scripts/gradle-run.ps1` quando a mudanca nao for apenas docs;
- criar commit apenas com arquivos relacionados;
- fazer rebase da branch sobre `develop` antes de abrir PR;
- subir a branch;
- abrir PR draft apontando para `develop`;
- reportar URL do PR, validacao rodada e arquivos incluidos/excluidos.

## Comandos de refactor

### Refatorar com mudanca de responsabilidade

Comando:

```text
Refatore esse fluxo para mover a decisao de negocio do spring-application para o core.
```

Deve fazer:

- ler `/.ai/change-safety.md`;
- identificar o dono correto da responsabilidade;
- preservar contratos publicos ou listar consumidores afetados;
- separar mudanca estrutural de mudanca comportamental quando possivel;
- validar todos os modulos consumidores afetados.

## Comandos de documentacao

### Atualizar docs canonicas

Comando:

```text
Atualize a documentacao de agentes para incluir esse novo workflow.
```

Deve fazer:

- ler `/.ai/docs.md`;
- atualizar primeiro a fonte canonica em `/.ai`;
- evitar duplicar regra detalhada em `AGENTS.md`, `CLAUDE.md` ou README;
- ajustar documentos ponte apenas quando necessario.

## Frases que mudam o nivel de permissao

- "publique no PR": permite publicar comentarios/reviews no GitHub.
- "responda os threads": permite responder threads do PR.
- "resolva os threads": permite marcar threads como resolvidas quando a ferramenta
  suportar isso.
- "suba o codigo": permite push.
- "faca commit": permite criar commit com o escopo relacionado.
- "abra PR": permite criar pull request.
- "PR draft": cria pull request em modo draft, que tambem e o padrao.
- "sem publicar" ou "deixe em rascunho": impede writes externos e deixa textos
  prontos na conversa.
