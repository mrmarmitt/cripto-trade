package com.marmitt.application.spring.metrics;

import com.marmitt.core.domain.strategy.StrategyEndpoint;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.UUID;

/**
 * Métricas da chamada a estratégias remotas (T41): latência por {@code strategyRef} e falhas por
 * motivo. Decorator técnico — a decisão de tratar falha como HOLD é do core.
 */
public class MeteredStrategyEvaluationPort implements StrategyEvaluationPort {

    static final String LATENCY = "strategy.remote.latency";
    static final String FAILURES = "strategy.remote.failures";

    private final StrategyEvaluationPort delegate;
    private final MeterRegistry meterRegistry;

    public MeteredStrategyEvaluationPort(StrategyEvaluationPort delegate, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public StrategyOutputDto evaluate(UUID strategyRef, StrategyEndpoint endpoint, StrategyInputDto input,
                                      StrategyContextDto context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return delegate.evaluate(strategyRef, endpoint, input, context);
        } catch (StrategyUnavailableException e) {
            meterRegistry.counter(FAILURES, "strategyRef", strategyRef.toString(), "reason", e.reason().name()).increment();
            throw e;
        } finally {
            sample.stop(Timer.builder(LATENCY).tag("strategyRef", strategyRef.toString())
                    .publishPercentileHistogram().register(meterRegistry));
        }
    }

    @Override
    public RemoteStrategyIdentity describe(UUID strategyRef, StrategyEndpoint endpoint) {
        return delegate.describe(strategyRef, endpoint);
    }
}
