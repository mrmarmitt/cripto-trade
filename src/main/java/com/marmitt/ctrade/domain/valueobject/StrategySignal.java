package com.marmitt.ctrade.domain.valueobject;

import com.marmitt.ctrade.domain.entity.TradingPair;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StrategySignal {
    private SignalType type;
    private TradingPair pair;
    private BigDecimal quantity;
    private BigDecimal price;
    private String reason;
    private String strategyName;

    public static StrategySignal hold(String strategyName) {
        return new StrategySignal(SignalType.HOLD, null, null, null, "No action required", strategyName);
    }

    public static StrategySignal buy(TradingPair pair, BigDecimal quantity, BigDecimal price, String reason, String strategyName) {
        return new StrategySignal(SignalType.BUY, pair, quantity, price, reason, strategyName);
    }

    public static StrategySignal sell(TradingPair pair, BigDecimal quantity, BigDecimal price, String reason, String strategyName) {
        return new StrategySignal(SignalType.SELL, pair, quantity, price, reason, strategyName);
    }

    public boolean isActionable() {
        return type != SignalType.HOLD;
    }
}