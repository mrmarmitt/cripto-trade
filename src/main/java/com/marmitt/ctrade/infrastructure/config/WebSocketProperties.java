package com.marmitt.ctrade.infrastructure.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConfigurationProperties(prefix = "websocket")
public class WebSocketProperties {
    
    private ExchangeType exchange;
    private String url;
    private Duration connectionTimeout;
    private Duration readTimeout;
    private int maxRetries;
    private Duration retryInterval;
    private boolean autoReconnect = true;

}