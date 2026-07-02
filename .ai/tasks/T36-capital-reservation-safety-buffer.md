# T36 — Safety Buffer na Reserva de Capital: documentado vs. não implementado

**Complexidade:** Média (decisão de negócio + eventual efeito financeiro)
**Responsável:** Claude
**Dependências:** nenhuma (independente das estratégias; toca o caminho de reserva de capital)
**Status:** Pendente — **decisão de negócio pendente** (implementar o buffer ou remover do design)

---

## Descrição

Há uma divergência **financeira** entre o design documentado e o código: o *safety buffer* de reserva de capital está **especificado, mas não implementado**.

O design descreve que a reserva de capital deve aplicar um multiplicador de segurança sobre o valor nominal, para cobrir slippage e fees entre o cálculo da intenção e a execução na exchange:

- `docs/IMPLEMENTATION_GUIDE.md` §7.2.1 — fórmula: `Capital_Requerido = Quantidade × Preço_Estimado × Safety_Buffer_Multiplier` (default `1.005`).
- `docs/IMPLEMENTATION_GUIDE.md` §7.2.2 — buffer **por tipo de ordem**: `MARKET` = `1.005` (0.5%), `LIMIT` = `1.001` (0.1%).
- `docs/IMPLEMENTATION_GUIDE.md` §7.2.3 — reconciliação pós-execução: o excedente (`Capital_Reservado − Capital_Efetivo`) é devolvido ao `AvailableBalance` via `confirmExecution()`/`release()`.
- `core/.../dto/capital/CapitalRequest.java` (Javadoc, ~linha 13) — *"`amount` inclui o safety buffer aplicado pelo Runner antes da chamada (ex.: `rawAmount * 1.005`…)"*.

**O código não faz isso.** `TradeIntentFactory.buildTransaction` calcula `total = quantity × price` (sem multiplicador) e `buildCapitalRequest` usa esse `total` direto como valor reservado. Ou seja, hoje a reserva é **exatamente o valor nominal**, sem margem para slippage/fees.

---

## Por que importa (risco)

- **Reserva pode ficar insuficiente:** o custo efetivo de um BUY inclui fees (e, em ordem a mercado, slippage). Sem buffer, o valor reservado pode ser **menor** que o custo real na conciliação do fill.
- **Invariante financeira:** `available` e `reserved` não podem ficar negativos (`docs/PHASE0_INVARIANTS.md`). Se o efetivo exceder o reservado, o ajuste na conciliação pode empurrar saldos para inconsistência ou exigir compensação não prevista.
- **Hoje só se opera `LIMIT` a preço de mercado** (ver T34): nesse caminho o preço é fixo e o gap é só arredondamento + fee — o risco é **baixo, porém não nulo** (fees não estão cobertas). Se a T34/T35 ou um cenário futuro introduzir ordens *marketable*/`MARKET`, o risco de slippage cresce e a ausência de buffer passa a ser relevante.

---

## Decisão de negócio (pré-requisito da implementação)

Esta task **não** deve implementar às cegas. Primeiro decidir entre:

- **Opção A — Implementar o buffer** conforme §7.2.1–7.2.3: aplicar o multiplicador na reserva e garantir a devolução do excedente na conciliação.
- **Opção B — Remover o buffer do design:** se a decisão consciente é operar sem buffer (ex.: apenas `LIMIT` com preço fixo, fees absorvidas por outra via), então **o documento é que está errado** e deve ser corrigido para refletir o comportamento real, eliminando §7.2.1–7.2.3 (ou reescrevendo-as como "sem buffer") e ajustando o Javadoc de `CapitalRequest`.

A escolha depende do apetite de risco e do roadmap de tipos de ordem. Registrar a decisão e a justificativa nesta task antes de codar.

---

## Escopo por opção

### Se Opção A (implementar)

1. Definir onde o buffer é aplicado. O design diz "pelo Runner antes da chamada" e que o buffer **não** afeta a ordem enviada — apenas o valor reservado (o dispatch continua com `quantity`/`price` originais). Candidato natural: `TradeIntentFactory.buildCapitalRequest` (ou um passo dedicado), mantendo `transaction.getPrice()`/`getQuantity()` intactos para o dispatch.
2. Buffer por tipo de ordem (§7.2.2). Como hoje só há `LIMIT`, começar por `1.001`; parametrizar por configuração (não hardcode).
3. **Garantir a reconciliação (§7.2.3):** o excedente `reservado − efetivo` deve voltar ao `Available` no `confirmExecution()`/`release()`. **Verificar se o caminho atual já devolve `reservado − efetivo`** — se a reserva passa a ser maior que o efetivo, essa devolução tem de existir e estar correta, senão o buffer vira capital preso.
4. Testes de invariante: reserva ≥ efetivo em fills normais; devolução do excedente; sem saldo negativo; idempotência na conciliação.

