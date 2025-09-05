package com.marmitt.core.ports.outbound;

import com.marmitt.core.enums.NotificationType;
import com.marmitt.core.dto.notification.Notification;

import java.util.concurrent.CompletableFuture;

public interface NotificationPort {
    
    CompletableFuture<Void> sendNotification(Notification notification);
    
    CompletableFuture<Void> sendTradingAlert(String message, NotificationType type);
    
    CompletableFuture<Void> sendStrategyAlert(String strategyName, String message);
    
    CompletableFuture<Void> sendRiskAlert(String message);
    
    CompletableFuture<Void> sendSystemAlert(String message);
    
    void subscribeToNotifications(NotificationListener listener);
    
    void unsubscribeFromNotifications();
    
    boolean isNotificationEnabled(NotificationType type);
    
    void enableNotification(NotificationType type);
    
    void disableNotification(NotificationType type);
    
    @FunctionalInterface
    interface NotificationListener {
        void onNotification(Notification notification);
    }
}