package com.marmitt.application.spring.config.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.adapter.binance.OkHttpClientAdapter;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.binance.boot.BinanceBootReadinessChecker;
import com.marmitt.binance.rest.BinanceRestRequestBuilder;
import com.marmitt.core.dto.exchange.boot.ExchangeBootReadiness;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BinanceBootReadinessIntegrationTest {

    private MockWebServer server;
    private BinanceBootReadinessChecker checker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        String baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
        var credentials = new BinanceCredentials("test-api-key", "test-api-secret");
        var signer = new BinanceRequestSigner(credentials);
        var requestBuilder = new BinanceRestRequestBuilder(baseUrl, signer);
        var httpClient = new OkHttpClientAdapter(new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(10))
                .build());

        checker = new BinanceBootReadinessChecker(baseUrl, requestBuilder, httpClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldReturnReadyWhenConnectivityAndApiKeyAreValid() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"canTrade\":true,\"balances\":[]}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isTrue();
        assertThat(result.code()).isEqualTo("READY");
        assertThat(result.exchangeName()).isEqualTo("BINANCE");
    }

    @Test
    void shouldReturnInsufficientPermissionsWhenCanTradeIsFalse() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"canTrade\":false,\"balances\":[]}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("INSUFFICIENT_PERMISSIONS");
    }

    @Test
    void shouldReturnConnectivityFailureWhenPingTimesOut() throws IOException {
        server.shutdown();

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("CONNECTIVITY_FAILURE");
    }

    @Test
    void shouldReturnConnectivityFailureWhenPingReturnsNon200() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("CONNECTIVITY_FAILURE");
    }

    @Test
    void shouldReturnInvalidApiKeyWhenAccountReturns401() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"code\":-2014,\"msg\":\"API-key format invalid.\"}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("INVALID_API_KEY");
    }

    @Test
    void shouldReturnInsufficientPermissionsWhenAccountReturns403() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("INSUFFICIENT_PERMISSIONS");
    }

    @Test
    void shouldReturnUnknownErrorWhenAccountReturnsUnexpectedStatusCode() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"msg\":\"Internal error\"}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("UNKNOWN_ERROR");
    }

    @Test
    void shouldReturnUnknownErrorWhenAccountResponseLacksCanTradeField() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"balances\":[]}"));

        ExchangeBootReadiness result = checker.check();

        assertThat(result.ready()).isFalse();
        assertThat(result.code()).isEqualTo("UNKNOWN_ERROR");
    }
}
