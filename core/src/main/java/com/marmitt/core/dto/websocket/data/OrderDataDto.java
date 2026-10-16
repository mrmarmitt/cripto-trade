package com.marmitt.core.dto.websocket.data;

import com.marmitt.core.domain.Symbol;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representa dados de uma ordem processada de mensagens WebSocket.
 *
 * <p>{@code fee} e a comissao <b>deste</b> evento de execucao (por incremento) e {@code feeAsset} o
 * ativo em que ela foi cobrada (ex.: {@code BTC}, {@code USDT}, {@code BNB}). Todo produtor informa
 * {@code feeAsset} explicitamente: nulo apenas quando o evento nao tem fee ou a fonte nao informa o
 * ativo (ex.: consulta de ordem); nesse caso o core aplica o comportamento legado (T46).
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