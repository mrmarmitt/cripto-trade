package com.marmitt.binance.filters;

import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * Valida uma ordem contra os filtros de símbolo da Binance no momento do dispatch.
 *
 * <p><b>Rejeição-only:</b> o alinhamento de quantidade ({@code stepSize}) e preço
 * ({@code tickSize}) é responsabilidade do {@link BinanceOrderNormalizer}, aplicado no core
 * antes do persist/reserva. Aqui o validador apenas <em>verifica</em>: se a ordem chega
 * desalinhada ou fora dos limites, lança {@link OrderFilterViolationException} — indício de
 * bug no chamador, não algo a ser silenciosamente ajustado.
 */
@Slf4j
public class OrderFilterValidator {

    private final SymbolFilterCache filterCache;

    public OrderFilterValidator(SymbolFilterCache filterCache) {
        this.filterCache = filterCache;
    }

    public SendOrderRequest validate(SendOrderRequest request) {
        String symbol = request.getSymbol();
        SymbolFilters filters = filterCache.getFilters(symbol);

        validateLotSize(symbol, request.getQuantity(), filters);
        validatePriceTick(symbol, request.getPrice(), filters);
        validateNotional(symbol, request.getQuantity(), request.getPrice(), filters);

        return request;
    }

    private void validateLotSize(String symbol, BigDecimal quantity, SymbolFilters filters) {
        BigDecimal stepSize = filters.stepSize();
        if (stepSize.compareTo(BigDecimal.ZERO) > 0
                && quantity.remainder(stepSize).compareTo(BigDecimal.ZERO) != 0) {
            throw new OrderFilterViolationException(
                    "Quantity " + quantity.toPlainString() + " is not aligned to stepSize " +
                    stepSize.toPlainString() + " for " + symbol);
        }
        if (quantity.compareTo(filters.minQty()) < 0) {
            throw new OrderFilterViolationException(
                    "Quantity " + quantity.toPlainString() +
                    " is below minQty " + filters.minQty().toPlainString() + " for " + symbol);
        }
        if (filters.maxQty().compareTo(BigDecimal.ZERO) > 0 && quantity.compareTo(filters.maxQty()) > 0) {
            throw new OrderFilterViolationException(
                    "Quantity " + quantity.toPlainString() +
                    " exceeds maxQty " + filters.maxQty().toPlainString() + " for " + symbol);
        }
    }

    private void validatePriceTick(String symbol, BigDecimal price, SymbolFilters filters) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal tickSize = filters.tickSize();
        if (tickSize.compareTo(BigDecimal.ZERO) > 0
                && price.remainder(tickSize).compareTo(BigDecimal.ZERO) != 0) {
            throw new OrderFilterViolationException(
                    "Price " + price.toPlainString() + " is not aligned to tickSize " +
                    tickSize.toPlainString() + " for " + symbol);
        }
    }

    private void validateNotional(String symbol, BigDecimal quantity, BigDecimal price, SymbolFilters filters) {
        if (price == null || price.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal notional = price.multiply(quantity);
        if (notional.compareTo(filters.minNotional()) < 0) {
            throw new OrderFilterViolationException(
                    "Notional " + notional.toPlainString() +
                    " (price=" + price.toPlainString() + ", qty=" + quantity.toPlainString() +
                    ") is below minNotional " + filters.minNotional().toPlainString() + " for " + symbol);
        }
    }
}
