package com.marmitt.ctrade.controller.dto;

import com.marmitt.ctrade.domain.entity.PriceAlert;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreatePriceAlertRequest {
    
    @NotBlank(message = "Trading pair is required")
    private String tradingPair;
    
    @NotNull(message = "Threshold is required")
    @Positive(message = "Threshold must be positive")
    private BigDecimal threshold;
    
    @NotNull(message = "Alert type is required")
    private PriceAlert.AlertType alertType;
}