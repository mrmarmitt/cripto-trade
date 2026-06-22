package com.marmitt.core.dto.runner;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.ports.outbound.exchange.rest.ExchangeOrderQueryPort;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecoveryContext {

    private final StrategyRunner runner;
    private final List<String> notes = new ArrayList<>();

    private boolean hasErrors;
    private List<Transaction> inFlight = List.of();
    private List<Transaction> limbo = List.of();
    private ExchangeOrderQueryPort orderQuery;
    private int remainingInFlight;

    public RecoveryContext(StrategyRunner runner) {
        this.runner = runner;
    }

    public StrategyRunner runner() { return runner; }
    public UUID runnerId() { return runner.getId(); }
    public String exchangeId() { return runner.getExchangeId(); }
    public boolean hasErrors() { return hasErrors; }
    public List<Transaction> inFlight() { return inFlight; }
    public List<Transaction> limbo() { return limbo; }
    public ExchangeOrderQueryPort orderQuery() { return orderQuery; }
    public int remainingInFlight() { return remainingInFlight; }
    public List<String> notes() { return List.copyOf(notes); }

    public void inFlight(List<Transaction> inFlight) { this.inFlight = inFlight; }
    public void limbo(List<Transaction> limbo) { this.limbo = limbo; }
    public void orderQuery(ExchangeOrderQueryPort orderQuery) { this.orderQuery = orderQuery; }
    public void remainingInFlight(int remainingInFlight) { this.remainingInFlight = remainingInFlight; }

    public void note(String msg) { notes.add(msg); }
    public void error(String msg) {
        hasErrors = true;
        notes.add(msg);
    }
}
