package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.exchange.QueryExchangeBalanceUseCase;
import com.marmitt.core.ports.inbound.exchange.QueryExchangeBalancePort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring dos casos de uso agnósticos de consulta na exchange (saldo e histórico).
 *
 * <p>Despacham por {@code exchangeName} em runtime via {@link ExchangeAdapterRepositoryPort},
 * portanto não dependem de credenciais de nenhum provider específico.
 */
@Configuration
public class ExchangeQueryConfig {

    @Bean
    public QueryExchangeBalancePort queryExchangeBalanceUseCase(
            ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        return new QueryExchangeBalanceUseCase(exchangeAdapterRepository);
    }
}
