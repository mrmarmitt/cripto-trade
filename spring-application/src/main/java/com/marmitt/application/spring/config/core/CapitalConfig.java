package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.reaction.ExecutionConfirmedReaction;
import com.marmitt.core.application.reaction.MarginReleasedReaction;
import com.marmitt.core.ports.outbound.repository.CapitalEventIdempotencyPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração Spring das reactions de capital do Portfolio.
 * <p>
 * Registra os handlers de eventos publicados pelo Runner que afetam o {@code GlobalBalance}:
 * confirmação de execução (Reserved → Realized) e estorno de margem (Reserved → Available).
 */
@Configuration
public class CapitalConfig {

    @Bean
    public ExecutionConfirmedReaction executionConfirmedReaction(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            CapitalEventIdempotencyPort capitalEventIdempotencyPort) {
        return new ExecutionConfirmedReaction(
                runnerRepository,
                globalBalanceRepository,
                capitalEventIdempotencyPort
        );
    }

    @Bean
    public MarginReleasedReaction marginReleasedReaction(
            StrategyRunnerRepositoryPort runnerRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            CapitalEventIdempotencyPort capitalEventIdempotencyPort) {
        return new MarginReleasedReaction(
                runnerRepository,
                globalBalanceRepository,
                capitalEventIdempotencyPort
        );
    }
}
