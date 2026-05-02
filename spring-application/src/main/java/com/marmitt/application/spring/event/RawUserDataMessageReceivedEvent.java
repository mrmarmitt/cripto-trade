package com.marmitt.application.spring.event;

import com.marmitt.core.dto.websocket.MessageContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RawUserDataMessageReceivedEvent extends ApplicationEvent {

    private final String rawMessage;
    private final MessageContext context;

    public RawUserDataMessageReceivedEvent(Object source, String rawMessage, MessageContext context) {
        super(source);
        this.rawMessage = rawMessage;
        this.context = context;
    }
}
