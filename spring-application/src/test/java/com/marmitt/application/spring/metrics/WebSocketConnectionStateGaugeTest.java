package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.StreamChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSocketConnectionStateGaugeTest {

    private SimpleMeterRegistry registry;
    private WebSocketConnectionStateGauge gauge;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        gauge = new WebSocketConnectionStateGauge(registry);
    }

    @Test
    void reportsOneWhenConnectedAndZeroWhenDisconnected() {
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA);
        assertEquals(1.0, gaugeValue("BINANCE", "USER_DATA"));

        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA);
        assertEquals(0.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    @Test
    void keepsSeparateSeriesPerExchangeAndChannel() {
        gauge.markConnected("BINANCE", StreamChannel.MARKET);
        gauge.markDisconnected("MOCK", StreamChannel.USER_DATA);

        assertEquals(1.0, gaugeValue("BINANCE", "MARKET"));
        assertEquals(0.0, gaugeValue("MOCK", "USER_DATA"));
    }

    @Test
    void reusesSameGaugeAcrossTransitions() {
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA);
        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA);
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA);

        assertEquals(1, registry.find(WebSocketConnectionStateGauge.METRIC)
                .tags("exchange", "BINANCE", "channel", "USER_DATA")
                .gauges().size());
        assertEquals(1.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    private double gaugeValue(String exchange, String channel) {
        return registry.get(WebSocketConnectionStateGauge.METRIC)
                .tags("exchange", exchange, "channel", channel)
                .gauge()
                .value();
    }
}
