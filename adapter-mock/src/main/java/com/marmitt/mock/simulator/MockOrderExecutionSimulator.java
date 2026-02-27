package com.marmitt.mock.simulator;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.mock.balance.MockBalanceStore;
import com.marmitt.mock.config.MockScenarioConfig;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Simula execução de ordens para o Mock Adapter.
 * Configuração inicial: 100% de sucesso, latência fixa, sem taxas.
 */
@Slf4j
public class MockOrderExecutionSimulator {

    /**
     * Simula execução de uma ordem com comportamento configurado.
     *
     * Configuração atual (simplificada):
     * - Taxa de sucesso: 100% (sempre FILLED)
     * - Latência: 200ms (fixa)
     * - Taxa de execução: 0% (sem fees)
     * - Slippage: 0 (executa no preço exato solicitado)
     */
    public OrderDataDto simulateExecution(SendOrderRequest request) {
        log.debug("Simulating order execution - ClientOrderId: {}, Symbol: {}, Side: {}, Quantity: {}, Price: {}",
                request.getClientOrderId(), request.getSymbol(), request.getOrderSide(),
                request.getQuantity(), request.getPrice());

        // Gerar ID único para a ordem mockada
        String mockOrderId = generateMockOrderId();

        // Simular ordem totalmente executada (100% sucesso)
        OrderDataDto response = new OrderDataDto(
                mockOrderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                request.getQuantity(),  // executedQuantity = quantity (100% executado)
                request.getPrice(),
                request.getPrice(),     // executedPrice = price (sem slippage)
                BigDecimal.ZERO,        // fee = 0 (simplificado)
                OrderDataDto.OrderStatus.FILLED,
                null,                   // rejectReason = null (sucesso)
                Instant.now()
        );

        log.info("Order simulated successfully - OrderId: {}, ClientOrderId: {}, Status: FILLED",
                mockOrderId, request.getClientOrderId());

        return response;
    }

    public List<OrderDataDto> buildScenarioEvents(SendOrderRequest request,
                                                  String orderId,
                                                  MockScenarioConfig config,
                                                  Random random,
                                                  MockBalanceStore balanceStore) {
        List<OrderDataDto> rejected = validateOrder(request, orderId, config, balanceStore);
        if (!rejected.isEmpty()) {
            return applyOrderingAndDuplicates(rejected, config, random);
        }

        List<OrderDataDto> events = new ArrayList<>();

        OrderDataDto accepted = simulateAccepted(request, orderId);
        events.add(accepted);

        BigDecimal baseExecutedPrice = calculateExecutedPrice(request, config, random);

        OrderDataDto.OrderStatus failureStatus = pickFailureStatus(config, random);
        if (failureStatus != null) {
            if (failureStatus == OrderDataDto.OrderStatus.CANCELED) {
                events.add(simulateCanceled(request, orderId, baseExecutedPrice));
            } else {
                events.add(simulateExpired(request, orderId, baseExecutedPrice));
            }
            return applyOrderingAndDuplicates(events, config, random);
        }

        int partialCount = Math.max(0, config.flow().partialFillCount());
        if (partialCount == 0) {
            BigDecimal fee = calculateFee(request.getQuantity(), baseExecutedPrice, config);
            events.add(simulateFilled(request, orderId, baseExecutedPrice, fee));
            applyBalanceForFill(request, balanceStore, baseExecutedPrice, request.getQuantity(), fee);
            return applyOrderingAndDuplicates(events, config, random);
        }

        List<BigDecimal> fractions = resolveFractions(partialCount, config.flow().partialFillFractions());
        BigDecimal previousExecuted = BigDecimal.ZERO;
        for (BigDecimal fraction : fractions) {
            BigDecimal executedQty = request.getQuantity().multiply(fraction).setScale(8, RoundingMode.HALF_UP);
            BigDecimal increment = executedQty.subtract(previousExecuted);
            if (increment.compareTo(BigDecimal.ZERO) < 0) {
                increment = BigDecimal.ZERO;
            }
            previousExecuted = executedQty;
            BigDecimal eventPrice = calculateEventExecutedPrice(request, baseExecutedPrice, config, random);
            BigDecimal fee = calculateFee(increment, eventPrice, config);
            events.add(simulatePartial(request, orderId, executedQty, eventPrice, fee));
            applyBalanceForFill(request, balanceStore, eventPrice, increment, fee);
        }

        BigDecimal finalIncrement = request.getQuantity().subtract(previousExecuted);
        if (finalIncrement.compareTo(BigDecimal.ZERO) < 0) {
            finalIncrement = BigDecimal.ZERO;
        }
        BigDecimal finalPrice = calculateEventExecutedPrice(request, baseExecutedPrice, config, random);
        BigDecimal finalFee = calculateFee(finalIncrement, finalPrice, config);
        events.add(simulateFilled(request, orderId, finalPrice, finalFee));
        applyBalanceForFill(request, balanceStore, finalPrice, finalIncrement, finalFee);
        return applyOrderingAndDuplicates(events, config, random);
    }

