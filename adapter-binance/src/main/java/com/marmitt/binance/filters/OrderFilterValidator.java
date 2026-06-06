package com.marmitt.binance.filters;

import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
public class OrderFilterValidator {

    private final SymbolFilterCache filterCache;

    public OrderFilterValidator(SymbolFilterCache filterCache) {
        this.filterCache = filterCache;
    }

    public SendOrderRequest validate(SendOrderRequest request) {
        String symbol = request.getSymbol();
        SymbolFilters filters = filterCache.getFilters(symbol);

        BigDecimal quantity = applyLotSize(symbol, request.getQuantity(), filters);
        BigDecimal price = applyPriceFilter(request.getPrice(), filters);
        validateNotional(symbol, quantity, price, filters);

        if (quantity.compareTo(request.getQuantity()) != 0) {
            log.info("Quantity adjusted for {}: {} -> {} (stepSize={})",
                    symbol, request.getQuantity(), quantity, filters.stepSize());
        }

        return new SendOrderRequest(
                request.getExchangeName(),
                symbol,
                quantity,
                price,
                request.getOrderType(),
                request.getOrderSide(),
                request.getClientOrderId()
        );
    }

    private BigDecimal applyLotSize(String symbol, BigDecimal quantity, SymbolFilters filters) {
        BigDecimal stepSize = filters.stepSize();
        if (stepSize.compareTo(BigDecimal.ZERO) == 0) return quantity;

        BigDecimal adjusted = quantity.divide(stepSize, 0, RoundingMode.FLOOR).multiply(stepSize).stripTrailingZeros();

        if (adjusted.compareTo(filters.minQty()) < 0) {
            throw new OrderFilterViolationException(
                    "Quantity " + quantity + " rounds down to " + adjusted.toPlainString() +
                    ", below minQty " + filters.minQty().toPlainString() + " for " + symbol);
        }
        if (filters.maxQty().compareTo(BigDecimal.ZERO) > 0 && adjusted.compareTo(filters.maxQty()) > 0) {
            throw new OrderFilterViolationException(
                    "Quantity " + adjusted.toPlainString() +
                    " exceeds maxQty " + filters.maxQty().toPlainString() + " for " + symbol);
        }

        return adjusted;
    }

    private BigDecimal applyPriceFilter(BigDecimal price, SymbolFilters filters) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) return price;
        BigDecimal tickSize = filters.tickSize();
        if (tickSize.compareTo(BigDecimal.ZERO) == 0) return price;
        return price.divide(tickSize, 0, RoundingMode.HALF_UP).multiply(tickSize).stripTrailingZeros();
    }

    private void validateNotional(String symbol, BigDecimal quantity, BigDecimal price, SymbolFilters filters) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) return;
        BigDecimal notional = price.multiply(quantity);
        if (notional.compareTo(filters.minNotional()) < 0) {
            throw new OrderFilterViolationException(
                    "Notional " + notional.toPlainString() +
                    " (price=" + price.toPlainString() + ", qty=" + quantity.toPlainString() +
                    ") is below minNotional " + filters.minNotional().toPlainString() + " for " + symbol);
        }
    }
}
