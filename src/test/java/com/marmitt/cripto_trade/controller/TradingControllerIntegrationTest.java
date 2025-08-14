package com.marmitt.cripto_trade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.cripto_trade.controller.dto.OrderRequest;
import com.marmitt.cripto_trade.controller.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should place buy order successfully with real services")
    void shouldPlaceBuyOrderSuccessfullyWithRealServices() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), new BigDecimal("50000"));

        MvcResult result = mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(0.5))
                .andExpect(jsonPath("$.price").value(50000))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").exists())
                .andReturn();

        OrderResponse orderResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        
        assertThat(orderResponse.getId()).isNotNull();
        assertThat(orderResponse.getStatus()).isIn("PENDING", "FILLED");
    }

    @Test
    @DisplayName("Should place sell order successfully with real services")
    void shouldPlaceSellOrderSuccessfullyWithRealServices() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.3"), new BigDecimal("52000"));

        mockMvc.perform(post("/api/trading/orders/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.side").value("SELL"))
                .andExpect(jsonPath("$.totalValue").exists());
    }

    @Test
    @DisplayName("Should place market buy order successfully with real services")
    void shouldPlaceMarketBuyOrderSuccessfullyWithRealServices() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.01"), null);

        mockMvc.perform(post("/api/trading/orders/market-buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.price").exists()) // Should have current market price
                .andExpect(jsonPath("$.status").value("FILLED")); // Market orders should fill immediately
    }

    @Test
    @DisplayName("Should get active orders successfully with real services")
    void shouldGetActiveOrdersSuccessfullyWithRealServices() throws Exception {
        // First, place an order to ensure we have at least one
        OrderRequest request = new OrderRequest("ETH/USD", new BigDecimal("1.0"), new BigDecimal("2900"));
        
        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Then get active orders
        mockMvc.perform(get("/api/trading/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should get current price successfully with real services")
    void shouldGetCurrentPriceSuccessfullyWithRealServices() throws Exception {
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "BTC", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.price").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should handle order workflow: create and cancel with real services")
    void shouldHandleOrderWorkflowCreateAndCancelWithRealServices() throws Exception {
        // Create an order
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.1"), new BigDecimal("48000"));
        
        MvcResult createResult = mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse orderResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), OrderResponse.class);
        String orderId = orderResponse.getId();

        // Check order status
        mockMvc.perform(get("/api/trading/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        // Try to cancel it (might succeed if pending, or fail if already filled)
        MvcResult cancelResult = mockMvc.perform(delete("/api/trading/orders/{orderId}", orderId))
                .andReturn();
        
        // The cancel operation should either succeed (200) or fail because order is already filled (400)
        assertThat(cancelResult.getResponse().getStatus()).isIn(200, 400);
    }

    @Test
    @DisplayName("Should return 400 for invalid order request with real validation")
    void shouldReturn400ForInvalidOrderRequestWithRealValidation() throws Exception {
        // Test with negative quantity
        OrderRequest invalidRequest = new OrderRequest("BTC/USD", new BigDecimal("-0.5"), new BigDecimal("50000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for unknown trading pair with real services")
    void shouldReturn400ForUnknownTradingPairWithRealServices() throws Exception {
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "UNKNOWN", "PAIR"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should return 400 for invalid trading pair format")
    void shouldReturn400ForInvalidTradingPairFormat() throws Exception {
        OrderRequest invalidRequest = new OrderRequest("INVALID-FORMAT", new BigDecimal("0.1"), new BigDecimal("1000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().is4xxClientError());
    }
}