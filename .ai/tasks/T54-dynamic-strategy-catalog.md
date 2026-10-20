# T54 — Catálogo Dinâmico de Estratégias Remotas

**Complexidade:** Alta
**Responsável:** Claude
**Dependências:** T40 (contrato), T41 (estratégia remota, catálogo estático), T49 (API admin de runners).
Pré-requisito da T53 com várias estratégias remotas.
**Status:** Concluído (2026-10-20) — ver "Entrega" no fim.

---

## Descrição

Hoje uma estratégia remota só entra no ctrade por configuração (`ctrade.strategy.remote.catalog` no
`application.yml`), lida no boot: cadastrar, trocar endereço ou tirar uma estratégia exige editar a
configuração e **reiniciar o ctrade**. A visão do produto é o oposto: cada estratégia é uma app
independente, em qualquer linguagem e arquitetura (stateful ou stateless), que só implementa o
contrato (`docs/strategy-service/CONTRACT.md`); o ctrade deve aceitar estratégias novas **sem deploy
nem restart**.

Esta task troca o catálogo estático por um catálogo persistido e administrado por API. O ctrade
continua especificando **apenas o contrato e o protocolo**; como a estratégia é construída não é
assunto do ctrade.

## Contexto técnico

- `spring-application/.../config/strategy/RemoteStrategyConfiguration.java` — monta, **uma vez no
  boot**, o `RemoteStrategyClient` com uma lista fixa de `RemoteStrategyEndpoint` (`strategyRef`,
  `baseUrl`, token) e o `RemoteStrategyCatalog` com as entradas `enabled`.
- `RemoteStrategyProperties` — `catalog[]` (`strategy-ref`, `base-url`, `name`, `version`, `enabled`)
  e um **token único** (`CTRADE_STRATEGY_REMOTE_TOKEN`) para todas as estratégias.
- `RemoteStrategyRegistrationScheduler` — a cada `registration-retry-interval-ms` chama
  `RegisterRemoteStrategiesUseCase.registerPending(catalog)`.
- `core/.../usecase/strategy/RegisterRemoteStrategiesUseCase.java` — confere `/v1/meta` (mesmo
  `strategyId`, nome, versão e major do contrato) e registra um `RemoteTradingStrategy` no
  `StrategyRepositoryPort`. Entrada que diverge ou está fora do ar não é registrada (ERROR + retry).
- `StrategyRepositoryPort` (`InMemoryStrategyRepository`) — só `findById` e `registerStrategy`; não há
  remoção nem desabilitação. Guarda também as estratégias in-process (SMA, cenários da T35).
- `StrategySignalEvaluator` — estratégia não encontrada ou desabilitada deixa o runner em HOLD
  implícito; a proteção do lote (T44) continua valendo.
- `CreateRunnerUseCase` — só cria runner para `strategyId` já registrado.
- API admin protegida por `X-Admin-Key` (`AdminApiKeyInterceptor`, `/api/admin/**`).

## Solução proposta (a validar)

1. **Catálogo persistido.** Tabela `remote_strategies`: `strategy_ref` (PK), `name`, `version`,
   `base_url`, `token`, `status` (`ENABLED`/`DISABLED`), estado de verificação (`PENDING`/`VERIFIED`/`FAILED`
   + último erro e horário), `created_at`, `updated_at`.
2. **API admin** (`X-Admin-Key`):
   - `POST /api/admin/strategies` — cadastra (`strategyRef`, `name`, `version`, `baseUrl`, `token`);
   - `GET /api/admin/strategies` e `GET /api/admin/strategies/{ref}` — catálogo com estado de
     verificação e runners que usam cada estratégia;
   - `POST /api/admin/strategies/{ref}/disable` e `/enable`;
   - `PATCH /api/admin/strategies/{ref}` — troca de `baseUrl` e/ou `token` (mesma identidade).
3. **Verificação reaproveitada.** O cadastro grava `PENDING`; o mesmo `RegisterRemoteStrategiesUseCase`
   (agora lendo o catálogo do repositório) confere `/v1/meta` e marca `VERIFIED` ou `FAILED`. O
   scheduler existente continua tentando os pendentes — um só caminho de registro.
4. **Endpoints em runtime.** O `RemoteStrategyClient` passa a resolver o endpoint de cada `strategyRef`
   num registro mutável alimentado pelo catálogo, em vez da lista fixa do boot.
