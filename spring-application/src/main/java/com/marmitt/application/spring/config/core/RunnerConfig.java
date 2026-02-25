package com.marmitt.application.spring.config.core;

import com.marmitt.core.application.usecase.runner.createrunner.CreateRunnerUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.OrderConciliationUseCase;
import com.marmitt.core.application.usecase.runner.queryrunner.QueryRunnerUseCase;
import com.marmitt.core.application.usecase.runner.processsignal.ProcessTradeSignalUseCase;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;


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
            public void transactionalSubmit(Transaction transaction) {
                txTemplate.executeWithoutResult(status -> submitTransaction(transaction));
            }

            @Override
            public void transactionalReleaseMargin(Transaction transaction) {
                txTemplate.executeWithoutResult(status -> releaseMargin(transaction));
            }

            @Override
            public void transactionalProcessFill(Transaction transaction, OrderDataDto orderData,
                                                 boolean isFinal) {
                txTemplate.executeWithoutResult(status ->
                        processFill(transaction, orderData, isFinal));
            }
        };
    }

    @Bean
    public ProcessTradeSignalPort createProcessTradeSignal(
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
            public void transactionalPersistBuyAndReserve(BuyExecutionContext context) {
                txTemplate.executeWithoutResult(status -> persistBuyAndReserve(context));
            }

            @Override
            public void transactionalPersistSellAndLockPosition(Transaction transaction, Position targetPosition) {
                txTemplate.executeWithoutResult(status -> persistSellAndLockPosition(transaction, targetPosition));
            }
        };
    }

    @Bean
    public CreateRunnerPort createRunner(
            PortfolioRepositoryPort portfolioRepository,
            StrategyRepositoryPort strategyRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        return new CreateRunnerUseCase(
                portfolioRepository,
                strategyRepository,
                exchangeAdapterRepository,
                strategyRunnerRepository
        );
    }

    @Bean
    public QueryRunnerPort queryRunner(
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        return new QueryRunnerUseCase(strategyRunnerRepository);
    }

}
