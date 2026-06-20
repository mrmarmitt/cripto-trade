package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.web.GlobalExceptionHandler;
import com.marmitt.core.dto.reconciliation.ReconciliationReportDto;
import com.marmitt.core.dto.reconciliation.ReconciliationRequest;
import com.marmitt.core.dto.reconciliation.ReconciliationSummaryDto;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.inbound.reconciliation.ReconcileTradesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReconciliationControllerTest {

    private final ReconcileTradesPort reconcileTradesPort = mock(ReconcileTradesPort.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReconciliationController(reconcileTradesPort))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void reconcile_returnsOkWithSummaryWhenRequestIsValid() throws Exception {
        ReconciliationReportDto report = new ReconciliationReportDto(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                new ReconciliationSummaryDto(3, 2, 1, 0, 0),
                List.of());

        when(reconcileTradesPort.reconcile(any(ReconciliationRequest.class))).thenReturn(report);

        mockMvc.perform(get("/api/reconciliation")
                        .param("symbol", "BTCUSDT")
                        .param("exchangeId", "BINANCE")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.matched").value(2))
                .andExpect(jsonPath("$.summary.divergent").value(1));
    }

    @Test
    void reconcile_returnsBadRequestWhenFromIsAfterTo() throws Exception {
        mockMvc.perform(get("/api/reconciliation")
                        .param("symbol", "BTCUSDT")
                        .param("exchangeId", "BINANCE")
                        .param("from", "2026-01-02T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconcile_returns400WhenExchangeIdNotSupported() throws Exception {
        when(reconcileTradesPort.reconcile(any(ReconciliationRequest.class)))
                .thenThrow(new IllegalArgumentException("Exchange 'COINBASE' is not supported"));

        mockMvc.perform(get("/api/reconciliation")
                        .param("symbol", "BTCUSDT")
                        .param("exchangeId", "COINBASE")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reconcile_returns502WhenExchangeQueryFails() throws Exception {
        when(reconcileTradesPort.reconcile(any(ReconciliationRequest.class)))
                .thenThrow(new ExchangeQueryException("BINANCE",
                        ExchangeQueryException.ErrorType.TEMPORARY, "Binance timeout"));

        mockMvc.perform(get("/api/reconciliation")
                        .param("symbol", "BTCUSDT")
                        .param("exchangeId", "BINANCE")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.path").value("/api/reconciliation"));
    }

    @Test
    void reconcile_passesAllParamsToUseCase() throws Exception {
        ReconciliationReportDto report = new ReconciliationReportDto(
                "BTCUSDT",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"),
                new ReconciliationSummaryDto(0, 0, 0, 0, 0),
                List.of());
        when(reconcileTradesPort.reconcile(any(ReconciliationRequest.class))).thenReturn(report);

        mockMvc.perform(get("/api/reconciliation")
                        .param("symbol", "BTCUSDT")
                        .param("exchangeId", "BINANCE")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z")
                        .param("includeMatched", "true"))
                .andExpect(status().isOk());

        verify(reconcileTradesPort).reconcile(
                new ReconciliationRequest(
                        "BTCUSDT",
                        "BINANCE",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z"),
                        true));
    }
}
