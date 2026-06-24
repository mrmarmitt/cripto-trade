package com.marmitt.core.dto.runner;

import java.util.Objects;
import java.util.UUID;

/**
 * Comando enviado ao {@code OrderDispatchPort} para cancelar uma ordem viva na exchange.
 * <p>
 * Espelha o {@link OrderDispatchCommand} (submit): carrega os dados mínimos para o adapter
 * construir a requisição de cancelamento específica da exchange. Agnóstico — nenhum DTO de
 * provider entra no core. O adapter não deve persistir nem acessar repositórios.
 * <p>
 * Fire-and-forget: o cancelamento é enviado e o {@code CANCELED} real chega de forma assíncrona
 * via stream, sendo conciliado pelo caminho idempotente. Nenhuma marcação terminal otimista.
 *
 * @param clientOrderId chave de idempotência da ordem a cancelar
 * @param runnerId      UUID do Runner dono da ordem
 * @param symbol        par de trading (ex: "BTCUSDT")
 * @param exchangeId    identificador da exchange destino
 */
public record OrderCancelCommand(
        String clientOrderId,
        UUID runnerId,
        String symbol,
        String exchangeId
) {
    public OrderCancelCommand {
        Objects.requireNonNull(clientOrderId, "clientOrderId cannot be null");
        Objects.requireNonNull(runnerId, "runnerId cannot be null");
        Objects.requireNonNull(symbol, "symbol cannot be null");
        Objects.requireNonNull(exchangeId, "exchangeId cannot be null");
    }
}
