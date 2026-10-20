package com.marmitt.application.spring.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Comandos do operador pela API (T55): toda requisicao que muda estado exige o cabecalho
 * {@value #REASON} e fica no {@link OperatorActionLog}, com o resultado. Um caminho so para todos os
 * comandos — nenhum controller precisa lembrar de auditar.
 *
 * <ul>
 *   <li>{@value #REASON} — motivo da acao (obrigatorio em POST/PUT/PATCH/DELETE); sem ele, {@code 400}
 *       com {@code code=REASON_REQUIRED}, sem executar o comando.</li>
 *   <li>{@value #OPERATOR} — quem pediu (opcional; ex.: o nome do agente). Sem ele, {@code unknown}.</li>
 * </ul>
 * Roda depois da checagem de {@code X-Admin-Key}: requisicao sem chave nao chega aqui.
 */
@Component
public class OperatorCommandInterceptor implements HandlerInterceptor {

    public static final String REASON = "X-Reason";
    public static final String OPERATOR = "X-Operator";
    /** Atributo de requisicao onde o {@link GlobalExceptionHandler} deixa o codigo da recusa. */
    public static final String ERROR_CODE_ATTRIBUTE = OperatorCommandInterceptor.class.getName() + ".errorCode";

    private static final String STARTED_AT = OperatorCommandInterceptor.class.getName() + ".startedAt";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final OperatorActionLog actionLog;
    private final ObjectMapper objectMapper;

    public OperatorCommandInterceptor(OperatorActionLog actionLog, ObjectMapper objectMapper) {
        this.actionLog = actionLog;
        this.objectMapper = objectMapper;
    }

    public static boolean isCommand(HttpServletRequest request) {
        return !READ_METHODS.contains(request.getMethod());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!isCommand(request)) {
            return true;
        }
        request.setAttribute(STARTED_AT, System.nanoTime());
        String reason = request.getHeader(REASON);
        if (reason == null || reason.isBlank()) {
            ApiError body = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "Header " + REASON + " is required for every command (POST, PUT, PATCH, DELETE)",
                    request.getRequestURI(), "REASON_REQUIRED");
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            record(request, handler, HttpStatus.BAD_REQUEST.value(), "REASON_REQUIRED");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        if (!isCommand(request)) {
            return;
        }
        record(request, handler, response.getStatus(), (String) request.getAttribute(ERROR_CODE_ATTRIBUTE));
    }

    private void record(HttpServletRequest request, Object handler, int status, String errorCode) {
        Object startedAt = request.getAttribute(STARTED_AT);
        long durationMs = startedAt instanceof Long start ? (System.nanoTime() - start) / 1_000_000 : 0L;
        String actor = request.getHeader(OPERATOR);
        String reason = request.getHeader(REASON);
        String query = request.getQueryString();
        actionLog.record(new OperatorActionLog.OperatorAction(
                UUID.randomUUID(),
                actor == null || actor.isBlank() ? "unknown" : actor.strip(),
                request.getMethod(),
                request.getRequestURI() + (query == null ? "" : "?" + query),
                handler instanceof HandlerMethod method
                        ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName()
                        : null,
                reason == null || reason.isBlank() ? null : reason.strip(),
                status,
                errorCode,
                durationMs,
                Instant.now()));
    }
}
