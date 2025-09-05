package com.marmitt.core.ports.inbound;

import com.marmitt.core.domain.RiskAssessment;
import com.marmitt.core.domain.RiskParameters;
import com.marmitt.core.dto.trade.TradingRequest;

import java.math.BigDecimal;

public interface RiskManagementPort {
    
    RiskAssessment assessTradingRisk(TradingRequest request);
    
    boolean isTradeAllowed(TradingRequest request);
    
    BigDecimal calculateMaxPositionSize(String symbol, BigDecimal accountBalance);
    
    void updateRiskParameters(RiskParameters parameters);
    
    RiskParameters getCurrentRiskParameters();
    
    void enableRiskManagement();
    
    void disableRiskManagement();
    
    boolean isRiskManagementEnabled();
}