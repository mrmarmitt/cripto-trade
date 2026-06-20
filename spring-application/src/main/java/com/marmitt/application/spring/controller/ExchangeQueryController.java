package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.exchange.BalanceDto;
import com.marmitt.core.dto.exchange.ExchangeBalanceSnapshot;
import com.marmitt.core.dto.reconciliation.TradeExecutionDto;
import com.marmitt.core.ports.inbound.exchange.QueryExchangeBalancePort;
import com.marmitt.core.ports.inbound.exchange.QueryTradeHistoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Consulta de leitura na exchange: saldo e histórico de trades.
 *
 * <p>Controllers finos — delegam aos ports inbound e deixam o tratamento de erro para o
 * {@code GlobalExceptionHandler} (exchange não registrada → 400; capacidade não suportada
 * pela exchange → 501; falha de comunicação → 502).
 */
@Slf4j
@RestController
@RequestMapping("/api/exchanges")
public class ExchangeQueryController {

    private final QueryExchangeBalancePort queryExchangeBalance;
    private final QueryTradeHistoryPort queryTradeHistory;

    public ExchangeQueryController(QueryExchangeBalancePort queryExchangeBalance,
                                   QueryTradeHistoryPort queryTradeHistory) {
        this.queryExchangeBalance = queryExchangeBalance;
        this.queryTradeHistory = queryTradeHistory;
    }

    /**
     * GET /api/exchanges/{exchangeName}/balances[?asset=USDT]
     *
     * <p>Retorna o snapshot completo de saldos. Com {@code asset}, filtra na borda para o
     * ativo informado (snapshot com 0 ou 1 saldo), mantendo o mesmo contrato de resposta.
     */
    @GetMapping("/{exchangeName}/balances")
    public ResponseEntity<ExchangeBalanceSnapshot> getBalances(
            @PathVariable String exchangeName,
            @RequestParam(required = false) String asset) {

        log.debug("exchange balances: exchange={} asset={}", exchangeName, asset);
        ExchangeBalanceSnapshot snapshot = queryExchangeBalance.queryBalances(exchangeName);

        if (asset != null && !asset.isBlank()) {
            List<BalanceDto> filtered = snapshot.balanceOf(asset).map(List::of).orElse(List.of());
            snapshot = new ExchangeBalanceSnapshot(snapshot.exchangeName(), filtered, snapshot.retrievedAt());
        }
        return ResponseEntity.ok(snapshot);
    }

    /**
     * GET /api/exchanges/{exchangeName}/trades?symbol=BTCUSDT&from=...&to=...
     */
    @GetMapping("/{exchangeName}/trades")
    public ResponseEntity<List<TradeExecutionDto>> getTrades(
            @PathVariable String exchangeName,
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        log.debug("exchange trades: exchange={} symbol={} from={} to={}", exchangeName, symbol, from, to);
        List<TradeExecutionDto> trades = queryTradeHistory.queryTrades(exchangeName, symbol, from, to);
        return ResponseEntity.ok(trades);
    }
}
