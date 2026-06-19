package com.marmitt.application.spring.web;

import com.marmitt.core.exceptions.DeadLetterConflictException;
import com.marmitt.core.exceptions.DeadLetterNotFoundException;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Handler global de exceções: centraliza o mapeamento exceção→status e monta o
 * {@link ApiError}. Controllers não decidem status de erro nem montam corpo de erro.
 *
 * <table>
 *   <tr><td>IllegalArgumentException / validação</td><td>400</td></tr>
 *   <tr><td>RunnerNotFoundException / DeadLetterNotFoundException</td><td>404</td></tr>
 *   <tr><td>DeadLetterConflictException</td><td>409</td></tr>
 *   <tr><td>ExchangeQueryException</td><td>502</td></tr>
 *   <tr><td>Exception (fallback)</td><td>500</td></tr>
 * </table>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> handleValidation(Exception ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler({RunnerNotFoundException.class, DeadLetterNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(DeadLetterConflictException.class)
    public ResponseEntity<ApiError> handleConflict(DeadLetterConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(ExchangeQueryException.class)
    public ResponseEntity<ApiError> handleExchangeQuery(ExchangeQueryException ex, HttpServletRequest request) {
        log.warn("exchange query failed path={} reason={}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "Exchange query failed: " + ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unexpected error path={}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
