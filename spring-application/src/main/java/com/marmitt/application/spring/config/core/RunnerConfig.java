package com.marmitt.application.spring.config.core;

import com.marmitt.application.spring.bootstrap.PortfolioReservationTtlProperties;
import com.marmitt.application.spring.bootstrap.RunnerBootPhase3Properties;
import com.marmitt.core.application.usecase.runner.CreateRunnerUseCase;
import com.marmitt.core.application.usecase.runner.HaltRunnerUseCase;
import com.marmitt.core.application.usecase.runner.RecoverStaleTransactionsUseCase;
import com.marmitt.core.application.usecase.runner.RecoverTransactionStatusUseCase;
import com.marmitt.core.application.usecase.runner.RunnerBootRecoveryUseCase;
import com.marmitt.core.application.usecase.runner.OrderConciliationUseCase;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdateExecutor;
import com.marmitt.core.application.usecase.runner.orderconciliation.ConciliationOrderUpdate;
import com.marmitt.core.application.usecase.runner.QueryRunnerUseCase;
import com.marmitt.core.application.usecase.runner.processsignal.ProcessTradeSignalUseCase;
import com.marmitt.core.domain.runner.Position;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.CreateRunnerPort;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.inbound.runner.RecoverStaleTransactionsPort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import com.marmitt.core.ports.outbound.events.EventPublisherPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.GlobalBalanceRepositoryPort;
import com.marmitt.core.ports.outbound.repository.DeadLetterEntryRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executor;


/**
 * Configuração Spring dos serviços do domínio Runner e Portfolio (capital).
 * <p>
 * Declara beans para:
 * <ul>
 *   <li>Use cases do ciclo de vida de ordens ({@code NewProcessTradeSignal}, {@code OrderConciliation})</li>
 *   <li>Listeners de mercado e ordem do Runner ({@code PortfolioStrategyRunner*})</li>
 * </ul>
 * <p>
 * A fronteira transacional da conciliação é centralizada em
 * {@link ConciliationOrderUpdateExecutor}, reutilizada no fluxo normal e no boot recovery.
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
    public ConciliationOrderUpdate reconcileOrderUpdate(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            EventPublisherPort eventPublisher
    ) {
        return new ConciliationOrderUpdate(strategyRunnerRepository, eventPublisher);
    }

    @Bean
    public ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor(
            TransactionTemplate txTemplate,
            ConciliationOrderUpdate conciliationOrderUpdate
    ) {
        return new ConciliationOrderUpdateExecutor() {
            @Override
            public void execute(OrderDataDto orderData) {
                conciliationOrderUpdate.execute(
                        orderData,
                        this::submitTransaction,
                        this::processFill,
                        this::releaseMargin
                );
            }

            @Override
            public void submitTransaction(Transaction transaction) {
                txTemplate.executeWithoutResult(status -> conciliationOrderUpdate.submitTransaction(transaction));
            }

            @Override
            public void processFill(Transaction transaction,
                                    OrderDataDto orderData,
                                    boolean isFinal) {
                txTemplate.executeWithoutResult(status ->
                        conciliationOrderUpdate.processFill(transaction, orderData, isFinal));
            }

            @Override
            public void releaseMargin(Transaction transaction) {
                txTemplate.executeWithoutResult(status -> conciliationOrderUpdate.releaseMargin(transaction));
            }
        };
    }

    @Bean
    public OrderConciliationUseCase createOrderConciliation(
            ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor
    ) {
        return new OrderConciliationUseCase(conciliationOrderUpdateExecutor);
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

    @Bean
    public HaltRunnerPort haltRunner(
            StrategyRunnerRepositoryPort strategyRunnerRepository
    ) {
        return new HaltRunnerUseCase(strategyRunnerRepository);
    }

    @Bean
    public RecoverTransactionStatusUseCase recoverTransactionStatusUseCase(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor
    ) {
        return new RecoverTransactionStatusUseCase(
                strategyRunnerRepository,
                exchangeAdapterRepository,
                deadLetterEntryRepository,
                conciliationOrderUpdateExecutor
        );
    }

    @Bean
    public RecoverStaleTransactionsPort recoverStaleTransactionsPort(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            RecoverTransactionStatusUseCase recoverTransactionStatusUseCase
    ) {
        return new RecoverStaleTransactionsUseCase(
                strategyRunnerRepository,
                recoverTransactionStatusUseCase
        );
    }

    @Bean
    public RunnerBootRecoveryUseCase runnerBootRecoveryUseCase(
            StrategyRunnerRepositoryPort strategyRunnerRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository,
            DeadLetterEntryRepositoryPort deadLetterEntryRepository,
            ConciliationOrderUpdateExecutor conciliationOrderUpdateExecutor,
            RecoverTransactionStatusUseCase recoverTransactionStatusUseCase,
            PortfolioReservationTtlProperties reservationTtlProperties,
            RunnerBootPhase3Properties phase3Properties,
            @Qualifier("bootRecoveryQueryExecutor") Executor bootRecoveryQueryExecutor
    ) {
        return new RunnerBootRecoveryUseCase(
                strategyRunnerRepository,
                exchangeAdapterRepository,
                deadLetterEntryRepository,
                conciliationOrderUpdateExecutor,
                recoverTransactionStatusUseCase,
                reservationTtlProperties.getTtlMs(),
                phase3Properties.getExchangeQueryTimeoutMs(),
                phase3Properties.getExchangeQueryMaxAttempts(),
                phase3Properties.getExchangeQueryInitialBackoffMs(),
                phase3Properties.getExchangeQueryBackoffMultiplier(),
                phase3Properties.getExchangeQueryMaxBackoffMs(),
                bootRecoveryQueryExecutor
        );
    }

}
