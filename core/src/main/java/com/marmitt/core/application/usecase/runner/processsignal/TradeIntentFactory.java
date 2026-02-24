package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.CapitalRequest;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.enums.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fabrica de objetos de intencao do fluxo de sinal:
 * input para estrategia, transaction, capital request e dispatch command.
 */
class TradeIntentFactory {

    /**
     * Converte market data em input padronizado para estrategia.
     */
    public StrategyInputDto buildStrategyInput(MarketDataDto marketData) {
        return StrategyInputDto.builder()
                .symbol(marketData.symbol())
                .currentPrice(marketData.price())
                .bidPrice(marketData.bidPrice())
                .askPrice(marketData.askPrice())
                .volume(marketData.volume())
                .high24h(marketData.high24h())
                .low24h(marketData.low24h())
                .timestamp(marketData.timestamp())
                .build();
    }

    /**
     * Materializa a transacao local (status inicial PENDING).
     */
    public Transaction buildTransaction(StrategyRunner runner, StrategyOutputDto signal, BigDecimal currentPrice) {
        TransactionType type = signal.decision() == TradingAction.SHOULD_BUY
                ? TransactionType.BUY : TransactionType.SELL;
        String clientOrderId = ClientOrderId.generate(runner.getShortCode(), type);
        BigDecimal total = signal.quantity().multiply(currentPrice).setScale(8, RoundingMode.HALF_UP);

        return new Transaction(
                runner.getId(),
                clientOrderId,
                type,
                runner.getSymbol(),
                signal.quantity(),
                currentPrice,
                total,
                signal.confidence(),
                signal.reasoning(),
                signal.targetLotId()
        );
    }

    /**
     * Construtor do pedido de reserva de capital para BUY.
     */
    public CapitalRequest buildCapitalRequest(StrategyRunner runner, Transaction transaction) {
        return new CapitalRequest(
                transaction.getId(),
                runner.getId(),
                runner.getShortCode(),
                runner.getSymbol(),
                transaction.getTotal(),
                transaction.getType()
        );
    }

    /**
     * Construtor do comando de envio de ordem para o adapter de exchange.
     */
    public OrderDispatchCommand buildDispatchCommand(StrategyRunner runner, Transaction transaction) {
        return new OrderDispatchCommand(
                transaction.getClientOrderId(),
                runner.getId(),
                runner.getSymbol(),
                runner.getExchangeId(),
                transaction.getType(),
                transaction.getQuantity(),
                transaction.getPrice()
        );
    }
}
