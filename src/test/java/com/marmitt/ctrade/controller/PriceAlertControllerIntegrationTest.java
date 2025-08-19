package com.marmitt.ctrade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.ctrade.application.service.PriceAlertService;
import com.marmitt.ctrade.controller.dto.CreatePriceAlertRequest;
import com.marmitt.ctrade.domain.entity.PriceAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PriceAlertControllerIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private PriceAlertService priceAlertService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        // Clear any existing alerts
        clearAllAlerts();
    }
    
    private void clearAllAlerts() {
        // Remove all alerts by getting them first then removing
        priceAlertService.getAllActiveAlerts().forEach(alert -> 
            priceAlertService.removeAlert(alert.getId()));
        priceAlertService.clearInactiveAlerts();
    }
    
    @Test
    void shouldCreatePriceAlertSuccessfully() throws Exception {
        // Given
        CreatePriceAlertRequest request = new CreatePriceAlertRequest();
        request.setTradingPair("BTCUSD");
        request.setThreshold(new BigDecimal("50000"));
        request.setAlertType(PriceAlert.AlertType.ABOVE);
        
        // When & Then
        mockMvc.perform(post("/api/price-alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").exists())
               .andExpect(jsonPath("$.tradingPair").value("BTCUSD"))
               .andExpect(jsonPath("$.threshold").value(50000))
               .andExpect(jsonPath("$.alertType").value("ABOVE"))
               .andExpect(jsonPath("$.active").value(true))
               .andExpect(jsonPath("$.createdAt").exists());
    }
    
    @Test
    void shouldValidateCreatePriceAlertRequest() throws Exception {
        // Given - invalid request (missing required fields)
        CreatePriceAlertRequest request = new CreatePriceAlertRequest();
        request.setTradingPair(""); // empty trading pair
        request.setThreshold(new BigDecimal("-100")); // negative threshold
        // missing alert type
        
        // When & Then
        mockMvc.perform(post("/api/price-alerts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
               .andExpect(status().isBadRequest());
    }
    
    @Test
    void shouldGetAllActiveAlerts() throws Exception {
        // Given - create some test alerts
        PriceAlert alert1 = new PriceAlert("BTCUSD", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert alert2 = new PriceAlert("ETHUSD", new BigDecimal("2500"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(alert1);
        priceAlertService.addAlert(alert2);
        
        // When & Then
        mockMvc.perform(get("/api/price-alerts"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].tradingPair").exists())
               .andExpect(jsonPath("$[1].tradingPair").exists());
    }
    
    @Test
    void shouldGetAlertsByTradingPair() throws Exception {
        // Given
        String tradingPair = "BTCUSD";
        PriceAlert alert1 = new PriceAlert(tradingPair, new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        PriceAlert alert2 = new PriceAlert(tradingPair, new BigDecimal("55000"), PriceAlert.AlertType.ABOVE);
        PriceAlert alert3 = new PriceAlert("ETHUSD", new BigDecimal("3000"), PriceAlert.AlertType.ABOVE);
        
        priceAlertService.addAlert(alert1);
        priceAlertService.addAlert(alert2);
        priceAlertService.addAlert(alert3);
        
        // When & Then
        mockMvc.perform(get("/api/price-alerts/pair/{tradingPair}", tradingPair))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2))
               .andExpect(jsonPath("$[0].tradingPair").value(tradingPair))
               .andExpect(jsonPath("$[1].tradingPair").value(tradingPair));
    }
    
    @Test
    void shouldRemoveAlertSuccessfully() throws Exception {
        // Given
        PriceAlert alert = new PriceAlert("ADAUSD", new BigDecimal("0.5"), PriceAlert.AlertType.ABOVE);
        priceAlertService.addAlert(alert);
        String alertId = alert.getId();
        
        // When & Then
        mockMvc.perform(delete("/api/price-alerts/{alertId}", alertId))
               .andExpect(status().isNoContent());
        
        // Verify alert was removed
        mockMvc.perform(get("/api/price-alerts"))
               .andExpect(jsonPath("$.length()").value(0));
    }
    
    @Test
    void shouldReturnNotFoundWhenRemovingNonExistentAlert() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/price-alerts/{alertId}", "non-existent-id"))
               .andExpect(status().isNotFound());
    }
    
    @Test
    void shouldClearInactiveAlertsSuccessfully() throws Exception {
        // Given - create alerts and trigger one
        PriceAlert activeAlert = new PriceAlert("BTCUSD", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert triggeredAlert = new PriceAlert("ETHUSD", new BigDecimal("3000"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(activeAlert);
        priceAlertService.addAlert(triggeredAlert);
        
        // Trigger one alert
        triggeredAlert.trigger();
        
        // When & Then
        mockMvc.perform(delete("/api/price-alerts/inactive"))
               .andExpect(status().isNoContent());
    }
    
    @Test
    void shouldHandleEmptyAlertsListGracefully() throws Exception {
        // When & Then - no alerts exist
        mockMvc.perform(get("/api/price-alerts"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
               
        mockMvc.perform(get("/api/price-alerts/pair/NONEXISTENT"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(0));
    }
}