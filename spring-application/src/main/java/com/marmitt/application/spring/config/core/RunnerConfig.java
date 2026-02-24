package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.runner.OrderConciliationUseCase;
import com.marmitt.core.application.usecase.runner.processsignal.ProcessTradeSignalUseCase;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

/**
 * Configuração Spring dos serviços do domínio Runner e Portfolio (capital).
 * <p>
 * Declara beans para:
 * <ul>
 *   <li>Use cases do ciclo de vida de ordens ({@code NewProcessTradeSignal}, {@code OrderConciliation})</li>
 *   <li>Listeners de mercado e ordem do Runner ({@code PortfolioStrategyRunner*})</li>
 * </ul>
 * <p>
 * Os use cases são classes abstratas cujas fronteiras {@code @Transactional} são definidas
 * aqui via subclasses anônimas — mesmo padrão de {@link OrderConciliationUseCase}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1, 6.3</a>
 */
@Configuration
public class RunnerConfig {

    /**
     * Retorna {@code OrderConciliationUseCase} (tipo concreto) para que Spring possa injetá-lo
     * tanto como {@code OrderConciliationPort} quanto como {@code HandleOrderTerminationPort}.
     */
    @Bean
    public OrderConciliationUseCase createOrderConciliation(TransactionTemplate txTemplate,
                                                         StrategyRunnerRepositoryPort strategyRunnerRepository,
                                                         EventPublisherPort eventPublisher) {

        return new OrderConciliationUseCase(strategyRunnerRepository, eventPublisher) {

            @Override
            public void transactionalReleaseMargin(Transaction transaction) {
                txTemplate.executeWithoutResult(status -> releaseMargin(transaction));
            }

            @Override
            public void transactionalProcessFill(Transaction transaction, OrderDataDto orderData,
                                                 BigDecimal fillIncrement, BigDecimal fillPrice,
                                                 boolean isFinal) {
                txTemplate.executeWithoutResult(status ->
                        processFill(transaction, orderData, fillIncrement, fillPrice, isFinal));
            }
        };
    }

    @Bean
    public ProcessTradeSignalUseCase createProcessTradeSignal(
            TransactionTemplate txTemplate,
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            StrategyRepositoryPort strategyRepository,
            GlobalBalanceRepositoryPort globalBalanceRepository,
            PortfolioRepositoryPort portfolioRepository,
            OrderDispatchPort orderDispatch) {

        return new ProcessTradeSignalUseCase(
                strategyRunnerRepository,
                strategyRepository,
                globalBalanceRepository,
                portfolioRepository,
                orderDispatch) {

            @Override
            public void transactionalPersistBuyAndReserve(Transaction transaction, CapitalRequest capitalRequest,
                                                          StrategyRunner runner) {
                txTemplate.executeWithoutResult(status -> persistBuyAndReserve(transaction, capitalRequest, runner));
            }

            @Override
            public void transactionalPersistSellAndLockPosition(Transaction transaction, Position targetPosition) {
                txTemplate.executeWithoutResult(status -> persistSellAndLockPosition(transaction, targetPosition));
            }
        };
    }

}
