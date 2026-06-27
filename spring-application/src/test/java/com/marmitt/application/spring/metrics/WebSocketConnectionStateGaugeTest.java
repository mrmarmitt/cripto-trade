package com.marmitt.application.spring.metrics;

import com.marmitt.core.enums.StreamChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

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
        UUID conn = UUID.randomUUID();
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, conn);
        assertEquals(1.0, gaugeValue("BINANCE", "USER_DATA"));

        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA, conn);
        assertEquals(0.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    @Test
    void keepsSeparateSeriesPerExchangeAndChannel() {
        gauge.markConnected("BINANCE", StreamChannel.MARKET, UUID.randomUUID());
        gauge.markDisconnected("MOCK", StreamChannel.USER_DATA, UUID.randomUUID());

        assertEquals(1.0, gaugeValue("BINANCE", "MARKET"));
        assertEquals(0.0, gaugeValue("MOCK", "USER_DATA"));
    }

    @Test
    void reusesSameGaugeAcrossTransitions() {
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, UUID.randomUUID());
        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA, null);
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, UUID.randomUUID());

        assertEquals(1, registry.find(WebSocketConnectionStateGauge.METRIC)
                .tags("exchange", "BINANCE", "channel", "USER_DATA")
                .gauges().size());
        assertEquals(1.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    @Test
    void ignoresStaleCloseFromSupersededConnectionAfterReconnect() {
        UUID oldConn = UUID.randomUUID();
        UUID newConn = UUID.randomUUID();

        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, oldConn);
        // Reconnect: nova conexão assume antes do close tardio da antiga chegar.
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, newConn);
        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA, oldConn);

        assertEquals(1.0, gaugeValue("BINANCE", "USER_DATA"),
                "Close tardio da conexão superada não deve zerar o gauge da conexão ativa");

        // O close da conexão atual zera normalmente.
        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA, newConn);
        assertEquals(0.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    @Test
    void clearsOnCriticalFailureWithoutConnectionId() {
        gauge.markConnected("BINANCE", StreamChannel.USER_DATA, UUID.randomUUID());
        // Falha crítica não traz connectionId: deve zerar incondicionalmente.
        gauge.markDisconnected("BINANCE", StreamChannel.USER_DATA, null);
        assertEquals(0.0, gaugeValue("BINANCE", "USER_DATA"));
    }

    private double gaugeValue(String exchange, String channel) {
        return registry.get(WebSocketConnectionStateGauge.METRIC)
                .tags("exchange", exchange, "channel", channel)
                .gauge()
                .value();
    }
}
