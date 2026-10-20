package com.marmitt.application.spring.metrics;

import com.marmitt.application.spring.bootstrap.BootStatusTracker;
import com.marmitt.core.dto.boot.BootRunSnapshot;
import com.marmitt.core.ports.outbound.operations.InfrastructureSignalsPort;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sinais técnicos para o estado operacional consolidado (T55): o resultado do boot e os circuit breakers
 * de consulta REST abertos (T24). Só lê; quem decide a severidade é o core.
 */
@Component
public class InfrastructureSignalsAdapter implements InfrastructureSignalsPort {

    private static final String ORDER_QUERY_SUFFIX = "-order-query";

    private final BootStatusTracker bootStatusTracker;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public InfrastructureSignalsAdapter(BootStatusTracker bootStatusTracker,
                                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.bootStatusTracker = bootStatusTracker;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public BootState bootState() {
        BootRunSnapshot snapshot = bootStatusTracker.snapshot();
        String detail = snapshot.failurePhase() == null ? null
                : snapshot.failurePhase() + ": " + snapshot.failureMessage();
        return new BootState(snapshot.status(), detail);
    }

    @Override
    public List<String> openRestCircuits() {
        return circuitBreakerRegistry.getAllCircuitBreakers().stream()
                .filter(breaker -> breaker.getState() == CircuitBreaker.State.OPEN
                        || breaker.getState() == CircuitBreaker.State.FORCED_OPEN)
                .map(CircuitBreaker::getName)
                .map(name -> name.endsWith(ORDER_QUERY_SUFFIX)
                        ? name.substring(0, name.length() - ORDER_QUERY_SUFFIX.length()).toUpperCase() : name)
                .sorted()
                .toList();
    }
}
