package com.marmitt.core.application.listener.runner;

import com.marmitt.core.domain.runner.ClientOrderId;
import com.marmitt.core.domain.runner.Transaction;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.listener.OrderUpdateListener;
import com.marmitt.core.ports.outbound.repository.StrategyRunnerRepositoryPort;
import lombok.extern.slf4j.Slf4j;

/**
 * Listener responsável por rotear callbacks de ordem da exchange para o {@link Transaction} correto.
 * <p>
 * Estratégia de roteamento:
 * <ol>
 *   <li>Valida se o {@code clientOrderId} está no formato v1 ({@code v1r{shortCode}...})</li>
 *   <li>Busca a {@link Transaction} pelo {@code clientOrderId}</li>
 *   <li>Aplica a transição de status na Transaction</li>
 *   <li>Roteia para o port correto conforme o status:
 *     <ul>
 *       <li>FILLED / PARTIALLY_FILLED → to do </li>
 *       <li>CANCELED / EXPIRED → {@link OrderConciliationPort}</li>
 *     </ul>
 *   </li>
 * </ol>
 * <p>
 * Um único listener que roteia internamente pelo {@code clientOrderId}.
 *
 * @see ClientOrderId#isValid(String)
 * @see StrategyRunnerRepositoryPort#findTransactionByClientOrderId(String)
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 5.6</a>
 */
@Slf4j
public class PortfolioStrategyRunnerOrderUpdateListener implements OrderUpdateListener {

    private final OrderConciliationPort orderConciliation;

    public PortfolioStrategyRunnerOrderUpdateListener(OrderConciliationPort orderConciliation) {
        this.orderConciliation = orderConciliation;
    }

    @Override
    public void onOrderUpdate(OrderDataDto orderData) {
        orderConciliation.execute(orderData);
    }

    @Override
    public boolean shouldProcess(OrderDataDto orderData) {
        return orderData.status() == OrderDataDto.OrderStatus.FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.PARTIALLY_FILLED ||
                orderData.status() == OrderDataDto.OrderStatus.CANCELED ||
                orderData.status() == OrderDataDto.OrderStatus.EXPIRED ||
                orderData.status() == OrderDataDto.OrderStatus.REJECTED;
    }
}
