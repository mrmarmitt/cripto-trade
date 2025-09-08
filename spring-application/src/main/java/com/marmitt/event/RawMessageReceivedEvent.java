package com.marmitt.event;

import com.marmitt.core.dto.websocket.MessageContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RawMessageReceivedEvent extends ApplicationEvent {
    
    private final String rawMessage;
    private final MessageContext context;
    
    public RawMessageReceivedEvent(Object source, String rawMessage, MessageContext context) {
        super(source);
        this.rawMessage = rawMessage;
        this.context = context;
    }

    @Override
    public String toString() {
        return "RawMessageReceivedEvent{" +
                "correlationId='" + context.correlationId() + '\'' +
                ", exchangeName='" + context.exchangeName() + '\'' +
                ", messageLength=" + rawMessage.length() +
                '}';
    }
}