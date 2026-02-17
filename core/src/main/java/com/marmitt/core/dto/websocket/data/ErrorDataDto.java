package com.marmitt.core.dto.websocket.data;

import java.time.Instant;

/**
 * Representa dados de erro processados de mensagens WebSocket
 */
public record ErrorDataDto(
    String errorCode,
    String errorMessage, 
    String source,
    Instant timestamp
) implements ProcessorResponse {}