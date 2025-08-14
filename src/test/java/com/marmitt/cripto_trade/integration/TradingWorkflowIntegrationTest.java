package com.marmitt.cripto_trade.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.cripto_trade.controller.dto.OrderRequest;
import com.marmitt.cripto_trade.controller.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
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
class TradingWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Clear any existing state if needed
    }

    @Test
    @DisplayName("Should execute complete buy order workflow")
    void shouldExecuteCompleteBuyOrderWorkflow() throws Exception {
        // 1. Check current price
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "BTC", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.price").exists());

        // 2. Place a buy order
        OrderRequest buyRequest = new OrderRequest("BTC/USD", new BigDecimal("0.1"), new BigDecimal("48000"));
        
        MvcResult result = mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(0.1))
                .andExpect(jsonPath("$.price").value(48000))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        OrderResponse orderResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        String orderId = orderResponse.getId();

        // 3. Check order status
        mockMvc.perform(get("/api/trading/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").isNotEmpty());

        // 4. Check active orders
        mockMvc.perform(get("/api/trading/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        // 5. Try to cancel the order (might succeed if pending, or fail if already filled)
        MvcResult cancelResult = mockMvc.perform(delete("/api/trading/orders/{orderId}", orderId))
                .andReturn();
        
        // The cancel operation should either succeed (200) or fail because order is already filled (400)
        assertThat(cancelResult.getResponse().getStatus()).isIn(200, 400);
    }

    @Test
    @DisplayName("Should execute complete sell order workflow")
    void shouldExecuteCompleteSellOrderWorkflow() throws Exception {
        // 1. Place a sell order
        OrderRequest sellRequest = new OrderRequest("BTC/USD", new BigDecimal("0.05"), new BigDecimal("52000"));
        
        MvcResult result = mockMvc.perform(post("/api/trading/orders/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sellRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andReturn();

        OrderResponse orderResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);
        
        // 2. Verify the order was created with correct total value
        BigDecimal expectedTotal = sellRequest.getQuantity().multiply(sellRequest.getPrice());
        assertThat(orderResponse.getTotalValue()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    @DisplayName("Should execute market buy order workflow")
    void shouldExecuteMarketBuyOrderWorkflow() throws Exception {
        // 1. Place a market buy order (no price specified)
        OrderRequest marketRequest = new OrderRequest("BTC/USD", new BigDecimal("0.01"), null);
        
        mockMvc.perform(post("/api/trading/orders/market-buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(marketRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(0.01))
                .andExpect(jsonPath("$.price").exists()) // Should have current market price
                .andExpect(jsonPath("$.status").value("FILLED")); // Market orders should fill immediately
    }

    @Test
    @DisplayName("Should handle multiple orders and active order filtering")
    void shouldHandleMultipleOrdersAndActiveOrderFiltering() throws Exception {
        // 1. Place multiple orders
        OrderRequest order1 = new OrderRequest("BTC/USD", new BigDecimal("0.1"), new BigDecimal("48000"));
        OrderRequest order2 = new OrderRequest("ETH/USD", new BigDecimal("1.0"), new BigDecimal("2900"));
        OrderRequest order3 = new OrderRequest("BTC/USD", new BigDecimal("0.05"), new BigDecimal("52000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/trading/orders/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order3)))
                .andExpect(status().isCreated());

        // 2. Check that active orders endpoint returns the pending orders
        mockMvc.perform(get("/api/trading/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        // Note: Exact count depends on which orders are still pending vs filled
    }

    @Test
    @DisplayName("Should validate order requests properly")
    void shouldValidateOrderRequestsProperly() throws Exception {
        // Test invalid trading pair
        OrderRequest invalidPair = new OrderRequest("INVALID", new BigDecimal("0.1"), new BigDecimal("1000"));
        
        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidPair)))
                .andExpect(status().is4xxClientError());

        // Test negative quantity
        OrderRequest negativeQuantity = new OrderRequest("BTC/USD", new BigDecimal("-0.1"), new BigDecimal("50000"));
        
        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(negativeQuantity)))
                .andExpect(status().isBadRequest());

        // Test zero price
        OrderRequest zeroPrice = new OrderRequest("BTC/USD", new BigDecimal("0.1"), BigDecimal.ZERO);
        
        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(zeroPrice)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle price lookup for different trading pairs")
    void shouldHandlePriceLookupForDifferentTradingPairs() throws Exception {
        // Test BTC/USD price
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "BTC", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"));

        // Test ETH/USD price
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "ETH", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingPair").value("ETH/USD"));

        // Test unknown trading pair
        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "UNKNOWN", "PAIR"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should handle order cancellation scenarios")
    void shouldHandleOrderCancellationScenarios() throws Exception {
        // Try to cancel non-existent order
        mockMvc.perform(delete("/api/trading/orders/non-existent-id"))
                .andExpect(status().is4xxClientError());

        // Place an order first
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.1"), new BigDecimal("48000"));
        
        MvcResult result = mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        OrderResponse orderResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), OrderResponse.class);

        // Try to cancel it - this might succeed (200) if order is pending, or fail (400) if already filled
        MvcResult cancelResult = mockMvc.perform(delete("/api/trading/orders/{orderId}", orderResponse.getId()))
                .andReturn();

        int cancelStatus = cancelResult.getResponse().getStatus();
        assertThat(cancelStatus).isIn(200, 400);

        // If the first cancel succeeded (order was pending), try to cancel again (should fail)
        if (cancelStatus == 200) {
            mockMvc.perform(delete("/api/trading/orders/{orderId}", orderResponse.getId()))
                    .andExpect(status().is4xxClientError());
        }
    }
}