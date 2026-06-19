package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.web.GlobalExceptionHandler;
import com.marmitt.core.dto.exchange.BalanceDto;
import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.inbound.exchange.QueryExchangeBalancePort;
import com.marmitt.core.ports.inbound.exchange.QueryTradeHistoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExchangeQueryControllerTest {

    private static final String EXCHANGE = "BINANCE";
    private static final Instant AS_OF = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");

    private final QueryExchangeBalancePort queryBalance = mock(QueryExchangeBalancePort.class);
    private final QueryTradeHistoryPort queryTrades = mock(QueryTradeHistoryPort.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ExchangeQueryController(queryBalance, queryTrades))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getBalances_returnsFullSnapshot() throws Exception {
        when(queryBalance.queryBalances(EXCHANGE)).thenReturn(snapshot(
                BalanceDto.of("USDT", new BigDecimal("100"), new BigDecimal("25")),
                BalanceDto.of("BTC", new BigDecimal("0.20"), BigDecimal.ZERO)));

        mockMvc.perform(get("/api/exchanges/{exchange}/balances", EXCHANGE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exchangeName").value(EXCHANGE))
                .andExpect(jsonPath("$.balances.length()").value(2));
    }

    @Test
    void getBalances_filtersByAssetAtTheEdge() throws Exception {
        when(queryBalance.queryBalances(EXCHANGE)).thenReturn(snapshot(
                BalanceDto.of("USDT", new BigDecimal("100"), new BigDecimal("25")),
                BalanceDto.of("BTC", new BigDecimal("0.20"), BigDecimal.ZERO)));

        mockMvc.perform(get("/api/exchanges/{exchange}/balances", EXCHANGE).param("asset", "usdt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(1))
                .andExpect(jsonPath("$.balances[0].asset").value("USDT"))
                .andExpect(jsonPath("$.balances[0].total").value(125));
    }

    @Test
    void getBalances_returnsEmptyWhenAssetAbsent() throws Exception {
        when(queryBalance.queryBalances(EXCHANGE)).thenReturn(snapshot(
                BalanceDto.of("USDT", new BigDecimal("100"), BigDecimal.ZERO)));

        mockMvc.perform(get("/api/exchanges/{exchange}/balances", EXCHANGE).param("asset", "ETH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances.length()").value(0));
    }

    @Test
    void getBalances_returns400WhenExchangeUnknown() throws Exception {
        when(queryBalance.queryBalances("NOPE"))
                .thenThrow(new IllegalArgumentException("Exchange 'NOPE' is not registered"));

        mockMvc.perform(get("/api/exchanges/{exchange}/balances", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getBalances_returns501WhenCapabilityUnsupported() throws Exception {
        when(queryBalance.queryBalances("COINBASE"))
                .thenThrow(new UnsupportedOperationException("Balance query capability is not available"));

        mockMvc.perform(get("/api/exchanges/{exchange}/balances", "COINBASE"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501));
    }

    @Test
    void getTrades_returnsFills() throws Exception {
        when(queryTrades.queryTrades(EXCHANGE, "BTCUSDT", FROM, TO)).thenReturn(List.of(fill()));

        mockMvc.perform(get("/api/exchanges/{exchange}/trades", EXCHANGE)
                        .param("symbol", "BTCUSDT")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("BTCUSDT"));

        verify(queryTrades).queryTrades(eq(EXCHANGE), eq("BTCUSDT"), eq(FROM), eq(TO));
    }

    @Test
    void getTrades_returns400WhenIntervalInvalid() throws Exception {
        when(queryTrades.queryTrades(EXCHANGE, "BTCUSDT", TO, FROM))
                .thenThrow(new IllegalArgumentException("from must not be after to"));

        mockMvc.perform(get("/api/exchanges/{exchange}/trades", EXCHANGE)
                        .param("symbol", "BTCUSDT")
                        .param("from", TO.toString())
                        .param("to", FROM.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getTrades_returns501WhenCapabilityUnsupported() throws Exception {
        when(queryTrades.queryTrades("COINBASE", "BTCUSDT", FROM, TO))
                .thenThrow(new UnsupportedOperationException("Trade history query capability is not available"));

        mockMvc.perform(get("/api/exchanges/{exchange}/trades", "COINBASE")
                        .param("symbol", "BTCUSDT")
                        .param("from", FROM.toString())
                        .param("to", TO.toString()))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501));
    }

    private static ExchangeBalanceSnapshot snapshot(BalanceDto... balances) {
        return new ExchangeBalanceSnapshot(EXCHANGE, List.of(balances), AS_OF);
    }

    private static TradeExecutionDto fill() {
        return new TradeExecutionDto(
                "trade-1", 100234L, null, "BTCUSDT",
                new BigDecimal("50000"), new BigDecimal("0.01"),
                new BigDecimal("500"), new BigDecimal("0.0001"), "BNB",
                Instant.parse("2026-01-01T10:00:00Z"), true);
    }
}
