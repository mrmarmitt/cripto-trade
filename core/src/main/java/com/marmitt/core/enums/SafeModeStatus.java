package com.marmitt.core.enums;

/**
 * Níveis escaláveis do Safe Mode do Portfolio. Cada nível inclui as ações do anterior.
 * Persistido para sobreviver a restarts.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.5, Blueprint 11.1.B.1</a>
 */
public enum SafeModeStatus {

    /**
     * Operação normal — todos os Runners podem operar
     */
    NORMAL,

    /**
     * Novas compras bloqueadas (sem reserva de capital). A estratégia continua rodando e pode
     * vender: só reduz exposição (decisão da T45).
     */
    HALT,

    /**
     * HALT + cancelamento de todas as ordens LIMIT em voo. A estratégia deixa de ser consultada.
     */
    CANCEL_ALL,

    /**
     * CANCEL_ALL + venda a mercado de todas as posições abertas.
     */
    PANIC_SELL;

    /**
     * Verifica se o Safe Mode está ativo (qualquer nível acima de NORMAL)
     */
    public boolean isActive() {
        return this != NORMAL;
    }

    /**
     * A partir de {@code CANCEL_ALL} o Safe Mode conduz os runners no lugar da estratégia.
     */
    public boolean cancelsOrders() {
        return compareTo(CANCEL_ALL) >= 0;
    }

    public boolean liquidatesPositions() {
        return this == PANIC_SELL;
    }

    /**
     * Níveis de ação em massa exigem confirmação explícita do operador (Blueprint 11.1.B.1).
     */
    public boolean requiresConfirmation() {
        return cancelsOrders();
    }
}
