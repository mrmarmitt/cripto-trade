package com.marmitt.core.dto.websocket.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representa dados de uma ordem processada de mensagens WebSocket.
 *
 * <p>{@code fee} e a comissao <b>deste</b> evento de execucao (por incremento) e {@code feeAsset} o
 * ativo em que ela foi cobrada (ex.: {@code BTC}, {@code USDT}, {@code BNB}). {@code feeAsset} nulo
 * significa ativo desconhecido (produtor que nao informa); o core aplica o comportamento legado nesse
 * caso (T46).
 */
public record OrderDataDto(
    String orderId,
    String clientOrderId,
    Symbol symbol,
    OrderSide side,
    OrderType type,
    BigDecimal quantity,
    BigDecimal executedQuantity,
    BigDecimal price,
    BigDecimal executedPrice,
    BigDecimal fee,
    OrderStatus status,
    String rejectReason,
    Instant timestamp,
    String feeAsset
) implements ProcessorResponse {

    /**
     * Construtor para produtores que nao conhecem o ativo da fee ({@code feeAsset = null}).
     */
    public OrderDataDto(String orderId, String clientOrderId, Symbol symbol, OrderSide side, OrderType type,
                        BigDecimal quantity, BigDecimal executedQuantity, BigDecimal price,
                        BigDecimal executedPrice, BigDecimal fee, OrderStatus status, String rejectReason,
                        Instant timestamp) {
        this(orderId, clientOrderId, symbol, side, type, quantity, executedQuantity, price, executedPrice,
                fee, status, rejectReason, timestamp, null);
    }

    public enum OrderSide {
        BUY, SELL
    }
    
    public enum OrderType {
        MARKET, LIMIT, STOP, STOP_LIMIT
    }
    
    public enum OrderStatus {
        NEW, PARTIALLY_FILLED, FILLED, CANCELED, REJECTED, EXPIRED
    }
}