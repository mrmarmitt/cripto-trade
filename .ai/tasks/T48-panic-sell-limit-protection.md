# T48 — Proteção LIMIT na Liquidação do PANIC_SELL

**Complexidade:** Média
**Responsável:** Claude
**Dependências:** T45 (PANIC_SELL com MARKET). Relaciona-se com T40 (contrato da estratégia).
**Status:** Pendente — **decisão de negócio pendente** (parâmetros da proteção).

---

## Descrição

A T45 entregou o PANIC_SELL com ordem **MARKET**, por decisão do mantenedor: dá saída garantida, mas
num livro raso a venda pode executar muito abaixo do último preço. O mantenedor quer, num segundo
momento, uma **opção de segurança LIMIT configurada na estratégia**.

## Solução proposta (a validar)

1. A estratégia (ou o runner) declara a política de liquidação: `MARKET` (padrão atual) ou `LIMIT`
   com afastamento máximo do último preço (ex.: 1%).
2. Com `LIMIT`, o `SafeModeHandler` envia SELL LIMIT ao preço `último × (1 − afastamento)`; se não
   preencher em N ticks, cancela e reenvia ao novo preço (ou escala para MARKET, se configurado).
3. A decisão de tipo continua no core (`Transaction.orderType`); o adapter só traduz.

## Decisões pendentes

- Onde mora a configuração: contrato da estratégia (T40) ou cadastro do runner.
- Afastamento padrão e comportamento quando a LIMIT não preenche (reenviar × escalar para MARKET).

## Critérios de aceitação

1. Runner com política LIMIT liquida com SELL LIMIT dentro do afastamento configurado.
2. Sem preenchimento, o comportamento configurado (reenvio/escalada) acontece sem SELL duplicada.
3. Runner sem configuração mantém o MARKET da T45.
