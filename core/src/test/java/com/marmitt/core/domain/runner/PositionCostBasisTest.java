package com.marmitt.core.domain.runner;

import com.marmitt.core.enums.PositionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T46: o custo baixado nas vendas soma exatamente o quote pago, mesmo quando a fee de BUY no ativo
 * base deixa o preco medio sem representacao exata.
 */
class PositionCostBasisTest {

    // 0.001 BTC executado a 66233.70 com fee 0.000001 BTC: recebe 0.000999 pagando 66.23370000.
    private static final BigDecimal NET_QTY = new BigDecimal("0.000999");
    private static final BigDecimal PAID = new BigDecimal("66.23370000");

    @Test
    void openKeepsPaidCostAndDerivesAveragePrice() {
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, PAID);

        assertAmount("66.23370000", position.getCostBasis());
        assertAmount("66300.00000000", position.getAveragePrice());
    }

    @Test
    void sellingTheWholeLotReleasesExactlyThePaidCost() {
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", new BigDecimal("0.0007"),
                new BigDecimal("46.36359000"));

        BigDecimal soldCost = position.reduceQuantity(new BigDecimal("0.0007"), new BigDecimal("66300"), BigDecimal.ZERO);

        assertAmount("46.36359000", soldCost);
        assertAmount("0", position.getCostBasis());
        assertEquals(PositionStatus.CLOSED, position.getStatus());
    }

    @Test
    void partialSellPlusDustWriteOffSumToThePaidCost() {
        // Custo que deixa o preco medio sem representacao exata (dizima): a soma das baixas ainda
        // fecha com o pago, porque a ultima baixa e o restante do costBasis.
        BigDecimal paid = new BigDecimal("66.23371234");
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, paid);

        BigDecimal soldCost = position.reduceQuantity(new BigDecimal("0.00099"), new BigDecimal("66400"), BigDecimal.ZERO);
        BigDecimal dustCost = position.writeOffDust();

        assertAmount(paid.toPlainString(), soldCost.add(dustCost));
        assertAmount("0", position.getQuantity());
        assertAmount("0", position.getCostBasis());
        assertEquals(PositionStatus.CLOSED, position.getStatus());
    }

    @Test
    void dustWriteOffIsRealizedAsLossOfItsCost() {
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, PAID);
        position.reduceQuantity(new BigDecimal("0.00099"), new BigDecimal("66300"), BigDecimal.ZERO);
        BigDecimal pnlBefore = position.getRealizedPnl();

        BigDecimal dustCost = position.writeOffDust();

        assertTrue(dustCost.signum() > 0);
        assertAmount(pnlBefore.subtract(dustCost).toPlainString(), position.getRealizedPnl());
    }

    @Test
    void dustWriteOffRejectsLockedPosition() {
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, PAID);
        position.lock(UUID.randomUUID(), NET_QTY);

        assertThrows(IllegalStateException.class, position::writeOffDust);
    }

    @Test
    void addQuantityAccumulatesCostAcrossBuys() {
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, PAID);

        position.addQuantity(NET_QTY, new BigDecimal("66.13370000"));

        assertAmount("132.36740000", position.getCostBasis());
        assertAmount("0.001998", position.getQuantity());
    }

    @Test
    void reconstitutedRowWithoutCostBasisDerivesItFromAveragePrice() {
        Position position = Position.reconstitute()
                .id(UUID.randomUUID())
                .runnerId(UUID.randomUUID())
                .symbol("BTCUSDT")
                .status(PositionStatus.OPEN)
                .quantity(new BigDecimal("0.001"))
                .averagePrice(new BigDecimal("60000"))
                .realizedPnl(BigDecimal.ZERO)
                .openedAt(java.time.Instant.now())
                .updatedAt(java.time.Instant.now())
                .build();

        assertAmount("60", position.getCostBasis());
    }

    @Test
    void unrealizedPnlIsMarketValueMinusPaidCost() {
        // T43: o flutuante usa o custo pago, nao o preco medio arredondado.
        Position position = Position.open(UUID.randomUUID(), "BTCUSDT", NET_QTY, PAID);

        position.updateCurrentPrice(new BigDecimal("60000"));

        assertAmount("-6.29370000", position.getUnrealizedPnl());
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }
}