    public OrderDataDto simulateAccepted(SendOrderRequest request, String orderId) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                BigDecimal.ZERO,
                request.getPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.NEW,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulatePartial(SendOrderRequest request, String orderId,
                                        BigDecimal executedQty, BigDecimal executedPrice, BigDecimal fee) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                executedQty,
                request.getPrice(),
                executedPrice,
                fee,
                OrderDataDto.OrderStatus.PARTIALLY_FILLED,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulateFilled(SendOrderRequest request, String orderId,
                                       BigDecimal executedPrice, BigDecimal fee) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                request.getQuantity(),
                request.getPrice(),
                executedPrice,
                fee,
                OrderDataDto.OrderStatus.FILLED,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulateCanceled(SendOrderRequest request, String orderId, BigDecimal executedPrice) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                BigDecimal.ZERO,
                request.getPrice(),
                executedPrice,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.CANCELED,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulateExpired(SendOrderRequest request, String orderId, BigDecimal executedPrice) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                BigDecimal.ZERO,
                request.getPrice(),
                executedPrice,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.EXPIRED,
                null,
                Instant.now()
        );
    }

    public OrderDataDto simulateRejected(SendOrderRequest request, String orderId, String reason) {
        return new OrderDataDto(
                orderId,
                request.getClientOrderId(),
                Symbol.of(request.getSymbol()),
                convertOrderSide(request.getOrderSide()),
                convertOrderType(request.getOrderType()),
                request.getQuantity(),
                BigDecimal.ZERO,
                request.getPrice(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                reason,
                Instant.now()
        );
    }

    /**
     * Gera ID único para ordem mockada
     */
    private String generateMockOrderId() {
        return "MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderDataDto.OrderStatus pickFailureStatus(MockScenarioConfig config, Random random) {
        double cancelRatio = config.failures().cancelRatio();
        double expireRatio = config.failures().expireRatio();
        double roll = random.nextDouble();
        if (roll < cancelRatio) {
            return OrderDataDto.OrderStatus.CANCELED;
        }
        if (roll < cancelRatio + expireRatio) {
            return OrderDataDto.OrderStatus.EXPIRED;
        }
        return null;
    }

    private List<OrderDataDto> applyOrderingAndDuplicates(List<OrderDataDto> baseEvents,
                                                          MockScenarioConfig config,
                                                          Random random) {
        List<OrderDataDto> events = new ArrayList<>();
        int maxDup = config.duplicates().maxDuplicatesPerEvent();
        for (OrderDataDto event : baseEvents) {
            events.add(event);
            if (maxDup > 0 && random.nextDouble() < config.duplicates().ratio()) {
                int dupCount = 1 + random.nextInt(maxDup);
                for (int i = 0; i < dupCount; i++) {
                    events.add(event);
                }
            }
        }

        if (events.size() > 1 && random.nextDouble() < config.outOfOrder().ratio()) {
            Collections.shuffle(events, random);
        }
        return events;
    }

    private List<BigDecimal> resolveFractions(int partialCount,
                                              List<BigDecimal> configuredFractions) {
        if (configuredFractions == null || configuredFractions.isEmpty()) {
            BigDecimal step = BigDecimal.ONE.divide(BigDecimal.valueOf(partialCount + 1L), 8, RoundingMode.HALF_UP);
            List<BigDecimal> fractions = new ArrayList<>();
            BigDecimal cumulative = BigDecimal.ZERO;
            for (int i = 0; i < partialCount; i++) {
                cumulative = cumulative.add(step);
                fractions.add(cumulative);
            }
            return fractions;
        }

        List<BigDecimal> fractions = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (BigDecimal fraction : configuredFractions) {
            cumulative = cumulative.add(fraction);
            fractions.add(cumulative);
        }

        if (fractions.size() != partialCount || cumulative.compareTo(BigDecimal.ONE) >= 0) {
            BigDecimal step = BigDecimal.ONE.divide(BigDecimal.valueOf(partialCount + 1L), 8, RoundingMode.HALF_UP);
            fractions.clear();
            cumulative = BigDecimal.ZERO;
            for (int i = 0; i < partialCount; i++) {
                cumulative = cumulative.add(step);
                fractions.add(cumulative);
            }
        }
        return fractions;
    }

    private BigDecimal calculateFee(BigDecimal increment, BigDecimal price, MockScenarioConfig config) {
        if (increment == null || price == null) {
            return BigDecimal.ZERO;
        }
        if (increment.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        MockScenarioConfig.FeeSettings feeSettings = config.fees();
        if (feeSettings.mode() == MockScenarioConfig.FeeMode.NONE) {
            return BigDecimal.ZERO;
        }
        if (feeSettings.feeRate().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = increment.multiply(price);
        return gross.multiply(feeSettings.feeRate()).setScale(8, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateExecutedPrice(SendOrderRequest request,
                                              MockScenarioConfig config,
                                              Random random) {
        if (request.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        MockScenarioConfig.SlippageSettings slippage = config.slippage();
        if (slippage.mode() == MockScenarioConfig.SlippageMode.NONE) {
            return request.getPrice();
        }
        if (slippage.mode() == MockScenarioConfig.SlippageMode.MARKET_ONLY
                && request.getOrderType() != com.marmitt.core.enums.OrderType.MARKET) {
            return request.getPrice();
        }
        int maxBps = slippage.maxSlippageBps();
        if (maxBps <= 0) {
            return request.getPrice();
        }
        int stepBps = Math.max(1, slippage.priceStepBps());
        int steps = maxBps / stepBps;
        if (steps <= 0) {
            return request.getPrice();
        }
        int stepCount = 1 + random.nextInt(steps);
        int bps = stepCount * stepBps;
        boolean improve = random.nextDouble() < slippage.priceImprovementChance();
        int signedBps = improve ? -bps : bps;

        BigDecimal multiplier = BigDecimal.valueOf(10000L + signedBps)
                .divide(BigDecimal.valueOf(10000L), 8, RoundingMode.HALF_UP);

        BigDecimal executed = request.getPrice().multiply(multiplier).setScale(8, RoundingMode.HALF_UP);
        if (executed.compareTo(BigDecimal.ZERO) <= 0) {
            return request.getPrice();
        }
        return applyLimitConstraint(request, executed);
    }

    private BigDecimal calculateEventExecutedPrice(SendOrderRequest request,
                                                   BigDecimal basePrice,
                                                   MockScenarioConfig config,
                                                   Random random) {
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return calculateExecutedPrice(request, config, random);
        }
        MockScenarioConfig.SlippageSettings slippage = config.slippage();
        if (slippage.mode() == MockScenarioConfig.SlippageMode.NONE) {
            return basePrice;
        }
        int maxBps = slippage.maxSlippageBps();
        if (maxBps <= 0) {
            return basePrice;
        }
        int stepBps = Math.max(1, slippage.priceStepBps());
        int steps = Math.max(1, maxBps / stepBps);
        int stepCount = random.nextInt(steps + 1);
        int bps = stepCount * stepBps;
        boolean improve = random.nextDouble() < slippage.priceImprovementChance();
        int signedBps = improve ? -bps : bps;

        BigDecimal multiplier = BigDecimal.valueOf(10000L + signedBps)
                .divide(BigDecimal.valueOf(10000L), 8, RoundingMode.HALF_UP);
        BigDecimal executed = basePrice.multiply(multiplier).setScale(8, RoundingMode.HALF_UP);
        if (executed.compareTo(BigDecimal.ZERO) <= 0) {
            return basePrice;
        }
        return applyLimitConstraint(request, executed);
    }

    private BigDecimal applyLimitConstraint(SendOrderRequest request, BigDecimal executedPrice) {
        if (request.getOrderType() != com.marmitt.core.enums.OrderType.LIMIT) {
            return executedPrice;
        }
        BigDecimal limitPrice = request.getPrice();
        if (limitPrice == null) {
            return executedPrice;
        }
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY
                && executedPrice.compareTo(limitPrice) > 0) {
            return limitPrice;
        }
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.SELL
                && executedPrice.compareTo(limitPrice) < 0) {
            return limitPrice;
        }
        return executedPrice;
    }

    private List<OrderDataDto> validateOrder(SendOrderRequest request,
                                             String orderId,
                                             MockScenarioConfig config,
                                             MockBalanceStore balanceStore) {
        MockScenarioConfig.ValidationSettings validation = config.validation();
        BigDecimal qty = request.getQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of(simulateRejected(request, orderId, "INVALID_QTY"));
        }
        if (validation.minQty().compareTo(BigDecimal.ZERO) > 0
                && qty.compareTo(validation.minQty()) < 0) {
            return List.of(simulateRejected(request, orderId, "MIN_QTY"));
        }
        if (validation.stepSize().compareTo(BigDecimal.ZERO) > 0
                && !isMultipleOfStep(qty, validation.stepSize())) {
            return List.of(simulateRejected(request, orderId, "STEP_SIZE"));
        }

        BigDecimal price = request.getPrice();
        if (validation.minNotional().compareTo(BigDecimal.ZERO) > 0) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                return List.of(simulateRejected(request, orderId, "MISSING_PRICE"));
            }
            BigDecimal notional = qty.multiply(price).setScale(8, RoundingMode.HALF_UP);
            if (notional.compareTo(validation.minNotional()) < 0) {
                return List.of(simulateRejected(request, orderId, "MIN_NOTIONAL"));
            }
        }

        if (!reserveForRequest(request, config, balanceStore)) {
            return List.of(simulateRejected(request, orderId, "INSUFFICIENT_BALANCE"));
        }
        return List.of();
    }

    private boolean isMultipleOfStep(BigDecimal value, BigDecimal step) {
        if (step.compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        BigDecimal remainder = value.remainder(step);
        return remainder.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean reserveForRequest(SendOrderRequest request,
                                      MockScenarioConfig config,
                                      MockBalanceStore balanceStore) {
        Symbol symbol = Symbol.of(request.getSymbol());
        String base = symbol.getBaseAsset();
        String quote = symbol.getQuoteAsset();
        BigDecimal qty = request.getQuantity();
        BigDecimal price = request.getPrice();
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY) {
            if (price == null) {
                return false;
            }
            BigDecimal estimatedFee = calculateFee(qty, price, config);
            BigDecimal required = qty.multiply(price).add(estimatedFee).setScale(8, RoundingMode.HALF_UP);
            return balanceStore.reserve(quote, required);
        }
        return balanceStore.reserve(base, qty);
    }

    private void applyBalanceForFill(SendOrderRequest request,
                                     MockBalanceStore balanceStore,
                                     BigDecimal executedPrice,
                                     BigDecimal increment,
                                     BigDecimal fee) {
        if (increment == null || increment.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        Symbol symbol = Symbol.of(request.getSymbol());
        String base = symbol.getBaseAsset();
        String quote = symbol.getQuoteAsset();
        if (request.getOrderSide() == com.marmitt.core.enums.OrderSide.BUY) {
            BigDecimal cost = increment.multiply(executedPrice).add(fee).setScale(8, RoundingMode.HALF_UP);
            balanceStore.debitReserved(quote, cost);
            balanceStore.credit(base, increment);
            return;
        }

        balanceStore.debitReserved(base, increment);
        BigDecimal proceeds = increment.multiply(executedPrice).subtract(fee).setScale(8, RoundingMode.HALF_UP);
        balanceStore.credit(quote, proceeds);
    }

    /**
     * Converte OrderSide do request para OrderSide do DTO
     */
    private OrderDataDto.OrderSide convertOrderSide(com.marmitt.core.enums.OrderSide orderSide) {
        return switch (orderSide) {
            case BUY -> OrderDataDto.OrderSide.BUY;
            case SELL -> OrderDataDto.OrderSide.SELL;
        };
    }

    /**
     * Converte OrderType do request para OrderType do DTO
     */
    private OrderDataDto.OrderType convertOrderType(com.marmitt.core.enums.OrderType orderType) {
        return switch (orderType) {
            case MARKET -> OrderDataDto.OrderType.MARKET;
            case LIMIT -> OrderDataDto.OrderType.LIMIT;
            case STOP_LOSS, STOP_LOSS_LIMIT -> OrderDataDto.OrderType.STOP;
            case TAKE_PROFIT, TAKE_PROFIT_LIMIT -> OrderDataDto.OrderType.STOP_LIMIT;
        };
    }
}
