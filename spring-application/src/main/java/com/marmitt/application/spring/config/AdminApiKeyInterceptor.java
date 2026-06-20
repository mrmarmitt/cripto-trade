package com.marmitt.application.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.application.spring.web.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

    private final String adminApiKey;
    private final ObjectMapper objectMapper;

    public AdminApiKeyInterceptor(@Value("${admin.api.key:}") String adminApiKey, ObjectMapper objectMapper) {
        this.adminApiKey = adminApiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!adminApiKey.isBlank() && adminApiKey.equals(request.getHeader("X-Admin-Key"))) {
            return true;
        }
        writeUnauthorized(request, response);
        return false;
    }

    /**
     * Escreve o mesmo envelope {@link ApiError} usado pelo {@code GlobalExceptionHandler},
     * já que o interceptor barra a requisição antes de qualquer controller/advice rodar.
     */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws Exception {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Missing or invalid X-Admin-Key header",
                request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