5. **Desabilitar e remover do repositório.** `StrategyRepositoryPort` ganha a operação de tirar uma
   estratégia de uso; runners que apontam para ela ficam em HOLD implícito (comportamento atual de
   "estratégia não encontrada").
6. **Um caminho só.** O catálogo do `application.yml` é removido; o banco passa a ser a única fonte
   (manual §1.3). As estratégias in-process (SMA, cenários) continuam como estão.

## Operação por IA (diretriz do mantenedor, 2026-10-20)

O principal consumidor desta API é um agente de IA, que vai operar o ctrade sem um humano
disponível. A API precisa permitir que o agente entenda o estado, aja e confira o efeito sozinho:

1. **Respostas autoexplicativas.** O `GET` traz, por estratégia: `status`, estado de verificação,
   último erro e horário, `lastVerifiedAt`, identidade servida pela app (nome, versão, contrato) e os
   runners que a usam (id e status). Toda mudança devolve o estado resultante, não só `200`.
2. **Erros com código estável.** Além da mensagem, cada recusa traz um código legível por máquina
   (`STRATEGY_NOT_FOUND`, `STRATEGY_ALREADY_EXISTS`, `STRATEGY_DEFINITION_CONFLICT`, `INVALID_BASE_URL`,
   …) no envelope `ApiError`.
3. **Idempotência.** Repetir um cadastro idêntico devolve o registro existente; cadastro com o mesmo
   `strategyRef` e dados diferentes é recusado com código. `enable`/`disable` repetidos não falham e
   não geram evento novo.
4. **`reason` obrigatório** em cadastro, `enable`, `disable` e `PATCH`, guardado num histórico de
   eventos do catálogo (`GET /api/admin/strategies/{ref}/events`), para o agente reconstruir o que
   aconteceu e por quê.
5. **Contrato da API publicado.** Os endpoints aparecem no OpenAPI gerado pelo springdoc, com
   descrição de campos, estados e códigos de erro.
6. **Runbook para agente.** A seção do runbook descreve os procedimentos (cadastrar, verificar,
   desabilitar, diagnosticar `FAILED`) como passos executáveis com a resposta esperada em cada um.

## Decisões registradas (2026-10-20)

1. **Desabilitar com runner ativo:** aceito; os runners que usam a estratégia ficam em HOLD implícito,
   com alerta, e a proteção do lote (T44) continua valendo. Nada é parado à força.
2. **Cadastro de estratégia fora do ar:** aceito como `PENDING`; a verificação por `/v1/meta` acontece
   depois, pelo scheduler de registro (mesmo comportamento do boot de hoje).
3. **Token por estratégia:** informado no cadastro e alterável por `PATCH`; revogar uma estratégia não
   afeta as outras. O token global `CTRADE_STRATEGY_REMOTE_TOKEN` sai junto com o catálogo do YAML.
   O token nunca volta nas respostas da API.
4. **Endereços:** sem restrição de host neste momento; basta uma URL válida.
5. **Remoção:** só desabilitar. Não há exclusão; o histórico e o vínculo com runners arquivados ficam
   preservados.
6. **Catálogo do `application.yml`:** removido. O banco é a única fonte.

## Fora do escopo

- Troca de versão de estratégia num runner em execução, shadow, canário e promoção automática.
- Monitoramento e proposição de estratégias por IA.
- Qualquer exigência sobre como a app da estratégia é implementada.

## Riscos

- Corrida entre desabilitar e um tick em avaliação: a decisão já pedida pode chegar depois; o
  comportamento esperado (ignorar ou aplicar) precisa ficar definido na implementação.
- Mudança de `baseUrl` com decisões em voo para o endereço antigo.
- **Token guardado no banco em texto:** aceitável em testnet; antes de produção, avaliar cifragem ou
  cofre de segredos junto com a gestão de segredos do go-live.

## Critérios de aceitação (preliminares)

1. Estratégia cadastrada por API fica utilizável por um runner novo sem restart do ctrade.
2. Estratégia que não responde `/v1/meta` ou diverge na identidade não é usada e mostra o motivo no
   `GET` do catálogo.
