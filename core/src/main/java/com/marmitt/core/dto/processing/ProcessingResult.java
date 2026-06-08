package com.marmitt.core.dto.processing;

import com.marmitt.core.dto.websocket.data.ErrorDataDto;

import java.time.Instant;
import java.util.Optional;

public sealed interface ProcessingResult<T>
        permits ProcessingResult.Success, ProcessingResult.Error, ProcessingResult.Warning, ProcessingResult.Ignored {

    String correlationId();

    Instant processedAt();

    boolean isSuccess();

    boolean isError();

    boolean isWarning();

    Optional<T> getData();

    Optional<String> getErrorMessage();

    Optional<Exception> getException();

    Optional<String> getRawMessage();

    record Success<T>(
            String correlationId,
            String rawMessage,
            T data,
            Instant processedAt
    ) implements ProcessingResult<T> {

        public Success(String correlationId, String rawMessage, T data) {
            this(correlationId, rawMessage, data, Instant.now());
        }

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public boolean isWarning() {
            return false;
        }

        @Override
        public Optional<T> getData() {
            return Optional.of(data);
        }

        @Override
        public Optional<String> getErrorMessage() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getRawMessage() {
            return Optional.of(rawMessage);
        }

        @Override
        public Optional<Exception> getException() {
            return Optional.empty();
        }
    }

    record Error(
            String correlationId,
            String errorMessage,
            String rawMessage,
            Exception exception,
            Instant processedAt
    ) implements ProcessingResult<ErrorDataDto> {

        public Error(String correlationId, String errorMessage, String rawMessage) {
            this(correlationId, errorMessage, rawMessage, null, Instant.now());
        }

        public Error(String correlationId, String errorMessage, Exception exception) {
            this(correlationId, errorMessage, null, exception, Instant.now());
        }

        public Error(String correlationId, String errorMessage, String rawMessage, Exception exception) {
            this(correlationId, errorMessage, rawMessage, exception, Instant.now());
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isError() {
            return true;
        }

        @Override
        public boolean isWarning() {
            return false;
        }

        @Override
        public Optional<ErrorDataDto> getData() {
            return Optional.empty();
        }

        @Override
        public Optional<String> getErrorMessage() {
            return Optional.of(errorMessage);
        }

        @Override
        public Optional<String> getRawMessage() {
            return Optional.ofNullable(rawMessage);
        }

        @Override
        public Optional<Exception> getException() {
            return Optional.ofNullable(exception);
        }
    }

    record Warning<T>(
            String correlationId,
            String rawMessage,
            T data,
            String warningMessage,
            Instant processedAt
    ) implements ProcessingResult<T> {

        public Warning(String correlationId, String rawMessage, T data, String warningMessage) {
            this(correlationId, rawMessage, data, warningMessage, Instant.now());
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public boolean isError() {
            return false;
        }

        @Override
        public boolean isWarning() {
            return true;
        }

        @Override
        public Optional<T> getData() {
            return Optional.of(data);
        }

        @Override
        public Optional<String> getErrorMessage() {
            return Optional.of(warningMessage);
        }
        @Override
        public Optional<String> getRawMessage() {
            return Optional.of(rawMessage);
        }

        @Override
        public Optional<Exception> getException() {
            return Optional.empty();
        }
    }

    // Factory methods para facilitar criação
    static <T> ProcessingResult<T> success(String correlationId, String rawMessage, T data) {
        return new Success<>(correlationId, rawMessage, data);
    }

    static ProcessingResult<ErrorDataDto> error(String correlationId, String errorMessage, String rawMessage) {
        return new Error(correlationId, errorMessage, rawMessage);
    }

    static ProcessingResult<ErrorDataDto> error(String correlationId, String errorMessage) {
        return new Error(correlationId, errorMessage, "");
    }

    static ProcessingResult<ErrorDataDto> error(String correlationId, String errorMessage, Exception exception) {
        return new Error(correlationId, errorMessage, exception);
    }

    static ProcessingResult<ErrorDataDto> error(String correlationId, String errorMessage, String rawMessage, Exception exception) {
        return new Error(correlationId, errorMessage, rawMessage, exception);
    }

    static <T> ProcessingResult<T> warning(String correlationId, String rawMessage, T data, String warningMessage) {
        return new Warning<>(correlationId, rawMessage, data, warningMessage);
    }

    record Ignored(
            String correlationId,
            String reason,
            Instant processedAt
    ) implements ProcessingResult<Void> {

        public Ignored(String correlationId, String reason) {
            this(correlationId, reason, Instant.now());
        }

        @Override public boolean isSuccess() { return false; }
        @Override public boolean isError() { return false; }
        @Override public boolean isWarning() { return false; }
        @Override public Optional<Void> getData() { return Optional.empty(); }
        @Override public Optional<String> getErrorMessage() { return Optional.of(reason); }
        @Override public Optional<String> getRawMessage() { return Optional.empty(); }
        @Override public Optional<Exception> getException() { return Optional.empty(); }
    }

    @SuppressWarnings("unchecked")
    static <T> ProcessingResult<T> ignored(String correlationId, String reason) {
        return (ProcessingResult<T>) new Ignored(correlationId, reason);
    }
}