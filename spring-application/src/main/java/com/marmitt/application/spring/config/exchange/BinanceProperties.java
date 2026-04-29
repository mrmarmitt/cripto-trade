package com.marmitt.application.spring.config.exchange;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "binance")
public class BinanceProperties {

    @NotBlank(message = "binance.ws-base-url cannot be blank")
    private String wsBaseUrl;

    @NotBlank(message = "binance.rest-base-url cannot be blank")
    private String restBaseUrl;

    @NotBlank(message = "binance.api-key is required when binance.api-secret is defined")
    private String apiKey;

    @NotBlank(message = "binance.api-secret is required when binance.api-key is defined")
    private String apiSecret;

    public String getWsBaseUrl() {
        return wsBaseUrl;
    }

    public void setWsBaseUrl(String wsBaseUrl) {
        this.wsBaseUrl = wsBaseUrl;
    }

    public String getRestBaseUrl() {
        return restBaseUrl;
    }

    public void setRestBaseUrl(String restBaseUrl) {
        this.restBaseUrl = restBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }
}
