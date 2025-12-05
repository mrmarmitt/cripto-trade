package com.marmitt.core.application.listener.portfolio;

import com.marmitt.core.dto.websocket.data.MarketDataDto;
import com.marmitt.core.domain.strategy.StrategyInput;
import com.marmitt.core.domain.portfolio.Portfolio;
import com.marmitt.core.ports.outbound.listener.PriceUpdateListener;
import com.marmitt.core.ports.outbound.repository.PortfolioRepositoryPort;
import com.marmitt.core.ports.outbound.repository.StrategyRepositoryPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class PortfolioStrategyListener implements PriceUpdateListener {
    
    private final PortfolioOrchestrator portfolioOrchestrator;
    private final PortfolioRepositoryPort portfolioRepository;
    
    public PortfolioStrategyListener(PortfolioRepositoryPort portfolioRepository,
                                     StrategyRepositoryPort strategyRepository) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioOrchestrator = new PortfolioOrchestrator(
                strategyRepository,
                portfolioRepository,
                null,
                null);
    }
    
    @Override
    public void onPriceUpdate(MarketDataDto marketData) {
        String symbol = marketData.symbol().value();
        List<Portfolio> portfolios = portfolioRepository.findBySymbol(symbol);
        
        if (portfolios.isEmpty()) {
            log.trace("No active portfolios for currency: {}", symbol);
            return;
        }

        StrategyInput strategyInput = StrategyInput.builder()
                .symbol(marketData.symbol())
                .currentPrice(marketData.price())
                .volume(marketData.volume())
                .timestamp(marketData.timestamp())
                .build();

        for (Portfolio portfolio : portfolios) {
            try {
                portfolioOrchestrator.processStrategyExecution(portfolio, strategyInput);
                
                log.debug("Strategy execution initiated for portfolio: {} on currency: {}",
                        portfolio.getId(), symbol);
                        
            } catch (Exception e) {
                log.error("Error initiating strategy execution for portfolio: {} on currency: {} - Error: {}",
                        portfolio.getId(), symbol, e.getMessage(), e);
                
                // Registrar erro mas continuar processamento de outros portfolios
                // Implementar circuit breaker se necessário
            }
        }
        
        // ALTERNATIVA: EXECUÇÃO EM LOTE QUANDO MÚLTIPLOS PORTFOLIOS
        // Descomente para usar execução otimizada em lote
        /*
        if (portfolios.size() > 1) {
            portfolioOrchestrator.processBatchExecution(portfolios, strategyInput);
        } else if (portfolios.size() == 1) {
            portfolioOrchestrator.processStrategyExecution(portfolios.get(0), strategyInput);
        }
        */
        
        // 4. MÉTRICAS E OBSERVABILIDADE
        // - Registrar métricas de execução
        // - Tracking de latência de processamento
        // - Contadores de sucesso/erro por portfolio
        
        // metricsService.recordStrategyExecution(currency, portfolios.size());
    }
    
    /**
     * Verifica se o listener deve processar este currency
     * baseado nos portfolios ativos.
     */
    private boolean shouldProcessSymbol(String symbol) {
        // Lógica para determinar se há interesse neste currency
        // Pode incluir cache para otimizar performance
        return true;
    }
    
    /**
     * Converte MarketData para StrategyInput com dados contextuais
     */
    // private StrategyInput convertToStrategyInput(MarketData marketData) {
    //     // Lógica de conversão
    //     // Pode incluir dados históricos se necessário
    // }
    
    /**
     * Processa resultado da execução de estratégia
     */
    // private void handleStrategyResult(Portfolio portfolio, StrategyOutput output) {
    //     // Processar resultado e decidir próximas ações
    //     // Pode incluir validações de risco
    // }
}