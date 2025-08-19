package com.marmitt.ctrade.controller.dto;

import com.marmitt.ctrade.domain.entity.PriceAlert;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ActivePriceAlertRequest {

    @NotBlank(message = "Id alert is required")
    private String id;

    @NotBlank(message = "Trading pair is required")
    private String tradingPair;
}