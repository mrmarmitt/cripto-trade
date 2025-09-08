package com.marmitt.core.ports.outbound.websocket;

public interface MessageProcessorPort {
    
    void onMessage(String message);

}