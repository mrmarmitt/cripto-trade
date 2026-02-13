# Design de Alavancagem (Leverage) e Margem — V2 (Futuro)

> **Status:** Reservado para implementação futura (V2).
> **Origem:** Extraído do Blueprint v16 (antiga Seção 12.2, atual 11.2) e documentos associados.
> **Decisão:** A V1 do CTrade opera exclusivamente em modo **Spot (custódia total, sem alavancagem)**. Este documento preserva o design de alavancagem para referência futura.

---

## 1. Modelagem de Alavancagem (Leverage) e Margem

O sistema trata a alavancagem como uma ferramenta de **eficiência de capital**, onde a margem é o colateral real bloqueado no Portfolio para sustentar uma exposição maior no Runner.

### A. Separação de Conceitos

1. **Margem (Conceito do Portfolio):** É o valor em "dinheiro vivo" (USDT, BTC, etc.) que a Exchange exige como garantia. O `Portfolio` é o único que gerencia a margem através da `MarginAccount`.
2. **Alavancagem/Leverage (Conceito do Runner):** É um multiplicador de risco. O `StrategyRunner` define a alavancagem desejada (ex: 10x), mas quem autoriza se há colateral suficiente para essa alavancagem é o `Portfolio`.

### B. Cálculo de Margem Necessária

O cálculo da margem no `Capital Request` segue a fórmula:

* **`Margem_Requerida = (Quantidade * Preço_Estimado) / Alavancagem`**
* O `StrategyRunner` envia tanto o **Valor Nocional** (total da ordem) quanto a **Alavancagem** no pedido de reserva.
* O `Portfolio` valida se o `AvailableBalance` suporta essa `Margem_Requerida` antes de mover o saldo para `Reserved`.

### C. Entidade `MarginAccount` (Versão Avançada)

A `MarginAccount` é uma entidade interna ao Agregado **Portfolio**:

* **Relacionamento:** 1 `Portfolio` : N `MarginAccounts` (uma para cada Exchange/Sub-conta).
* **Função:** Rastreia o `MaintenanceMargin` (margem de manutenção) e o `LiquidationPrice`.
* **Margin Calls:** Caso a Exchange envie um evento de risco ou o preço chegue perto da liquidação, o `Portfolio` dispara um evento de **`Emergency_Liquidation`** para o Runner proprietário, forçando o fechamento da posição para proteger o colateral restante.

### D. Sincronização e Divergência Externa

A Exchange é a única fonte de verdade para a alavancagem ativa. O Runner valida a alavancagem no `Boot` e monitora alterações via WebSocket. Em caso de alteração manual externa, o Runner prioriza o valor real da Exchange para o cálculo de margem, garantindo que o `ReservedBalance` solicitado ao Portfolio seja tecnicamente viável na Exchange.

### E. Implicações Arquiteturais

| Responsabilidade          | Dono             | Justificativa                                                                  |
|---------------------------|------------------|--------------------------------------------------------------------------------|
| **Definição de Leverage** | `StrategyRunner` | É uma decisão da estratégia de trade.                                          |
| **Validação de Limites**  | `Portfolio`      | Garante que a soma das margens de todos os Runners não exceda o risco global.  |
| **Cálculo de Colateral**  | `Portfolio`      | É quem detém a autoridade sobre o saldo real (`GlobalBalance`).                |

---

## 2. Boot Sequence — Sincronização de Alavancagem (Leverage Sync)

> **Origem:** Blueprint v16, Seção 6.D, Passo 5.

O Runner consulta a alavancagem real do símbolo na Exchange. Se houver divergência com a configuração local, o Runner atualiza sua `MarginAccount` interna para refletir a realidade da Exchange ("A Exchange é a Lei"), garantindo que novos cálculos de margem sejam precisos.

---

## 3. GAP — Consistência entre MarginAccount e GlobalBalance

> **Origem:** BLUEPRINT_QUESTOES.md, GAP #10.

**Local:** Seção 11.2.C (do Blueprint atual, antiga 12.2.C)

**O que estava escrito:**
> "MarginAccount rastreia o MaintenanceMargin e o LiquidationPrice."

**O que falta:**
- **Como o Portfolio atualiza o MaintenanceMargin em tempo real?**
- O preço do ativo oscila, a margem de manutenção sobe/desce.
- O Portfolio precisa de um **worker de precificação** ou ouvir WebSocket de mark price.
- **Isso é responsabilidade do Portfolio ou do ExchangeAdapter?**
- **A atualização é síncrona (bloqueia novos trades) ou assíncrona?**

**Classificação:** **Funcionalidade não modelada.**
**Impacto:** Risco de margem insuficiente não detectada até o Capital Request.

---

## 4. Notas de Implementação — Alavancagem e Margem

> **Origem:** IMPLEMENTATION_GUIDE_QUESTOES.md, Notas 14 e 15.

### Nota 14 — Sincronização de Alavancagem na Exchange (SET_LEVERAGE)

* **Requisito:** Antes de enviar a primeira ordem de um Runner, o sistema deve garantir que o comando `SET_LEVERAGE` foi executado com sucesso na Exchange para o símbolo correspondente.
* **Implementação:** Adicionar um passo de "Handshake de Risco" no boot do Runner para configurar o modo de margem (Isolated vs Cross) e o nível de alavancagem.
* **Detecção de Alteração Externa:** Se um administrador altera a alavancagem diretamente no site da Exchange, o sistema detecta?
* **Persistência:** A alavancagem é estado do Runner ou do Portfolio?
* **Rejeição:** Qual é o fluxo se a Exchange rejeitar uma tentativa de alteração de alavancagem?

### Nota 15 — Monitoramento de Margem de Manutenção (MaintenanceMargin)

* **Desafio:** O valor da margem necessária muda conforme o preço do ativo oscila (Mark Price).
* **Solução:** O `Portfolio` deve possuir um worker de background (ou ouvir websockets de conta) que atualiza o `MarginUsage` global em tempo real. Se o uso de margem ultrapassar 90%, o Circuit Breaker Global deve impedir novas aberturas.
