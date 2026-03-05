package com.marmitt.mock.balance;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MockBalanceStore {

    private final Map<String, BigDecimal> available = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> reserved = new ConcurrentHashMap<>();

    public MockBalanceStore(Map<String, BigDecimal> initialBalances) {
        if (initialBalances != null) {
            for (Map.Entry<String, BigDecimal> entry : initialBalances.entrySet()) {
                String asset = normalize(entry.getKey());
                BigDecimal amount = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
                available.put(asset, amount);
                reserved.putIfAbsent(asset, BigDecimal.ZERO);
            }
        }
    }

    public synchronized boolean reserve(String asset, BigDecimal amount) {
        String key = normalize(asset);
        BigDecimal availableAmount = available.getOrDefault(key, BigDecimal.ZERO);
        if (availableAmount.compareTo(amount) < 0) {
            return false;
        }
        available.put(key, availableAmount.subtract(amount));
        reserved.put(key, reserved.getOrDefault(key, BigDecimal.ZERO).add(amount));
        return true;
    }

    public synchronized void release(String asset, BigDecimal amount) {
        String key = normalize(asset);
        BigDecimal reservedAmount = reserved.getOrDefault(key, BigDecimal.ZERO);
        if (reservedAmount.compareTo(amount) < 0) {
            amount = reservedAmount;
        }
        reserved.put(key, reservedAmount.subtract(amount));
        available.put(key, available.getOrDefault(key, BigDecimal.ZERO).add(amount));
    }

    public synchronized void credit(String asset, BigDecimal amount) {
        String key = normalize(asset);
        available.put(key, available.getOrDefault(key, BigDecimal.ZERO).add(amount));
    }

    public synchronized void debitReserved(String asset, BigDecimal amount) {
        String key = normalize(asset);
        BigDecimal reservedAmount = reserved.getOrDefault(key, BigDecimal.ZERO);
        if (reservedAmount.compareTo(amount) < 0) {
            amount = reservedAmount;
        }
        reserved.put(key, reservedAmount.subtract(amount));
    }

    public synchronized BigDecimal getAvailable(String asset) {
        return available.getOrDefault(normalize(asset), BigDecimal.ZERO);
    }

    public synchronized BigDecimal getReserved(String asset) {
        return reserved.getOrDefault(normalize(asset), BigDecimal.ZERO);
    }

    public synchronized Map<String, BigDecimal> snapshotAvailable() {
        return Collections.unmodifiableMap(new HashMap<>(available));
    }

    public synchronized Map<String, BigDecimal> snapshotReserved() {
        return Collections.unmodifiableMap(new HashMap<>(reserved));
    }

    private String normalize(String asset) {
        if (asset == null) {
            return "UNKNOWN";
        }
        return asset.trim().toUpperCase(Locale.ROOT);
    }
}
