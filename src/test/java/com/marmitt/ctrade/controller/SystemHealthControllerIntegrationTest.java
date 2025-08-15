package com.marmitt.ctrade.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class SystemHealthControllerIntegrationTest {
    
    @Autowired
    private WebApplicationContext webApplicationContext;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }
    
    @Test
    void shouldReturnSystemHealthWithRealComponents() throws Exception {
        mockMvc.perform(get("/api/system/health"))
            .andExpect(status().is2xxSuccessful()) // Aceita 200 (UP) ou 207 (DEGRADED)
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.cache").exists())
            .andExpect(jsonPath("$.cache.status").value("UP"))
            .andExpect(jsonPath("$.cache.tradingPairs").isNumber())
            .andExpect(jsonPath("$.cache.totalEntries").isNumber())
            .andExpect(jsonPath("$.cache.ttlMinutes").exists())
            .andExpect(jsonPath("$.cache.maxHistorySize").exists())
            .andExpect(jsonPath("$.webSocket").exists())
            .andExpect(jsonPath("$.webSocket.status").exists())
            .andExpect(jsonPath("$.webSocket.connected").isBoolean())
            .andExpect(jsonPath("$.webSocket.subscribedPairs").isNumber())
            .andExpect(jsonPath("$.webSocket.orderUpdatesSubscribed").isBoolean());
    }
}