3. Desabilitar leva os runners que a usam ao comportamento decidido no item 1, sem efeito financeiro.
4. Reiniciar o ctrade preserva o catálogo e o estado de cada estratégia.
5. Nenhuma configuração de catálogo nem token global resta no `application.yml`.
6. O token de uma estratégia é enviado só nas chamadas a ela e não aparece em respostas nem logs.
7. Cada recusa da API traz código de erro estável; repetir uma operação já aplicada não falha nem
   duplica evento.
8. Toda mudança de estado registra `reason` e aparece no histórico de eventos da estratégia.

## Fontes

- `docs/strategy-service/CONTRACT.md`, `docs/strategy-service/openapi.yaml`
- `.ai/tasks/T41-remote-strategy-minimal-extraction.md`
- `docs/OPERATIONS_RUNBOOK.md` (Estratégia remota)

## Entrega

### Decisões de implementação (2026-10-20)

- **Core é dono do catálogo.** Agregado `RemoteStrategy` (`core/domain/strategy`), eventos
  `RemoteStrategyEvent` e `RemoteStrategyCatalogUseCase`, que implementa `ManageRemoteStrategiesPort`
  (operador) e `SyncRemoteStrategiesPort` (ciclo periódico). Endereço e token (`StrategyEndpoint`) são
  dados do catálogo; o core só guarda e repassa, o transporte continua no adapter. `toString` do
  endpoint mascara o token.
- **Um caminho de conferência.** Cadastro, reabilitação, troca de endereço e o ciclo de 30s passam pelo
  mesmo `sync`: garante a `RemoteTradingStrategy` no repositório e, se habilitada e ainda não conferida
  neste processo, confere `/v1/meta`. Conferida, entra em uso; senão, os runners ficam em HOLD sem
  chamar o serviço. No boot toda estratégia habilitada é conferida de novo.
- **Endereço por chamada.** `StrategyEvaluationPort.evaluate/describe` recebem o `StrategyEndpoint`; o
  `RemoteStrategyClient` não guarda lista de estratégias. Trocar endereço vale na chamada seguinte.
- **Removidos (manual §1.3):** `RegisterRemoteStrategiesUseCase`/`Port`, `RemoteStrategyDefinition`,
  `RemoteStrategyEndpoint`, o catálogo `ctrade.strategy.remote.catalog` e o token global
  `CTRADE_STRATEGY_REMOTE_TOKEN`.
- **Persistência:** `V17__create_remote_strategy_catalog.sql` (`remote_strategies`,
  `remote_strategy_events` com `seq` para ordenar eventos do mesmo instante).
- **Erros com código:** `StrategyCatalogException` com `Code` (`STRATEGY_NOT_FOUND`,
  `STRATEGY_DEFINITION_CONFLICT`, `STRATEGY_REF_IN_USE`, `INVALID_STRATEGY_DEFINITION`,
  `REASON_REQUIRED`). O `ApiError` ganhou `code` (omitido quando nulo); o 401 do `X-Admin-Key` passou a
  trazer `ADMIN_KEY_INVALID`.
- **Alerta:** desabilitar com runner `ACTIVE` gera `STRATEGY_DISABLED` no Discord.
- **API documentada** com anotações springdoc (`/v3/api-docs`, tag *Strategy catalog*).

### Validação

- `RemoteStrategyCatalogUseCaseTest`: conferência no cadastro, serviço fora → `FAILED` e recuperação no
  ciclo, identidade/contrato divergentes nunca em uso, idempotência e conflito, `strategyRef` de
  estratégia interna, `reason` e códigos, desabilitar com alerta, reabilitar com nova conferência, troca
  de endereço, token mascarado, boot só conferindo as habilitadas.
- `RemoteStrategyClientTest` migrado para endereço por chamada.
- `StrategyCatalogControllerTest`: estado sem token e códigos/status das recusas.
- `RemoteStrategyE2ETest` (Postgres + MockWebServer): cadastro por API, decisão vira ordem, falhas viram
  HOLD, desabilitar deixa o runner em HOLD sem chamar o serviço, reabilitar volta a operar, estado e
  histórico persistidos.

### Riscos residuais

- Token em texto no banco (rever com a gestão de segredos antes de produção).
- Entradas do catálogo sobrevivem entre classes de teste que compartilham o Postgres; o ciclo de outro
  contexto pode conferir uma estratégia de teste já encerrada e registrar `VERIFICATION_FAILED` (só
  ruído de log em teste).
