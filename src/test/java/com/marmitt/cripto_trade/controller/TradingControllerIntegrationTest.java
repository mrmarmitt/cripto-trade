package com.marmitt.cripto_trade.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.cripto_trade.application.service.TradingService;
import com.marmitt.cripto_trade.controller.dto.OrderRequest;
import com.marmitt.cripto_trade.domain.entity.Order;
import com.marmitt.cripto_trade.domain.entity.TradingPair;
import com.marmitt.cripto_trade.domain.valueobject.Price;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TradingController.class)
class TradingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TradingService tradingService;

    @Test
    @DisplayName("Should place buy order successfully")
    void shouldPlaceBuyOrderSuccessfully() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), new BigDecimal("50000"));
        TradingPair tradingPair = new TradingPair("BTC/USD");
        Order mockOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                                  request.getQuantity(), request.getPrice());

        when(tradingService.placeBuyOrder(any(TradingPair.class), eq(request.getQuantity()), eq(request.getPrice())))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tradingPair").value("BTC/USD"))
                .andExpect(jsonPath("$.type").value("LIMIT"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(0.5))
                .andExpect(jsonPath("$.price").value(50000))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should place sell order successfully")
    void shouldPlaceSellOrderSuccessfully() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), new BigDecimal("50000"));
        TradingPair tradingPair = new TradingPair("BTC/USD");
        Order mockOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, 
                                  request.getQuantity(), request.getPrice());

        when(tradingService.placeSellOrder(any(TradingPair.class), eq(request.getQuantity()), eq(request.getPrice())))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/trading/orders/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.side").value("SELL"));
    }

    @Test
    @DisplayName("Should place market buy order successfully")
    void shouldPlaceMarketBuyOrderSuccessfully() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), null);
        TradingPair tradingPair = new TradingPair("BTC/USD");
        Order mockOrder = new Order(tradingPair, Order.OrderType.MARKET, Order.OrderSide.BUY, 
                                  request.getQuantity(), new BigDecimal("50000"));

        when(tradingService.placeMarketBuyOrder(any(TradingPair.class), eq(request.getQuantity())))
                .thenReturn(mockOrder);

        mockMvc.perform(post("/api/trading/orders/market-buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MARKET"))
                .andExpect(jsonPath("$.side").value("BUY"));
    }

    @Test
    @DisplayName("Should cancel order successfully")
    void shouldCancelOrderSuccessfully() throws Exception {
        String orderId = "order123";
        TradingPair tradingPair = new TradingPair("BTC/USD");
        Order mockOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                                  new BigDecimal("0.5"), new BigDecimal("50000"));
        mockOrder.updateStatus(Order.OrderStatus.CANCELLED);

        when(tradingService.cancelOrder(orderId)).thenReturn(mockOrder);

        mockMvc.perform(delete("/api/trading/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("Should get order status successfully")
    void shouldGetOrderStatusSuccessfully() throws Exception {
        String orderId = "order123";
        TradingPair tradingPair = new TradingPair("BTC/USD");
        Order mockOrder = new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                                  new BigDecimal("0.5"), new BigDecimal("50000"));

        when(tradingService.getOrderStatus(orderId)).thenReturn(mockOrder);

        mockMvc.perform(get("/api/trading/orders/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mockOrder.getId()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("Should get active orders successfully")
    void shouldGetActiveOrdersSuccessfully() throws Exception {
        TradingPair tradingPair = new TradingPair("BTC/USD");
        List<Order> mockOrders = Arrays.asList(
                new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.BUY, 
                         new BigDecimal("0.5"), new BigDecimal("50000")),
                new Order(tradingPair, Order.OrderType.LIMIT, Order.OrderSide.SELL, 
                         new BigDecimal("0.3"), new BigDecimal("51000"))
        );

        when(tradingService.getActiveOrders()).thenReturn(mockOrders);

        mockMvc.perform(get("/api/trading/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[1].side").value("SELL"));
    }

    @Test
    @DisplayName("Should get current price successfully")
    void shouldGetCurrentPriceSuccessfully() throws Exception {
        String tradingPairSymbol = "BTC/USD";
        Price mockPrice = new Price("50000");

        when(tradingService.getCurrentPrice(any(TradingPair.class))).thenReturn(mockPrice);

        mockMvc.perform(get("/api/trading/price/{baseCurrency}/{quoteCurrency}", "BTC", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradingPair").value(tradingPairSymbol))
                .andExpect(jsonPath("$.price").value(50000))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return 400 for invalid order request - missing trading pair")
    void shouldReturn400ForInvalidOrderRequestMissingTradingPair() throws Exception {
        OrderRequest request = new OrderRequest(null, new BigDecimal("0.5"), new BigDecimal("50000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid order request - negative quantity")
    void shouldReturn400ForInvalidOrderRequestNegativeQuantity() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("-0.5"), new BigDecimal("50000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid order request - zero quantity")
    void shouldReturn400ForInvalidOrderRequestZeroQuantity() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", BigDecimal.ZERO, new BigDecimal("50000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for invalid order request - negative price")
    void shouldReturn400ForInvalidOrderRequestNegativePrice() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), new BigDecimal("-50000"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully")
    void shouldHandleServiceExceptionsGracefully() throws Exception {
        OrderRequest request = new OrderRequest("BTC/USD", new BigDecimal("0.5"), new BigDecimal("50000"));

        when(tradingService.placeBuyOrder(any(TradingPair.class), any(BigDecimal.class), any(BigDecimal.class)))
                .thenThrow(new IllegalArgumentException("Invalid trading pair"));

        mockMvc.perform(post("/api/trading/orders/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }
}