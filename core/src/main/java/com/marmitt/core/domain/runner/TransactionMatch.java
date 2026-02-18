package com.marmitt.core.domain.runner;

import com.marmitt.core.domain.shared.Fee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro imutável de uma execução de matching entre compra e venda.
 * Ganha PK própria, {@code runnerId}, preços denormalizados, Fee VO embutido
 * e PnL realizado — permite cálculos sem JOINs adicionais.
 * <p>
 * <b>Constraint DB:</b> {@code UNIQUE(buyTransactionId, sellTransactionId)} garante
 * integridade de matching sem ser a PK.
 * <p>
 * <b>Imutabilidade:</b> TransactionMatch nunca é alterado após criação — é um registro
 * histórico de uma execução. Use o factory method {@link #create} para instanciar.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.3.4, Blueprint 4.D, 9.1, 9.3</a>
 */
public record TransactionMatch(
        UUID id,
        UUID runnerId,
        UUID buyTransactionId,
        UUID sellTransactionId,
        BigDecimal matchedQuantity,

        /** Preço de execução da compra no momento do match (em quote asset). Denormalizado. */
        BigDecimal buyPrice,

        /** Preço de execução da venda no momento do match (em quote asset). Denormalizado. */
        BigDecimal sellPrice,

        /**
         * Fee desta execução. Pode ser cross-currency (ex: BNB).
         * Se {@code fee.isPendingConversion()}, Portfolio converte via Mark Price
         * e registra em DustAccount em caso de falha.
         */
        Fee fee,

        /**
         * PnL líquido deste match:
         * {@code (sellPrice - buyPrice) × matchedQuantity - fee.convertedAmountOrZero()}.
         * Em quote asset.
         */
        BigDecimal pnlRealized,

        Instant createdAt
) {
    public TransactionMatch {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(buyTransactionId, "buyTransactionId cannot be null");
        Objects.requireNonNull(sellTransactionId, "sellTransactionId cannot be null");
        Objects.requireNonNull(matchedQuantity, "matchedQuantity cannot be null");
        Objects.requireNonNull(buyPrice, "buyPrice cannot be null");
        Objects.requireNonNull(sellPrice, "sellPrice cannot be null");
        Objects.requireNonNull(fee, "fee cannot be null");
        Objects.requireNonNull(pnlRealized, "pnlRealized cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");

        if (matchedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("matchedQuantity must be positive");
        }
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("buyPrice must be positive");
        }
        if (sellPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("sellPrice must be positive");
        }
    }

    /**
     * Factory method para criação de um novo match.
     * Calcula automaticamente o {@code pnlRealized} a partir dos preços e fee.
     *
     * @param runnerId         Runner que originou as transações
     * @param buyTransactionId FK para a Transaction de compra
     * @param sellTransactionId FK para a Transaction de venda
     * @param matchedQuantity  quantidade casada neste match (em base asset)
     * @param buyPrice         preço de execução da compra (em quote asset)
     * @param sellPrice        preço de execução da venda (em quote asset)
     * @param fee              fee desta execução (pode ser cross-currency)
     */
    public static TransactionMatch create(
            UUID runnerId,
            UUID buyTransactionId,
            UUID sellTransactionId,
            BigDecimal matchedQuantity,
            BigDecimal buyPrice,
            BigDecimal sellPrice,
            Fee fee
    ) {
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(buyTransactionId, "buyTransactionId cannot be null");
        Objects.requireNonNull(sellTransactionId, "sellTransactionId cannot be null");
        Objects.requireNonNull(matchedQuantity, "matchedQuantity cannot be null");
        Objects.requireNonNull(buyPrice, "buyPrice cannot be null");
        Objects.requireNonNull(sellPrice, "sellPrice cannot be null");
        Objects.requireNonNull(fee, "fee cannot be null");

        BigDecimal grossPnl = sellPrice.subtract(buyPrice)
                .multiply(matchedQuantity)
                .setScale(8, RoundingMode.HALF_UP);
        BigDecimal pnlRealized = grossPnl.subtract(fee.getConvertedAmountOrZero())
                .setScale(8, RoundingMode.HALF_UP);

        return new TransactionMatch(
                UUID.randomUUID(),
                runnerId,
                buyTransactionId,
                sellTransactionId,
                matchedQuantity,
                buyPrice,
                sellPrice,
                fee,
                pnlRealized,
                Instant.now()
        );
    }
}
