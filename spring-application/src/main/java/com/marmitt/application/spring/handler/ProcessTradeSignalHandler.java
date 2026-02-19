package com.marmitt.application.spring.handler;

import com.marmitt.core.application.usecase.runner.ProcessTradeSignalUseCase;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.inbound.runner.ProcessTradeSignalPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Handler Spring do fluxo ProcessTradeSignal (F2-01).
 * <p>
 * Responsabilidade única: implementar {@link ProcessTradeSignalPort} e delegar ao
 * {@link ProcessTradeSignalUseCase}. Toda a lógica de domínio e orquestração
 * do protocolo Persist-First está no UseCase.
 *
 * @see ProcessTradeSignalUseCase
 * @see ProcessTradeSignalTransactionAdapter
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
@Component
public class ProcessTradeSignalHandler implements ProcessTradeSignalPort {

    private final ProcessTradeSignalUseCase useCase;

    public ProcessTradeSignalHandler(ProcessTradeSignalUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void handle(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        useCase.handle(runner, signal, currentPrice);
    }
}
