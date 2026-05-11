# AGENTS.md

## Diretriz Obrigatoria para Agentes

Antes de qualquer implementacao, review, refactor ou sugestao arquitetural, consulte `/.ai/README.md`.

O diretorio `/.ai` e a fonte canonica para:

- fronteiras arquiteturais
- padroes de implementacao
- heuristicas de review
- validacao minima
- atualizacao de documentacao

Leituras adicionais obrigatorias por contexto:

- review: `/.ai/review.md`
- mudanca de responsabilidade entre modulos: `/.ai/change-safety.md`
- atualizacao de documentacao: `/.ai/docs.md`

## Objetivo

Este repositorio utiliza o Codex principalmente como agente de implementacao e de review. Em tarefas de review, priorize correcao, limites arquiteturais, regressao e falta de validacao acima de comentarios apenas de estilo.

Ao revisar um pull request:

- Comece pelos achados, ordenados por severidade.
- Foque em risco comportamental e desvio arquitetural antes de nomenclatura ou formatacao.
- Aponte violacoes de fronteira mesmo quando o codigo estiver tecnicamente funcional.
- Se nenhum problema for encontrado, diga isso explicitamente e registre riscos residuais ou lacunas de teste.

## Principios de Arquitetura

Espera-se que este codebase siga clean code, clean architecture e arquitetura hexagonal.

Expectativas centrais:

- As dependencias devem apontar para dentro, em direcao as regras de negocio.
- Decisoes de negocio devem permanecer isoladas de frameworks, transporte, persistencia e detalhes especificos de provedores.
- Cada modulo deve manter uma responsabilidade unica e clara; rejeite mudancas que embaralhem a responsabilidade de cada modulo.

Use essas regras arquiteturais como invariantes de review.

## Responsabilidades dos Modulos

### `core/`

O modulo `core` e o dono das regras de negocio e deve permanecer independente de detalhes de implementacao.

Revise considerando que:

- Entidades de dominio, value objects, ports e use cases pertencem aqui.
- Regras de negocio e orquestracao do comportamento de dominio pertencem aqui.
- Detalhes de framework nao pertencem aqui.

Considere problema quando houver:

- Anotacoes do Spring ou abstracoes especificas do Spring.
- Preocupacoes de HTTP, WebSocket, mensageria, banco de dados ou SDK de provedor.
- DTOs especificos de exchange ou estruturas de payload vazando para dentro do modulo.
- Decisoes tecnologicas embutidas na logica de dominio.
- Codigo que decide como uma ferramenta externa funciona, em vez de expressar o que o negocio precisa.

### `spring-application/`

O modulo `spring-application` e a camada de entrega e de composicao da aplicacao. O papel dele e disponibilizar as ferramentas necessarias para o `core` funcionar, como PostgreSQL, OkHttp, mensageria, configuracao, agendamento e exposicao REST.

Revise considerando que:

- Wiring de dependencias, configuracao de beans, adapters, controllers e setup de execucao pertencem aqui.
- Mapeamento entre entradas externas e use cases do core pertence aqui.
- Implementacoes de infraestrutura para ports do core pertencem aqui.
- DTOs nao devem ser criados nem mantidos nesta camada.

Considere problema quando houver:

- Regras de negocio sendo decididas aqui em vez de no `core`.
- Calculo de estrategia sendo implementado aqui em vez de no `strategy`.
- Controllers ou services acumulando decisoes de dominio em vez de delegarem para os use cases.
- Detalhes de payload de provedores subindo de camada quando deveriam permanecer isolados nos modulos adapter.
- DTOs declarados no `spring-application`, mesmo quando usados apenas para request/response.

### `strategy/`

O modulo `strategy` deve focar apenas em decidir ou calcular uma acao a partir de uma entrada.

Revise considerando que:

- Logica deterministica de estrategia, regras de decisao e fluxo de calculo pertencem aqui.
- Entradas e saidas devem ser explicitas e faceis de avaliar.

Considere problema quando houver:

- Preocupacoes de rede, persistencia, mensageria ou framework.
- Controllers, repositories, clientes HTTP ou wiring de infraestrutura.
- Efeitos colaterais ocultos que nao estejam relacionados ao calculo do resultado da estrategia.
- Orquestracao de negocio que deveria estar no `core`.

### `adapter-*`

Os modulos adapter sao as bordas voltadas ao provedor. Eles devem focar em payloads, mapeamento e tratamento de schemas especificos do provedor, e nao em regras de negocio.

Revise considerando que:

- DTOs de request/response, parsing, traducao e representacoes especificas do provedor pertencem aqui.
- Os detalhes do provedor devem permanecer isolados do `core`.

Considere problema quando houver:

- Regras de negocio ou decisoes de estrategia dentro de modulos adapter.
- Vazamento de payloads de exchange/provedor para dentro do `core`.
- Orquestracao da aplicacao ou decisoes de dominio implementadas aqui.
- Acoplamento forte entre detalhes internos do adapter e modulos nao relacionados.

## Heuristicas de Review

Priorize estas perguntas durante o review:

1. A mudanca preserva a fronteira pretendida do modulo?
2. Alguma regra de negocio esta vazando para codigo de infraestrutura ou transporte?
3. Algum detalhe especifico de provedor esta vazando para o `core`?
4. O `strategy` continua sendo logica pura de decisao/calculo?
5. O `spring-application` esta atuando como wiring em vez de virar uma segunda camada de negocio?
6. Os adapters continuam focados em traducao de payload, e nao em politica de dominio?
7. Nomes, DTOs e interfaces estao expressando o dominio com clareza suficiente para manter mudancas futuras seguras?

## Validacao

Use o wrapper do repositorio para comandos Gradle, para manter consistente o cache local compartilhado:

```powershell
./scripts/gradle-run.ps1 -q :core:compileJava
./scripts/gradle-run.ps1 -q :strategy:compileJava
./scripts/gradle-run.ps1 -q :spring-application:compileJava
```

Orientacao de validacao:

- Execute o compile ou teste mais restrito possivel para os modulos tocados pela mudanca.
- Se a mudanca atravessar fronteiras entre modulos, prefira compilar todos os modulos afetados.
- Se o review encontrar ausencia de testes para um comportamento arriscado, explicite isso.

## Orientacao para Review de Pull Request

Quando o Codex for solicitado a revisar um PR, prefira comentarios que expliquem:

- qual fronteira ou invariante foi violado
- por que isso aumenta o risco de longo prazo ou o potencial de regressao
- qual modulo deveria ser o dono daquela responsabilidade

Evite comentarios de baixo valor que apenas repitam preferencia de estilo, a menos que isso afete legibilidade, manutenibilidade ou intencao arquitetural.
