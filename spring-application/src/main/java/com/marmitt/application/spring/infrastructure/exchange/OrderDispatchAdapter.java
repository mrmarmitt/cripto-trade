package com.marmitt.application.spring.infrastructure.exchange;

import com.marmitt.core.dto.runner.OrderDispatchCommand;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.enums.OrderSide;
import com.marmitt.core.enums.OrderType;
import com.marmitt.core.enums.TransactionType;
import com.marmitt.core.ports.outbound.exchange.OrderDispatchPort;
import com.marmitt.core.ports.outbound.exchange.adapter.ExchangeAdapterPort;
import com.marmitt.core.ports.outbound.repository.ExchangeAdapterRepositoryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter de saída responsável por despachar ordens à exchange via WebSocket.
 * <p>
 * Fire-and-forget: serializa o comando, envia pelo canal WebSocket e retorna imediatamente.
 * A confirmação (SUBMITTED) e a rejeição (REJECTED) chegam de forma assíncrona via
 * {@code onMessage()} → {@code BinanceReceivedMessageProcessor} → {@code OrderConciliationUseCase}.
 * <p>
 * Em caso de falha técnica (WebSocket desconectado, serialização), lança exceção.
 * O chamador deixa a transação em {@code PENDING} para reconciliação pelo Boot Sequence.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1, 15.8.1</a>
 */
@Slf4j
@Component
public class OrderDispatchAdapter implements OrderDispatchPort {

    private final ExchangeAdapterRepositoryPort exchangeAdapterRepository;

    public OrderDispatchAdapter(ExchangeAdapterRepositoryPort exchangeAdapterRepository) {
        this.exchangeAdapterRepository = exchangeAdapterRepository;
    }

    @Override
    public void dispatch(OrderDispatchCommand command) {
        ExchangeAdapterPort adapter = exchangeAdapterRepository
                .findByName(command.exchangeId())
                .orElseThrow(() -> new IllegalStateException(
                        "No exchange adapter found for exchangeId: " + command.exchangeId()));

        SendOrderRequest request = toSendOrderRequest(command, adapter.getExchangeName());
        String json = adapter.getSenderMessageProcessor().execute(request);
        adapter.getWebSocketPort().sendMessage(json);

        log.debug("dispatch: order sent — clientOrderId={} exchange={} symbol={} type={}",
                command.clientOrderId(), command.exchangeId(), command.symbol(), command.type());
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
}
