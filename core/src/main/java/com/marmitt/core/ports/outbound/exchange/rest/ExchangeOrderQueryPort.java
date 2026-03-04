package com.marmitt.core.ports.outbound.exchange.rest;

import com.marmitt.core.dto.websocket.data.OrderDataDto;

import java.util.List;
import java.util.Optional;

/**
 * Capacidade REST para consulta de ordens (read path).
 *
 * <p>Principal consumidor: Boot/Recovery e reconciliacao.
 * A exchange e tratada como fonte autoritativa do estado financeiro.
 *
 * <p>Falhas de consulta devem preferir excecoes tipadas
 * ({@link com.marmitt.core.exceptions.ExchangeQueryException})
 * para classificacao robusta de retry no boot recovery.
 */
public interface ExchangeOrderQueryPort {

    Optional<OrderDataDto> queryOrderByClientOrderId(String symbol, String clientOrderId);

    Optional<OrderDataDto> queryOrderByExchangeOrderId(String symbol, String exchangeOrderId);

    List<OrderDataDto> listOpenOrdersBySymbol(String symbol);

    List<OrderDataDto> listAllOpenOrders();
}
