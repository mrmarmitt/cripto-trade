package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.PriceAlertService;
import com.marmitt.ctrade.controller.dto.CreatePriceAlertRequest;
import com.marmitt.ctrade.domain.entity.PriceAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/price-alerts")
@RequiredArgsConstructor
@Slf4j
public class PriceAlertController {
    
    private final PriceAlertService priceAlertService;
    
    @PostMapping
    public ResponseEntity<PriceAlert> createAlert(@Valid @RequestBody CreatePriceAlertRequest request) {
        PriceAlert alert = new PriceAlert(
            request.getTradingPair(),
            request.getThreshold(), 
            request.getAlertType()
        );
        
        priceAlertService.addAlert(alert);
        log.info("Price alert created via API: {}", alert.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED).body(alert);
    }
    
    @GetMapping
    public ResponseEntity<List<PriceAlert>> getAllActiveAlerts() {
        List<PriceAlert> alerts = priceAlertService.getAllActiveAlerts();
        return ResponseEntity.ok(alerts);
    }
    
    @GetMapping("/pair/{tradingPair}")
    public ResponseEntity<List<PriceAlert>> getAlertsByPair(@PathVariable String tradingPair) {
        List<PriceAlert> alerts = priceAlertService.getActiveAlerts(tradingPair);
        return ResponseEntity.ok(alerts);
    }
    
    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> removeAlert(@PathVariable String alertId) {
        boolean removed = priceAlertService.removeAlert(alertId);
        
        if (removed) {
            log.info("Price alert removed via API: {}", alertId);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @DeleteMapping("/inactive")
    public ResponseEntity<Void> clearInactiveAlerts() {
        priceAlertService.clearInactiveAlerts();
        log.info("Inactive alerts cleared via API");
        return ResponseEntity.noContent().build();
    }
}