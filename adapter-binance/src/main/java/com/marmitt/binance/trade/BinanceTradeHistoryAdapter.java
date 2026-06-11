package com.marmitt.binance.trade;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.rest.BinanceRestRequestBuilder;
import com.marmitt.binance.rest.HttpClientPort;
import com.marmitt.binance.rest.RestRequest;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.outbound.exchange.TradeHistoryQueryPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BinanceTradeHistoryAdapter implements TradeHistoryQueryPort {

    private static final int PAGE_LIMIT = 1000;
    // Binance caps startTime/endTime window at 24 hours for GET /api/v3/myTrades
    private static final long WINDOW_MS = 24L * 60 * 60 * 1000;

    private final BinanceRestRequestBuilder requestBuilder;
    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;

    public BinanceTradeHistoryAdapter(BinanceRestRequestBuilder requestBuilder,
                                      HttpClientPort httpClient,
                                      ObjectMapper objectMapper) {
        this.requestBuilder = requestBuilder;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getExchangeName() {
        return "BINANCE";
    }

    @Override
    public List<TradeExecutionDto> fetchTrades(String symbol, Instant from, Instant to) {
        List<TradeExecutionDto> all = new ArrayList<>();
        long windowStart = from.toEpochMilli();
        long totalEnd = to.toEpochMilli();

        while (windowStart <= totalEnd) {
            // Keep each Binance request within the 24-hour limit.
            // Last window uses totalEnd so it always covers the requested endpoint exactly.
            boolean isLastWindow = (totalEnd - windowStart) <= WINDOW_MS;
            long windowEnd = isLastWindow ? totalEnd : windowStart + WINDOW_MS - 1;
            fetchWindowInto(symbol, windowStart, windowEnd, all);
            if (isLastWindow) break;
            windowStart = windowEnd + 1;
        }

        log.debug("reconciliation: fetched {} trades for symbol={} from={} to={}", all.size(), symbol, from, to);
        return all;
    }

    private void fetchWindowInto(String symbol, long startMs, long endMs, List<TradeExecutionDto> all) {
        Instant windowEndInstant = Instant.ofEpochMilli(endMs);

        List<TradeExecutionDto> page = fetchPage(requestBuilder.buildMyTrades(symbol, startMs, endMs));
        all.addAll(page);

        // Paginate: if full page returned, advance using fromId of last trade
        while (page.size() == PAGE_LIMIT) {
            long lastId = Long.parseLong(page.getLast().exchangeTradeId());
            page = fetchPage(requestBuilder.buildMyTradesFromId(symbol, lastId + 1));
            // Filter client-side to stay within this window's end (fromId ignores time range)
            page = page.stream()
                    .filter(t -> !t.executedAt().isAfter(windowEndInstant))
                    .toList();
            all.addAll(page);
            if (page.size() < PAGE_LIMIT) break;
        }
    }

    private List<TradeExecutionDto> fetchPage(RestRequest req) {
        HttpClientPort.HttpResponse response;
        try {
            response = httpClient.get(req.url(), req.headers());
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch myTrades from Binance: " + e.getMessage(), e);
        }

        if (!response.isSuccessful()) {
            throw new RuntimeException(
                    "Binance myTrades returned HTTP " + response.statusCode() + ": " + response.body());
        }

        try {
            JsonNode root = objectMapper.readTree(response.body());
            List<TradeExecutionDto> trades = new ArrayList<>(root.size());
            for (JsonNode node : root) {
                trades.add(parseTrade(node));
            }
            return trades;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse myTrades response: " + e.getMessage(), e);
        }
    }

    private TradeExecutionDto parseTrade(JsonNode node) {
        return new TradeExecutionDto(
                node.path("id").asText(),
                node.path("orderId").asLong(),
                node.path("clientOrderId").asText(null),
                node.path("symbol").asText(),
                new BigDecimal(node.path("price").asText()),
                new BigDecimal(node.path("qty").asText()),
                new BigDecimal(node.path("quoteQty").asText()),
                new BigDecimal(node.path("commission").asText("0")),
                node.path("commissionAsset").asText(null),
                Instant.ofEpochMilli(node.path("time").asLong()),
                node.path("isBuyer").asBoolean()
        );
    }
}
