package com.marmitt.core.enums;

/**
 * Níveis escaláveis do Safe Mode do Portfolio.
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
     * Novos trades bloqueados — Runners ativos apenas monitoram
     */
    HALT,

    /**
     * Cancelar todas as ordens em voo
     */
    CANCEL_ALL,

    /**
     * Vender todas as posições abertas imediatamente
     */
    PANIC_SELL;

    /**
     * Verifica se o Safe Mode está ativo (qualquer nível acima de NORMAL)
     */
    public boolean isActive() {
        return this != NORMAL;
    }
}
