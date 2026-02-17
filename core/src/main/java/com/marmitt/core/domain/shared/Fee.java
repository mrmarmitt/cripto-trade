package com.marmitt.core.domain.shared;

import com.marmitt.core.enums.FeeType;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa o custo operacional imutável de uma execução.
 * Embutido no TransactionMatch como Value Object.
 * <p>
 * Se {@code asset} == baseCurrency do Portfolio, {@code convertedAmount} == {@code amount}.
 * Se {@code asset} != baseCurrency, Portfolio converte usando Mark Price no momento do match.
 * Em caso de falha na conversão, débito registrado na DustAccount como TECHNICAL_DEBT.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.4.2, Blueprint 9.1</a>
 */
public record Fee(
        BigDecimal amount,
        String asset,
        FeeType type,
        BigDecimal convertedAmount
) {
    public Fee {
        Objects.requireNonNull(amount, "Fee amount cannot be null");
        Objects.requireNonNull(asset, "Fee asset cannot be null");
        Objects.requireNonNull(type, "Fee type cannot be null");

        if (asset.isBlank()) {
            throw new IllegalArgumentException("Fee asset cannot be blank");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fee amount cannot be negative");
        }
    }

    /**
     * Cria uma Fee com conversão trivial (mesmo ativo que baseCurrency).
     */
    public static Fee sameBaseCurrency(BigDecimal amount, String asset, FeeType type) {
        return new Fee(amount, asset.toUpperCase(), type, amount);
    }

    /**
     * Cria uma Fee cross-currency com valor convertido.
     */
    public static Fee crossCurrency(BigDecimal amount, String asset, FeeType type, BigDecimal convertedAmount) {
        return new Fee(amount, asset.toUpperCase(), type, convertedAmount);
    }

    /**
     * Cria uma Fee cross-currency pendente de conversão (convertedAmount = null).
     */
    public static Fee pendingConversion(BigDecimal amount, String asset, FeeType type) {
        return new Fee(amount, asset.toUpperCase(), type, null);
    }

    /**
     * Cria uma Fee zero (sem custo).
     */
    public static Fee zero(String asset) {
        return new Fee(BigDecimal.ZERO, asset.toUpperCase(), FeeType.UNKNOWN, BigDecimal.ZERO);
    }

    /**
     * Verifica se a fee ainda precisa de conversão para baseCurrency.
     */
    public boolean isPendingConversion() {
        return convertedAmount == null;
    }

    /**
     * Retorna o valor convertido ou zero se pendente.
     */
    public BigDecimal getConvertedAmountOrZero() {
        return convertedAmount != null ? convertedAmount : BigDecimal.ZERO;
    }
}
