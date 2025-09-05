package com.marmitt.core.ports.outbound;

public interface WebSocketListenerPort {
    
    void onMessage(String message);

}