package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.domain.Symbol;
import com.marmitt.core.dto.exchange.OrderSubmissionResult;
import com.marmitt.core.dto.runner.OrderCancelCommand;
import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import com.marmitt.core.dto.websocket.request.SendCancelOrderRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.inbound.runner.OrderConciliationPort;
import com.marmitt.core.ports.outbound.exchange.ExchangeAdapterDescriptor;
import com.marmitt.core.ports.outbound.exchange.ExchangeOrderPort;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderDispatchAdapter implements OrderDispatchPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;
    private final OrderConciliationPort orderConciliation;

    public OrderDispatchAdapter(ExchangeAdapterRepositoryPort exchangeAdapterRepository,
                                OrderConciliationPort orderConciliation) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
        this.orderConciliation = orderConciliation;
    }

    @Override
    public void dispatch(OrderDispatchCommand command) {
        if (exchangeAdapterRepository.isDispatchBlocked(command.exchangeId())) {
            log.warn("dispatch: blocked — connection lost for exchange={} clientOrderId={} — rejecting to release PENDING state",
                    command.exchangeId(), command.clientOrderId());
            orderConciliation.execute(toRejectedOrder(command, "dispatch blocked: connection lost"));
            return;
        }

        ExchangeOrderPort orderPort = exchangeAdapterRepository
                .findAdapter(command.exchangeId())
                .orElseThrow(() -> new IllegalStateException(
                        "No adapter registered for exchangeId: " + command.exchangeId()))
                .orderPort();

        SendOrderRequest request = toSendOrderRequest(command, command.exchangeId());
        OrderSubmissionResult result = orderPort.submitOrder(request);

        if (result.isCompleted()) {
            orderConciliation.execute(result.syncResult());
        } else if (result.isFailed()) {
            log.warn("dispatch: order rejected — clientOrderId={} exchange={} reason={}",
                    command.clientOrderId(), command.exchangeId(), result.failureReason());
            orderConciliation.execute(toRejectedOrder(command, result.failureReason()));
        }

        log.debug("dispatch: order sent — clientOrderId={} exchange={} symbol={} type={}",
                command.clientOrderId(), command.exchangeId(), command.symbol(), command.type());
    }

    @Override
    public void cancel(OrderCancelCommand command) {
        ExchangeAdapterDescriptor adapter = exchangeAdapterRepository
                .findAdapter(command.exchangeId())
                .orElseThrow(() -> new IllegalStateException(
                        "No adapter registered for exchangeId: " + command.exchangeId()));

        if (!adapter.hasOrderExecution()) {
            log.warn("cancel: order execution capability not available exchange={} clientOrderId={} — skipping",
                    command.exchangeId(), command.clientOrderId());
            return;
        }

        // Fire-and-forget: envia o cancelamento; o CANCELED real (e a liberacao de capital) chega
        // pelo stream e e conciliado pelo caminho idempotente. Sem marcacao terminal otimista.
        adapter.orderExecution().cancelOrder(
                new SendCancelOrderRequest(command.exchangeId(), command.clientOrderId(), command.symbol()));

        log.debug("cancel: cancel sent — clientOrderId={} exchange={} symbol={} — awaits CANCELED via stream",
                command.clientOrderId(), command.exchangeId(), command.symbol());
    }

    private SendOrderRequest toSendOrderRequest(OrderDispatchCommand command, String exchangeName) {
        OrderSide side = command.type() == TransactionType.BUY ? OrderSide.BUY : OrderSide.SELL;
        return new SendOrderRequest(
                exchangeName,
                command.symbol(),
                command.quantity(),
                command.price(),
                OrderType.LIMIT,
                side,
                command.clientOrderId()
        );
    }

    private OrderDataDto toRejectedOrder(OrderDispatchCommand command, String rejectReason) {
        OrderDataDto.OrderSide side = command.type() == TransactionType.BUY
                ? OrderDataDto.OrderSide.BUY : OrderDataDto.OrderSide.SELL;
        return new OrderDataDto(
                null,
                command.clientOrderId(),
                Symbol.of(command.symbol()),
                side,
                OrderDataDto.OrderType.LIMIT,
                command.quantity(),
                BigDecimal.ZERO,
                command.price(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderDataDto.OrderStatus.REJECTED,
                rejectReason,
                Instant.now()
        );
    }
}
