package com.marmitt.core.dto.processing;

import com.marmitt.core.domain.data.ErrorData;

import java.time.Instant;
import java.util.Optional;

public sealed interface ProcessingResult<T> 
    permits ProcessingResult.Success, ProcessingResult.Error, ProcessingResult.Warning {
    
    String correlationId();
    Instant processedAt();
    
    boolean isSuccess();
    boolean isError();
    boolean isWarning();
    
    Optional<T> getData();
    Optional<String> getErrorMessage();
    Optional<Exception> getException();
    
    record Success<T>(
        String correlationId,
        T data,
        Instant processedAt
    ) implements ProcessingResult<T> {
        
        public Success(String correlationId, T data) {
            this(correlationId, data, Instant.now());
        }
        
        @Override
        public boolean isSuccess() { return true; }
        
        @Override
        public boolean isError() { return false; }
        
        @Override
        public boolean isWarning() { return false; }
        
        @Override
        public Optional<T> getData() { return Optional.of(data); }
        
        @Override
        public Optional<String> getErrorMessage() { return Optional.empty(); }
        
        @Override
        public Optional<Exception> getException() { return Optional.empty(); }
    }
    
    record Error(
        String correlationId,
        String errorMessage,
        Exception exception,
        Instant processedAt
    ) implements ProcessingResult<ErrorData> {
        
        public Error(String correlationId, String errorMessage) {
            this(correlationId, errorMessage, null, Instant.now());
        }
        
        public Error(String correlationId, String errorMessage, Exception exception) {
            this(correlationId, errorMessage, exception, Instant.now());
        }
        
        @Override
        public boolean isSuccess() { return false; }
        
        @Override
        public boolean isError() { return true; }
        
        @Override
        public boolean isWarning() { return false; }
        
        @Override
        public Optional<ErrorData> getData() { return Optional.empty(); }
        
        @Override
        public Optional<String> getErrorMessage() { return Optional.of(errorMessage); }
        
        @Override
        public Optional<Exception> getException() { return Optional.ofNullable(exception); }
    }
    
    record Warning<T>(
        String correlationId,
        T data,
        String warningMessage,
        Instant processedAt
    ) implements ProcessingResult<T> {
        
        public Warning(String correlationId, T data, String warningMessage) {
            this(correlationId, data, warningMessage, Instant.now());
        }
        
        @Override
        public boolean isSuccess() { return false; }
        
        @Override
        public boolean isError() { return false; }
        
        @Override
        public boolean isWarning() { return true; }
        
        @Override
        public Optional<T> getData() { return Optional.of(data); }
        
        @Override
        public Optional<String> getErrorMessage() { return Optional.of(warningMessage); }
        
        @Override
        public Optional<Exception> getException() { return Optional.empty(); }
    }
    
    // Factory methods para facilitar criação
    static <T> ProcessingResult<T> success(String correlationId, T data) {
        return new Success<>(correlationId, data);
    }
    
    static ProcessingResult<ErrorData> error(String correlationId, String errorMessage) {
        return new Error(correlationId, errorMessage);
    }
    
    static ProcessingResult<ErrorData> error(String correlationId, String errorMessage, Exception exception) {
        return new Error(correlationId, errorMessage, exception);
    }
    
    static <T> ProcessingResult<T> warning(String correlationId, T data, String warningMessage) {
        return new Warning<>(correlationId, data, warningMessage);
    }
}