package com.marmitt.core.dto.runner;

import com.marmitt.core.enums.TransactionType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando enviado ao {@code OrderDispatchPort} para submeter uma ordem na exchange.
 * <p>
 * Carrega os dados mínimos necessários para o adapter construir a requisição REST/WebSocket
 * específica da exchange. O adapter não deve persistir nem acessar repositórios.
 *
 * @param clientOrderId chave de idempotência gerada pelo Runner
 * @param runnerId      UUID do Runner que emite a ordem
 * @param symbol        par de trading (ex: "BTCUSDT")
 * @param exchangeId    identificador da exchange destino
 * @param type          BUY ou SELL
 * @param quantity      quantidade a negociar (em base asset)
 * @param price         preço de referência (market price — a exchange decide o melhor fill)
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1</a>
 */
public record OrderDispatchCommand(
        String clientOrderId,
        UUID runnerId,
        String symbol,
        String exchangeId,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal price
) {
    public OrderDispatchCommand {
        Objects.requireNonNull(clientOrderId, "clientOrderId cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(exchangeId, "exchangeId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(price, "price cannot be null");
    }
}
