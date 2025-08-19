package com.marmitt.ctrade.controller;

import com.marmitt.ctrade.application.service.PriceMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class PriceMetricsControllerIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    @Autowired
    private PriceMetricsService priceMetricsService;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        priceMetricsService.resetAllMetrics();
        
        // Add some sample data
        priceMetricsService.recordPriceUpdate("BTCUSD", new BigDecimal("45000"), LocalDateTime.now());
        priceMetricsService.recordPriceUpdate("ETHUSD", new BigDecimal("3000"), LocalDateTime.now());
    }
    
    @Test
    void shouldGetSystemMetricsSummary() throws Exception {
        mockMvc.perform(get("/api/metrics/summary"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalPairsTracked").value(2))
               .andExpect(jsonPath("$.totalPriceUpdates").value(2))
               .andExpect(jsonPath("$.systemAverageVolatility").exists())
               .andExpect(jsonPath("$.firstUpdateTime").exists())
               .andExpect(jsonPath("$.lastUpdateTime").exists());
    }
    
    @Test
    void shouldGetAllPriceMetrics() throws Exception {
        mockMvc.perform(get("/api/metrics/prices"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.length()").value(2));
    }
    
    @Test
    void shouldGetSpecificPairMetrics() throws Exception {
        mockMvc.perform(get("/api/metrics/prices/BTCUSD"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.tradingPair").value("BTCUSD"))
               .andExpect(jsonPath("$.currentPrice").value(45000))
               .andExpect(jsonPath("$.updateCount").value(1));
    }
    
    @Test
    void shouldReturnNotFoundForUnknownPair() throws Exception {
        mockMvc.perform(get("/api/metrics/prices/UNKNOWNUSD"))
               .andExpect(status().isNotFound());
    }
    
    @Test
    void shouldGetSystemVolatility() throws Exception {
        mockMvc.perform(get("/api/metrics/system/volatility"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isNumber());
    }
    
    @Test
    void shouldGetTotalUpdatesCount() throws Exception {
        mockMvc.perform(get("/api/metrics/system/updates-count"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").value(2));
    }
    
    @Test
    void shouldResetSpecificPairMetrics() throws Exception {
        mockMvc.perform(delete("/api/metrics/prices/BTCUSD"))
               .andExpect(status().isNoContent());
               
        mockMvc.perform(get("/api/metrics/prices/BTCUSD"))
               .andExpect(status().isNotFound());
    }
    
    @Test
    void shouldResetAllMetrics() throws Exception {
        mockMvc.perform(delete("/api/metrics/prices"))
               .andExpect(status().isNoContent());
               
        mockMvc.perform(get("/api/metrics/summary"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.totalPairsTracked").value(0))
               .andExpect(jsonPath("$.totalPriceUpdates").value(0));
    }
}