package com.marmitt.ctrade.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {
    
    private String url = "wss://stream.binance.com:9443/ws/btcusdt@ticker";
    private Duration connectionTimeout = Duration.ofSeconds(30);
    private Duration readTimeout = Duration.ofSeconds(10);
    private int maxRetries = 3;
    private Duration retryInterval = Duration.ofSeconds(5);
    private boolean autoReconnect = true;
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    
    public Duration getReadTimeout() {
        return readTimeout;
    }
    
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public Duration getRetryInterval() {
        return retryInterval;
    }
    
    public void setRetryInterval(Duration retryInterval) {
        this.retryInterval = retryInterval;
    }
    
    public boolean isAutoReconnect() {
        return autoReconnect;
    }
    
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
}