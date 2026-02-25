package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.capital.BuyExecutionContext;
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
 * Fabrica responsavel por construir todos os objetos que representam a "intencao de trade"
 * ao longo do pipeline: do input da estrategia ate o comando de dispatch para a exchange.
 *
 * <p>Centraliza a construcao em um unico lugar para manter o orquestrador e os handlers
 * livres de logica de montagem. Cada metodo produz um objeto especifico para uma etapa:
 * <ol>
 *   <li>{@link #buildStrategyInput} — adaptacao do tick para o contrato da estrategia.</li>
 *   <li>{@link #buildTransaction} — materializacao da intencao como registro local PENDING,
 *       com clientOrderId unico gerado para roteamento de callbacks da exchange.</li>
 *   <li>{@link #buildCapitalRequest} — pedido de reserva de capital para ordens BUY.</li>
 *   <li>{@link #buildBuyExecutionContext} — agrupamento de todos os artefatos do ramo BUY
 *       em um unico objeto para facilitar passagem entre camadas.</li>
 *   <li>{@link #buildDispatchCommand} — comando de envio para o adapter de exchange.</li>
 * </ol>
 */
class TradeIntentFactory {

    /**
     * Adapta o tick de market data para o contrato {@link StrategyInputDto} esperado
     * pela estrategia. Permite que a estrategia opere sem dependencia direta do DTO
     * de transporte WebSocket.
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
     * Cria a {@link Transaction} local representando a intencao de trade com status PENDING.
     *
     * <p>O {@code clientOrderId} gerado aqui e a chave de roteamento que permite ao sistema
     * reconciliar os callbacks assincronos da exchange com a transacao correta no banco.
     * O total e calculado como {@code quantity × currentPrice} e usado como valor de
     * reserva de capital — o valor executado real sera atualizado ao receber os fills.
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
     * Constroi o pedido de reserva de capital para uma ordem BUY.
     * O valor reservado e o total estimado da transacao; eventuais diferenca para o
     * valor executado real serao devolvidas ao saldo pelo fluxo de conciliacao.
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
     * Agrupa transacao, pedido de capital e runner em um {@link BuyExecutionContext}
     * para passagem entre o orquestrador e os handlers sem multiplos parametros avulsos.
     * O {@code precomputedExposure} evita nova consulta ao banco quando o snapshot ja
     * foi carregado pela policy SINGLE.
     */
    public BuyExecutionContext buildBuyExecutionContext(StrategyRunner runner,
                                                        Transaction transaction,
                                                        BigDecimal precomputedExposure) {
        return new BuyExecutionContext(
                transaction,
                buildCapitalRequest(runner, transaction),
                runner,
                precomputedExposure
        );
    }

    /**
     * Constroi o comando de envio para o adapter de exchange.
     * Contem apenas os dados necessarios para o dispatch: nao expoe internos do dominio
     * ao adapter, mantendo o desacoplamento entre nucleo e infraestrutura.
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
