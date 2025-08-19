package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.PriceAlert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PriceAlertService {
    
    private final Map<String, List<PriceAlert>> alertsByPair = new ConcurrentHashMap<>();
    
    public void addAlert(PriceAlert alert) {
        alertsByPair.computeIfAbsent(alert.getTradingPair(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                   .add(alert);
        log.info("Price alert created: {} {} {}", 
                alert.getTradingPair(), 
                alert.getAlertType(), 
                alert.getThreshold());
    }

    public PriceAlert activeAlerts(String id, String tradingPair) {
        return alertsByPair.getOrDefault(tradingPair, List.of())
                .stream()
                .filter(priceAlert -> priceAlert.getId().equals(id))
                .peek(priceAlert -> priceAlert.setActive(true))
                .findFirst()
                .orElse(null);
    }

    public List<PriceAlert> getActiveAlerts(String tradingPair) {
        return alertsByPair.getOrDefault(tradingPair, List.of())
                          .stream()
                          .filter(PriceAlert::isActive)
                          .collect(Collectors.toList());
    }
    
    public List<PriceAlert> getAllActiveAlerts() {
        return alertsByPair.values().stream()
                          .flatMap(List::stream)
                          .filter(PriceAlert::isActive)
                          .collect(Collectors.toList());
    }
    
    public List<PriceAlert> checkAndTriggerAlerts(String tradingPair, BigDecimal currentPrice) {
        List<PriceAlert> activeAlerts = getActiveAlerts(tradingPair);

        return activeAlerts.stream()
                .filter(alert -> alert.shouldTrigger(currentPrice))
                .peek(alert -> {
                    alert.trigger();
                    log.warn("PRICE ALERT TRIGGERED! {} {} {} - Current: {}",
                            alert.getTradingPair(),
                            alert.getAlertType(),
                            alert.getThreshold(),
                            currentPrice);
                })
                .collect(Collectors.toList());
    }
    
    public boolean removeAlert(String alertId) {
        for (List<PriceAlert> alerts : alertsByPair.values()) {
            boolean removed = alerts.removeIf(alert -> alert.getId().equals(alertId));
            if (removed) {
                log.info("Price alert removed: {}", alertId);
                return true;
            }
        }
        return false;
    }
    
    public void clearInactiveAlerts() {
        alertsByPair.values().forEach(alerts -> 
            alerts.removeIf(alert -> !alert.isActive()));
        log.info("Inactive alerts cleared");
    }


}