### Se Opção B (remover do design)

1. Corrigir `docs/IMPLEMENTATION_GUIDE.md` §7.2.1–7.2.3 e `docs/BLUEPRINT.md` onde referenciar buffer.
2. Corrigir o Javadoc de `core/.../dto/capital/CapitalRequest.java` (remover a afirmação de que `amount` inclui buffer).
3. Registrar a decisão (sem buffer) e o motivo.

---

## Arquivos envolvidos

| Arquivo | Papel |
|---|---|
| `core/.../usecase/runner/processsignal/TradeIntentFactory.java` | Reserva `total = quantity × price` (ponto onde o buffer entraria) |
| `core/.../dto/capital/CapitalRequest.java` | Javadoc afirma buffer incluso (divergente do código) |
| `core/.../` conciliação (`confirmExecution`/`release`) | Devolução do excedente reservado − efetivo (§7.2.3) — verificar/ajustar |
| `docs/IMPLEMENTATION_GUIDE.md` §7.2.1–7.2.3 | Design do buffer (fonte da divergência) |
| `docs/BLUEPRINT.md` | Referências a reserva/estorno de margem |

> Caminhos de conciliação a confirmar durante a implementação (não assumir): localizar os use cases que aplicam `confirmExecution`/`release` e validar como tratam `reservado ≠ efetivo` hoje.

---

## Riscos / pontos de atenção

- **Área de alto risco (efeito financeiro):** qualquer mudança no valor reservado toca invariantes de `GlobalBalance`. Preferir teste de comportamento cobrindo reserva, fill total, fill parcial + cancel e devolução do excedente.
- **Buffer vira capital preso se a devolução falhar:** implementar o buffer **sem** garantir §7.2.3 é pior que não ter buffer. A reconciliação da devolução é parte obrigatória da Opção A.
- **Consistência com a T34:** a T34 introduz `limitPrice`; a reserva passa a ser `quantity × limitPrice`. O buffer (se implementado) incide sobre esse valor. Não há conflito, mas as duas tasks tocam o mesmo ponto (`TradeIntentFactory`) — coordenar ordem de merge.
- **Não escopar tipo de ordem aqui:** `MARKET` continua fora do sistema (ver T34). O buffer `1.005` de MARKET só passa a valer se/quando `MARKET` existir; por ora, apenas o ramo `LIMIT`.

---

## Critérios de aceitação

1. Decisão de negócio registrada nesta task (Opção A ou B) com justificativa.
2. **Se A:** a reserva de capital aplica o buffer configurável por tipo de ordem; o valor enviado à exchange permanece o nominal (`quantity`/`price` originais); o excedente `reservado − efetivo` é devolvido na conciliação; invariantes de saldo cobertas por teste.
3. **Se B:** `docs/IMPLEMENTATION_GUIDE.md` §7.2.1–7.2.3, `docs/BLUEPRINT.md` e o Javadoc de `CapitalRequest` corrigidos para refletir "sem buffer"; nenhuma referência remanescente a `1.005`/`1.001` como comportamento vigente.
4. Em ambos os casos, **doc e código deixam de divergir** quanto ao buffer.

---

## Fontes

- `docs/IMPLEMENTATION_GUIDE.md` §7.2.1 / §7.2.2 / §7.2.3 (fórmula, buffer por tipo de ordem, reconciliação do excedente).
- `core/.../dto/capital/CapitalRequest.java` (Javadoc que afirma buffer incluso).
- `core/.../usecase/runner/processsignal/TradeIntentFactory.java` (`total = quantity × price`, sem buffer).
- `docs/PHASE0_INVARIANTS.md` (saldos não-negativos, reserva de BUY sem duplicar efeito).
- `.ai/flows/portfolio-capital.md` (fluxo de reserva/liberação de capital).
- Divergência detectada durante a especificação da T34 (contrato de decisão da estratégia).
