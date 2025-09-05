package com.marmitt.core.dto.notification;

import com.marmitt.core.enums.NotificationPriority;
import com.marmitt.core.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

public record Notification(
        String id,
        NotificationType type,
        String title,
        String message,
        NotificationPriority priority,
        Map<String, Object> data,
        Instant timestamp,
        boolean read
) {
    public static Notification info(String title, String message) {
        return new Notification(
                generateId(),
                NotificationType.INFO,
                title,
                message,
                NotificationPriority.LOW,
                Map.of(),
                Instant.now(),
                false
        );
    }
    
    public static Notification warning(String title, String message) {
        return new Notification(
                generateId(),
                NotificationType.WARNING,
                title,
                message,
                NotificationPriority.MEDIUM,
                Map.of(),
                Instant.now(),
                false
        );
    }
    
    public static Notification error(String title, String message) {
        return new Notification(
                generateId(),
                NotificationType.ERROR,
                title,
                message,
                NotificationPriority.HIGH,
                Map.of(),
                Instant.now(),
                false
        );
    }
    
    public static Notification tradingAlert(String message, Map<String, Object> data) {
        return new Notification(
                generateId(),
                NotificationType.TRADING,
                "Trading Alert",
                message,
                NotificationPriority.HIGH,
                data,
                Instant.now(),
                false
        );
    }
    
    public static Notification strategyAlert(String strategyName, String message) {
        return new Notification(
                generateId(),
                NotificationType.STRATEGY,
                "Strategy Alert: " + strategyName,
                message,
                NotificationPriority.MEDIUM,
                Map.of("strategyName", strategyName),
                Instant.now(),
                false
        );
    }
    
    public static Notification riskAlert(String message) {
        return new Notification(
                generateId(),
                NotificationType.RISK,
                "Risk Management Alert",
                message,
                NotificationPriority.CRITICAL,
                Map.of(),
                Instant.now(),
                false
        );
    }
    
    public Notification markAsRead() {
        return new Notification(id, type, title, message, priority, data, timestamp, true);
    }
    
    public boolean isUnread() {
        return !read;
    }
    
    private static String generateId() {
        return java.util.UUID.randomUUID().toString();
    }
}