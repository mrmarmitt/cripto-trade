# Fase 1 — Fundação: Modelo de Dados, Migração e Comunicação

## Propósito

A Fase 1 estabelece as bases estruturais do sistema: o novo modelo de dados com dois agregados (Portfolio + StrategyRunner), o plano de migração do modelo legado, e o protocolo de comunicação entre eles. Sem essa fundação, nenhuma das fases seguintes pode ser implementada.

## Seções do Implementation Guide Cobertas

| Seção  | Título                                        | Linhas IG  |
|--------|-----------------------------------------------|------------|
| **3**  | Modelo de Dados                               | 25–802     |
| **4**  | Guia de Migração e Decomposição (Refatoração) | 803–831    |
| **5**  | Protocolo de Comunicação entre Agregados      | 833–1182   |

## Work Packages

### WP-1: Modelo de Dados (Seção 3)

Implementar campo-a-campo a transformação do modelo atual (agregado único Portfolio) no modelo alvo (dois agregados). Inclui:

- **Portfolio Aggregate Root** — campos novos (`safeModeStatus`, `capitalPoolingMode`), remoção de campos operacionais que migram para StrategyRunner
- **GlobalBalance** — substitui Balance atual; decompõe `invested` em `reservedBalance`, adiciona `totalFeesPaid`, `initialCapital`, `baseCurrency`
- **StrategyRunner Aggregate Root** — nova entidade com lifecycle próprio, configuração de estratégia, exchange e símbolo
- **RunnerBalance** — saldo local do Runner (shadow balance, alocação dedicada)
- **Position** — refatorada para suportar Hedging (1:N com Runner)
- **Transaction** — migra para StrategyRunner, ganha `ClientOrderId` VO
- **TransactionMatch** — ganha PK próprio (UUID), suporta matches parciais
- **Fee VO** — substitui o Asset genérico com campos específicos
- **Enums e Value Objects** novos conforme especificado no IG

### WP-2: Migração e Decomposição (Seção 4)

Executar a refatoração prática do código existente:

- Criar `StrategyRunnerRepository` separado
- Segregar tabelas de Positions e Transactions do domínio financeiro do Portfolio
- Implementar o Value Object `ClientOrderId` (geração e parsing do ID de 32/36 caracteres)
- Redistribuir responsabilidades conforme tabela de mapeamento do IG (Seção 4)
- Garantir que Capital Request seja o único ponto de sincronização entre agregados

### WP-3: Protocolo de Comunicação (Seção 5)

Implementar o padrão híbrido de comunicação entre Portfolio e StrategyRunner:

- **Interface `CapitalManager`** — ponto único de acoplamento entre agregados
- **Operações síncronas** — `reserve()` para reserva de capital (consistência forte)
- **Operações assíncronas** — `confirm()`, `release()`, `adjustReservation()` para liquidação e estorno
- **Modelo de consistência eventual** — window máximo, tratamento de saldo desatualizado
- **Mecanismos de proteção** — retry, timeout, idempotência no Portfolio
- **Domain Events** — definição e contrato dos eventos entre agregados

## Entregável Esperado

Ao final da Fase 1, o sistema deve ter:

1. Entidades, VOs e enums do novo modelo implementados no domain layer
2. Schema de banco atualizado (ou migrations criadas) refletindo os dois agregados
3. Repositórios separados para Portfolio e StrategyRunner
4. Interface `CapitalManager` implementada com operações sync e async
5. Domain Events definidos e publicáveis entre agregados
6. Código legado refatorado — sem "god aggregate"

## Critério de Done

- [ ] Todas as entidades da Seção 3 do IG estão implementadas como classes Java
- [ ] GlobalBalance substitui Balance com os novos campos
- [ ] StrategyRunner existe como aggregate root independente
- [ ] `ClientOrderId` VO implementado com geração e parsing
- [ ] `CapitalManager` interface definida e com implementação funcional
- [ ] Operações síncronas (`reserve`) e assíncronas (`confirm`, `release`) funcionam
- [ ] Schema DB reflete o modelo alvo (2 agregados)
- [ ] Aplicação compila e sobe sem erros (`./gradlew build`)

## Contexto: Visão Geral das 4 Fases

| Fase                      | Escopo                                                  | Seções IG      |
|---------------------------|---------------------------------------------------------|----------------|
| **1 — Fundação**          | Modelo de dados, migração, comunicação entre agregados  | 3, 4, 5        |
| **2 — Ciclo Operacional** | Transações, capital, fees, locks                        | 6, 7, 8, 9     |
| **3 — Resiliência**       | Reconciliação, circuit breaker, concorrência, lifecycle | 10, 11, 12, 13 |
| **4 — Integração**        | Strategy context, exchange, questões em aberto          | 14, 15, 16     |

---

> **Referência:** [Implementation Guide](IMPLEMENTATION_GUIDE.md) — documento completo com 16 seções (~6000 linhas).
