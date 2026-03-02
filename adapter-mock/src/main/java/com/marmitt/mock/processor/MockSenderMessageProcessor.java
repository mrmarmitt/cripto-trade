package com.marmitt.mock.processor;

import com.marmitt.core.dto.websocket.request.MessageRequest;
import com.marmitt.core.dto.websocket.request.SendOrderRequest;
import com.marmitt.core.dto.websocket.request.StreamSubscriptionRequest;
import com.marmitt.core.ports.outbound.exchange.adapter.SenderMessageProcessorPort;
import com.marmitt.mock.runtime.MockExchangeRuntime;
import lombok.extern.slf4j.Slf4j;

/**
 * Processor for outgoing mock messages.
 */
@Slf4j
public class MockSenderMessageProcessor implements SenderMessageProcessorPort {

    private final MockExchangeRuntime runtime;

    public MockSenderMessageProcessor(MockExchangeRuntime runtime) {
        this.runtime = runtime;
    }

    public void startLifecycle() {
        runtime.start();
    }

    public void stopLifecycle() {
        runtime.stop();
    }

    public void resetLifecycle() {
        runtime.reset();
    }

    public boolean isLifecycleRunning() {
        return runtime.isRunning();
    }

    @Override
    public String execute(MessageRequest request) {
        log.debug("Mock sender processing request - Type: {}", request.getClass().getSimpleName());

        if (request instanceof SendOrderRequest orderRequest) {
            return runtime.submitOrder(orderRequest);
        }
        if (request instanceof StreamSubscriptionRequest streamRequest) {
            return runtime.handleStream(streamRequest);
        }

        log.debug("Mock ignoring unsupported request type: {}", request.getClass().getSimpleName());
        return "{\"status\":\"ignored\",\"message\":\"Mock only processes order and stream requests\"}";
    }
}
