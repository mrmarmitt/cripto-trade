package com.marmitt.application.spring.config.exchange;

import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Decora as consultas REST de ordens de uma exchange com um circuit breaker (T24). Só proteção
 * técnica: com o circuito fechado o comportamento é o do adapter.
 *
 * <p>Com o circuito aberto a chamada falha na hora com {@link ExchangeQueryException} {@code TEMPORARY},
 * o mesmo tipo de uma falha de rede: o retry do boot recovery e o próximo ciclo do watchdog já tratam
 * esse caso, sem caminho novo no core.
 */
public class CircuitBreakerExchangeOrderQuery implements ExchangeOrderQueryPort {

    private final String exchangeId;
    private final ExchangeOrderQueryPort delegate;
    private final CircuitBreaker circuitBreaker;

    public CircuitBreakerExchangeOrderQuery(String exchangeId, ExchangeOrderQueryPort delegate,
                                            CircuitBreaker circuitBreaker) {
        this.exchangeId = exchangeId;
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId) {
        return call(() -> delegate.queryOrderByClientOrderId(symbol, clientOrderId));
    }

    @Override
    public Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId) {
        return call(() -> delegate.queryOrderByExchangeOrderId(symbol, exchangeOrderId));
    }

    @Override
    public List<OrderDataDto> listOpenOrdersBySymbol(String symbol) {
        return call(() -> delegate.listOpenOrdersBySymbol(symbol));
    }

    @Override
    public List<OrderDataDto> listAllOpenOrders() {
        return call(delegate::listAllOpenOrders);
    }

    private <T> T call(Supplier<T> query) {
        try {
            return circuitBreaker.executeSupplier(query);
        } catch (CallNotPermittedException e) {
            throw new ExchangeQueryException(exchangeId, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Order query circuit open for " + exchangeId + " - exchange REST failing, retry later", e);
        }
    }
}
