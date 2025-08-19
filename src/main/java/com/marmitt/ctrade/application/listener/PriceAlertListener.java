package com.marmitt.ctrade.application.listener;

import com.marmitt.ctrade.application.service.PriceAlertService;
import com.marmitt.ctrade.domain.dto.PriceUpdateMessage;
import com.marmitt.ctrade.domain.entity.PriceAlert;
import com.marmitt.ctrade.domain.listener.PriceUpdateListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceAlertListener implements PriceUpdateListener {
    
    private final PriceAlertService priceAlertService;
    
    @Override
    public void onPriceUpdate(PriceUpdateMessage message) {
        String tradingPair = message.getTradingPair();
        
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts(
            tradingPair, 
            message.getPrice()
        );
        
        if (!triggeredAlerts.isEmpty()) {
            log.info("Processed {} triggered alerts for {}", 
                    triggeredAlerts.size(), tradingPair);
            
            // Aqui poderia enviar notificações (email, webhook, etc.)
            triggeredAlerts.forEach(this::sendNotification);
        }
    }
    
    private void sendNotification(PriceAlert alert) {
        // Placeholder para sistema de notificações
        log.warn("🚨 PRICE ALERT: {} has {} {} (triggered at {})", 
                alert.getTradingPair(),
                alert.getAlertType() == PriceAlert.AlertType.ABOVE ? "exceeded" : "fallen below",
                alert.getThreshold(),
                alert.getTriggeredAt());
    }
}