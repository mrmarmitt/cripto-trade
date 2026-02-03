package com.marmitt.core.application.listener.portfolio;

import com.marmitt.core.domain.portfolio.Asset;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.domain.portfolio.Transaction;
import com.marmitt.core.domain.portfolio.TradingDecision;
import com.marmitt.core.dto.strategy.PortfolioContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionStatus;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
class StrategyExecution {

    private final StrategyRepositoryPort strategyRepository;
    private final PortfolioRepositoryPort portfolioRepository;
    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    // private final PerformanceCalculatorPort performanceCalculator;

    public StrategyExecution(
            StrategyRepositoryPort strategyRepository,
            PortfolioRepositoryPort portfolioRepository,
            ExchangeAdapterRepositoryPort exchangeAdapterRepository
    ) {
        this.strategyRepository = strategyRepository;
        this.portfolioRepository = portfolioRepository;
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    public void executeStrategy(Portfolio portfolio, StrategyInputDto strategyInput) {

        log.debug("Executing strategy for portfolio: {} with input: {}",
                portfolio.getId(), strategyInput);

        try {
            UUID strategyId = portfolio.getStrategyId();
            Optional<TradingStrategy> strategyOptional = strategyRepository.findById(strategyId);

            if (strategyOptional.isEmpty()) {
                log.error("Strategy not found for portfolio: {} - strategyId: {}",
                        portfolio.getId(), strategyId);
                return;
            }

            TradingStrategy strategy = strategyOptional.get();
            
            // Criar contexto do portfolio para a strategy
            PortfolioContextDto portfolioContext = portfolio.createContext();
            
            // Strategy executa com contexto completo e retorna quantity absoluta
            StrategyOutputDto output = strategy.executeStrategy(strategyInput, portfolioContext);

            if (output == null) {
                log.warn("Strategy returned null output for portfolio: {}", portfolio.getId());
                return;
            }

            log.debug("Strategy executed - Portfolio: {}, Action: {}, Quantity: {}",
                    portfolio.getId(), output.decision(), output.quantity());

            processStrategyOutput(portfolio, output, strategyInput);

            updatePerformanceMetrics(portfolio, output);

            log.debug("Strategy execution completed for portfolio: {}", portfolio.getId());

        } catch (Exception e) {
            log.error("Error executing strategy for portfolio: {} - Error: {}",
                    portfolio.getId(), e.getMessage(), e);

            handleExecutionError(portfolio, strategyInput, e);
        }
    }

    /**
     * Processa resultado da estratégia e decide próximas ações
     */
    private void processStrategyOutput(Portfolio portfolio, StrategyOutputDto output, StrategyInputDto input) {

        log.debug("Processing strategy output - Portfolio: {}, Action: {}, Quantity: {}",
                portfolio.getId(), output.decision(), output.quantity());

        try {
            // 1. Converter StrategyOutput para TradingDecision
            // Strategy já calculou quantity considerando portfolio context
            TradingDecision decision = TradingDecision.fromStrategy(input, output);
            
            log.debug("Trading decision made - Portfolio: {}, Decision: {}, Reasoning: {}", 
                    portfolio.getId(), decision.decision(), decision.reasoning());

            // 2. Executar ação baseada na decisão
            if (decision.shouldExecute()) {
                executeOrder(portfolio, decision);
            } else {
                log.debug("Decision to HOLD - Portfolio: {}, Reason: {}", 
                        portfolio.getId(), decision.reasoning());
            }

            // 3. Atualizar timestamp da última execução
            portfolio.updateLastExecutionTime();

        } catch (Exception e) {
            log.error("Error processing strategy output - Portfolio: {}, Error: {}", 
                    portfolio.getId(), e.getMessage(), e);
        }

        try {
            portfolioRepository.saveBalance(portfolio.getId(), portfolio.getBalance(), portfolio.getLastExecutionTime());
            log.debug("Portfolio {} balance saved after strategy execution", portfolio.getId());
        } catch (Exception e) {
            log.error("Failed to save portfolio {} balance after strategy execution: {}",
                    portfolio.getId(), e.getMessage(), e);
        }
    }

    /**
     * Executa ordem baseada na decisão do Portfolio
     */
    private void executeOrder(Portfolio portfolio, TradingDecision decision) {
        
        log.debug("Starting order execution - Portfolio: {}, Action: {}, Quantity: {}, Price: {}", 
                portfolio.getId(), decision.decision(), decision.quantity(), decision.price().amount());

        try {
            // 1. Buscar adapter da exchange configurada para execução de ordens
            String targetExchange = portfolio.getOrderExecutionExchange();

            log.debug("Portfolio {} configured to execute orders on exchange: {}",
                    portfolio.getId(), targetExchange);

            ExchangeAdapterPort exchangeAdapter = exchangeAdapterRepository
                .findByName(targetExchange)
                .orElseThrow(() -> new IllegalStateException(
                    String.format("Exchange adapter '%s' not found for portfolio '%s' (%s)",
                        targetExchange, portfolio.getName(), portfolio.getId())
                ));

            SenderMessageProcessorPort senderProcessor = exchangeAdapter.getSenderMessageProcessor();
            
            // 2. Gerar ID único para correlação
            String clientOrderId = generateClientOrderId(portfolio.getId(), decision);
            
            // 3. Converter TradingDecision para SendOrderRequest com correlationId
            SendOrderRequest sendOrderRequest = createSendOrderRequest(decision, exchangeAdapter.getExchangeName(), clientOrderId);
            
            log.debug("Created send order request for portfolio: {}, Exchange: {}, Symbol: {}, ClientOrderId: {}",
                    portfolio.getId(), exchangeAdapter.getExchangeName(),
                    sendOrderRequest.getSymbol(), clientOrderId);

            // 4. Criar transaction PENDING antes do envio
            TransactionType transactionType = decision.decision() == TradingAction.SHOULD_BUY ?
                TransactionType.BUY : TransactionType.SELL;

            Asset estimatedFee = Asset.of(BigDecimal.ZERO, decision.price().currency()); // Estimativa (será atualizado depois)

            Transaction pendingTransaction = Transaction.builder()
                    .id(UUID.randomUUID())
                    .clientOrderId(clientOrderId)
                    .status(TransactionStatus.PENDING)
                    .type(transactionType)
                    .symbol(decision.symbol())
                    .quantity(Asset.of(decision.quantity(), portfolio.getSymbol().getBaseAsset()))
                    .executedQuantity(null) // Será preenchido quando executar
                    .price(decision.price())
                    .executedPrice(null) // Será preenchido quando executar
                    .total(decision.estimatedTotal())
                    .fee(estimatedFee)
                    .requestedAt(Instant.now())
                    .executedAt(null) // Será preenchido quando executar
                    .rejectReason(null)
                    .build();

            portfolio.addPendingTransaction(pendingTransaction);
            portfolioRepository.saveTransaction(portfolio.getId(), pendingTransaction);

            log.debug("Pending transaction registered - ClientOrderId: {}, Type: {}, Quantity: {}",
                    clientOrderId, transactionType, decision.quantity());

            // 5. Processar mensagem através do sender processor  
            String orderMessage = senderProcessor.execute(sendOrderRequest);
            
            log.debug("Order message processed - Portfolio: {}, Exchange: {}, ClientOrderId: {}, Message length: {}", 
                    portfolio.getId(), exchangeAdapter.getExchangeName(), clientOrderId, orderMessage.length());
            
            // 6. Enviar mensagem via WebSocket
            exchangeAdapter.getWebSocketPort().sendMessage(orderMessage);

            // 7. Atualizar status para SUBMITTED após envio bem-sucedido
            portfolio.updateTransactionStatus(clientOrderId, TransactionStatus.SUBMITTED);
            Transaction submittedTransaction = portfolio.findTransactionByClientOrderId(clientOrderId)
                    .orElseThrow(() -> new IllegalStateException("Transaction not found after status update: " + clientOrderId));
            portfolioRepository.saveTransaction(portfolio.getId(), submittedTransaction);

            log.info("Order sent via WebSocket - Portfolio: {}, Exchange: {}, ClientOrderId: {}, Action: {}, " +
                    "Symbol: {}, Quantity: {}, Price: {} - Awaiting async response", 
                    portfolio.getId(), 
                    exchangeAdapter.getExchangeName(),
                    clientOrderId,
                    decision.decision(), 
                    sendOrderRequest.getSymbol(),
                    sendOrderRequest.getQuantity(),
                    sendOrderRequest.getPrice());
                    
            // A resposta virá assincronamente via OrderUpdateListener com clientOrderId para correlação

        } catch (Exception e) {
            log.error("Error sending order - Portfolio: {}, Action: {}, Error: {}", 
                    portfolio.getId(), decision.decision(), e.getMessage(), e);
            
            handleOrderSendingException(portfolio, decision, e);
        }
    }
    
    /**
     * Gera ID único para correlação de orders (client-side)
     */
    private String generateClientOrderId(UUID portfolioId, TradingDecision decision) {
        // Formato: PORTFOLIO_PREFIX + TIMESTAMP + HASH
        String prefix = "P" + portfolioId.toString().substring(0, 8).toUpperCase();
        long timestamp = System.currentTimeMillis();
        String actionPrefix = decision.decision() == TradingAction.SHOULD_BUY ? "B" : "S";
        
        return String.format("%s_%s_%d", prefix, actionPrefix, timestamp);
    }
    
    /**
     * Converte TradingDecision para SendOrderRequest com correlationId
     */
    private SendOrderRequest createSendOrderRequest(TradingDecision decision, String exchangeName, String clientOrderId) {
        // Mapear TradingAction para OrderSide
        OrderSide orderSide = switch (decision.decision()) {
            case SHOULD_BUY -> OrderSide.BUY;
            case SHOULD_SELL -> OrderSide.SELL;
            default -> throw new IllegalArgumentException("Invalid trading action for order: " + decision.decision());
        };
        
        // Para simplicidade, usar MARKET order por enquanto
        // TODO: Permitir configuração do tipo de ordem baseado na estratégia
        OrderType orderType = OrderType.MARKET;
        
        // Converter Symbol para string
        String symbol = decision.symbol().value();
        
        return new SendOrderRequest(
            exchangeName,
            symbol,
            decision.quantity(),
            decision.price().amount(),
            orderType,
            orderSide,
            clientOrderId  // ID para correlação
        );
    }
    
    /**
     * Trata exceções durante envio de ordem
     */
    private void handleOrderSendingException(Portfolio portfolio, TradingDecision decision, Exception exception) {
        log.error("Handling order sending exception - Portfolio: {}, Exception: {}", 
                 portfolio.getId(), exception.getMessage());
        
        // TODO: Implementar estratégias de recuperação
        // 1. Retry para falhas temporárias de rede
        // 2. Circuit breaker para falhas recorrentes  
        // 3. Alertas para falhas críticas
        // 4. Fallback para exchange alternativa se disponível
    }

    private void updatePerformanceMetrics(Portfolio portfolio, StrategyOutputDto output) {

        log.debug("Updating performance metrics for portfolio: {} after action: {}",
                portfolio.getId(), output.decision());

        // Por enquanto, apenas registrar que as métricas foram processadas
        // Implementação completa será adicionada quando PerformanceCalculatorPort estiver disponível

        // TODO: Implementar cálculo de métricas quando PerformanceCalculatorPort for adicionado
        // 1. CALCULAR P&L DA EXECUÇÃO
        // - P&L realized das vendas
        // - P&L unrealized das posições atuais
        // - Fees e custos de transação

        // 2. ATUALIZAR ESTATÍSTICAS DO PORTFOLIO
        // - Win rate da estratégia
        // - Sharpe ratio
        // - Maximum drawdown
        // - Total return

        // 3. REGISTRAR HISTÓRICO
        // - Snapshot do estado do portfolio
        // - Resultado da execução
        // - Timestamp e contexto de mercado

        log.trace("Performance metrics update completed for portfolio: {}", portfolio.getId());
    }

    /**
     * Trata erros durante execução da estratégia
     */
    private void handleExecutionError(Portfolio portfolio, StrategyInputDto input, Exception error) {

        // 1. CLASSIFICAR TIPO DE ERRO
        // - Erro de conexão com exchange
        // - Erro de validação de dados
        // - Erro de lógica da estratégia
        // - Erro de insuficiência de capital

        // 2. IMPLEMENTAR RETRY/FALLBACK
        // - Retry para erros temporários
        // - Circuit breaker para falhas recorrentes
        // - Modo degradado se necessário

        // 3. NOTIFICAR E REGISTRAR
        // - Alertas para erros críticos
        // - Logging estruturado
        // - Métricas de erro para monitoramento
    }
}