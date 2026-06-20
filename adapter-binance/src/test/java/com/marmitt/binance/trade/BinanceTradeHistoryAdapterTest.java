package com.marmitt.binance.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.rest.BinanceRestRequestBuilder;
import com.marmitt.binance.rest.HttpClientPort;
import com.marmitt.binance.rest.RestRequest;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.exceptions.ExchangeQueryException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BinanceTradeHistoryAdapterTest {

    private static final String SYMBOL = "BTCUSDT";
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-01-02T00:00:00Z");
    private static final RestRequest DUMMY_REQ =
            new RestRequest("GET", "https://api.binance.com/api/v3/myTrades?...", Map.of(), null);

    private final BinanceRestRequestBuilder requestBuilder = mock(BinanceRestRequestBuilder.class);
    private final HttpClientPort httpClient = mock(HttpClientPort.class);
    private final ObjectMapper objectMapper = buildObjectMapper();

    private final BinanceTradeHistoryAdapter adapter =
            new BinanceTradeHistoryAdapter(requestBuilder, httpClient, objectMapper);

    @Test
    void fetchTrades_parsesJsonFieldsCorrectly() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        String json = """
                [{"id":"28457","orderId":100234,"clientOrderId":"client-1","symbol":"BTCUSDT",
                  "price":"50000.00","qty":"0.01","quoteQty":"500.00","commission":"0.0001",
                  "commissionAsset":"BNB","time":1699865549590,"isBuyer":true}]
                """;
        when(httpClient.get(any(), any())).thenReturn(new HttpClientPort.HttpResponse(200, json));

        List<TradeExecutionDto> result = adapter.fetchTrades(SYMBOL, FROM, TO);

        assertEquals(1, result.size());
        TradeExecutionDto trade = result.get(0);
        assertEquals("28457", trade.exchangeTradeId());
        assertEquals(100234L, trade.exchangeOrderId());
        assertEquals("client-1", trade.clientOrderId());
        assertEquals("BTCUSDT", trade.symbol());
        assertEquals(0, new BigDecimal("50000.00").compareTo(trade.price()));
        assertEquals(0, new BigDecimal("0.01").compareTo(trade.qty()));
        assertEquals(0, new BigDecimal("500.00").compareTo(trade.quoteQty()));
        assertEquals(0, new BigDecimal("0.0001").compareTo(trade.commission()));
        assertEquals("BNB", trade.commissionAsset());
        assertEquals(Instant.ofEpochMilli(1699865549590L), trade.executedAt());
        assertEquals(true, trade.isBuy());
    }

    @Test
    void fetchTrades_returnsEmptyListWhenJsonArrayIsEmpty() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any())).thenReturn(new HttpClientPort.HttpResponse(200, "[]"));

        List<TradeExecutionDto> result = adapter.fetchTrades(SYMBOL, FROM, TO);

        assertEquals(0, result.size());
    }

    @Test
    void fetchTrades_paginatesWhenFullPageIsReturned() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(requestBuilder.buildMyTradesFromId(anyString(), anyLong())).thenReturn(DUMMY_REQ);

        String firstPage = buildTradeJsonArray(1000, 0L);
        String secondPage = """
                [{"id":"1001","orderId":100234,"clientOrderId":"client-1000","symbol":"BTCUSDT",
                  "price":"50000","qty":"0.01","quoteQty":"500","commission":"0.0001",
                  "commissionAsset":"BNB","time":1699865549590,"isBuyer":true}]
                """;

        when(httpClient.get(any(), any()))
                .thenReturn(new HttpClientPort.HttpResponse(200, firstPage))
                .thenReturn(new HttpClientPort.HttpResponse(200, secondPage));

        List<TradeExecutionDto> result = adapter.fetchTrades(SYMBOL, FROM, TO);

        assertEquals(1001, result.size());
        verify(requestBuilder, times(1)).buildMyTrades(anyString(), anyLong(), anyLong());
        verify(requestBuilder, times(1)).buildMyTradesFromId(anyString(), anyLong());
    }

    @Test
    void fetchTrades_throwsRuntimeExceptionWhenHttpErrorReturned() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any()))
                .thenReturn(new HttpClientPort.HttpResponse(400, "{\"code\":-1100,\"msg\":\"Illegal characters\"}"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adapter.fetchTrades(SYMBOL, FROM, TO));
        assertEquals(true, ex.getMessage().contains("400"));
    }

    @Test
    void fetchTrades_throwsRuntimeExceptionOnIoException() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any())).thenThrow(new IOException("connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> adapter.fetchTrades(SYMBOL, FROM, TO));
        assertEquals(true, ex.getMessage().contains("connection refused"));
    }

    @Test
    void fetchTrades_throwsRuntimeExceptionWhenJsonIsMalformed() throws IOException {
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any()))
                .thenReturn(new HttpClientPort.HttpResponse(200, "not-json"));

        assertThrows(RuntimeException.class, () -> adapter.fetchTrades(SYMBOL, FROM, TO));
    }

    @Test
    void fetchTrades_throwsExchangeQueryExceptionWhenNumericFieldIsNotNumeric() throws IOException {
        // HTTP 200 with a non-numeric price → NumberFormatException inside parseTrade.
        // Must surface as a provider-side ExchangeQueryException (502), never leak as
        // IllegalArgumentException (which the global handler maps to a 400 client error).
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        String json = """
                [{"id":"28457","orderId":100234,"clientOrderId":"client-1","symbol":"BTCUSDT",
                  "price":"not-a-number","qty":"0.01","quoteQty":"500.00","commission":"0.0001",
                  "commissionAsset":"BNB","time":1699865549590,"isBuyer":true}]
                """;
        when(httpClient.get(any(), any())).thenReturn(new HttpClientPort.HttpResponse(200, json));

        assertThrows(ExchangeQueryException.class, () -> adapter.fetchTrades(SYMBOL, FROM, TO));
    }

    @Test
    void fetchTrades_throwsExchangeQueryExceptionWhenPaginationCursorIdIsNotNumeric() throws IOException {
        // Full page (1000) triggers pagination, but the last trade's id is non-numeric.
        // The Long.parseLong cursor conversion must surface as ExchangeQueryException (502),
        // not leak as IllegalArgumentException (400) — same provider-payload contract as parseTrade.
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        String fullPageWithBadLastId = buildTradeJsonArrayWithLastId(1000, 0L, "not-a-number");
        when(httpClient.get(any(), any()))
                .thenReturn(new HttpClientPort.HttpResponse(200, fullPageWithBadLastId));

        assertThrows(ExchangeQueryException.class, () -> adapter.fetchTrades(SYMBOL, FROM, TO));
    }

    @Test
    void fetchTrades_issuesSingleRequestWhenWindowIsWithin24Hours() throws IOException {
        // FROM → TO is exactly 24h; should produce 1 buildMyTrades call, not 2
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any())).thenReturn(new HttpClientPort.HttpResponse(200, "[]"));

        adapter.fetchTrades(SYMBOL, FROM, TO);

        verify(requestBuilder, times(1)).buildMyTrades(anyString(), anyLong(), anyLong());
    }

    @Test
    void fetchTrades_splitsIntoTwoWindowsWhenPeriodSpans48Hours() throws IOException {
        // 48h → 2 separate buildMyTrades calls (one per 24h window)
        Instant from48 = Instant.parse("2026-01-01T00:00:00Z");
        Instant to48 = Instant.parse("2026-01-03T00:00:00Z");
        when(requestBuilder.buildMyTrades(anyString(), anyLong(), anyLong())).thenReturn(DUMMY_REQ);
        when(httpClient.get(any(), any())).thenReturn(new HttpClientPort.HttpResponse(200, "[]"));

        adapter.fetchTrades(SYMBOL, from48, to48);

        verify(requestBuilder, times(2)).buildMyTrades(anyString(), anyLong(), anyLong());
    }

    @Test
    void getExchangeName_returnsBinance() {
        assertEquals("BINANCE", adapter.getExchangeName());
    }

    private static String buildTradeJsonArray(int count, long startId) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(
                    "{\"id\":\"%d\",\"orderId\":100234,\"clientOrderId\":\"client-%d\",\"symbol\":\"BTCUSDT\"," +
                    "\"price\":\"50000\",\"qty\":\"0.01\",\"quoteQty\":\"500\",\"commission\":\"0.0001\"," +
                    "\"commissionAsset\":\"BNB\",\"time\":1699865549590,\"isBuyer\":true}",
                    startId + i, i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String buildTradeJsonArrayWithLastId(int count, long startId, String lastId) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(",");
            String id = (i == count - 1) ? "\"" + lastId + "\"" : "\"" + (startId + i) + "\"";
            sb.append(String.format(
                    "{\"id\":%s,\"orderId\":100234,\"clientOrderId\":\"client-%d\",\"symbol\":\"BTCUSDT\"," +
                    "\"price\":\"50000\",\"qty\":\"0.01\",\"quoteQty\":\"500\",\"commission\":\"0.0001\"," +
                    "\"commissionAsset\":\"BNB\",\"time\":1699865549590,\"isBuyer\":true}",
                    id, i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static ObjectMapper buildObjectMapper() {
        return new ObjectMapper();
    }
}
