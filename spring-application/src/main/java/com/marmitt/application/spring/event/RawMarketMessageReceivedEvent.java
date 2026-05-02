package com.marmitt.application.spring.event;

import com.marmitt.core.dto.websocket.MessageContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RawMarketMessageReceivedEvent extends ApplicationEvent {

    private final String rawMessage;
    private final MessageContext context;

    public RawMarketMessageReceivedEvent(Object source, String rawMessage, MessageContext context) {
        super(source);
        this.rawMessage = rawMessage;
        this.context = context;
    }
}
