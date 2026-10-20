package com.marmitt.application.spring.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Envelope único de erro HTTP da aplicação.
 *
 * <p>Toda resposta de erro retorna {@code ApiError} — nunca {@code String} crua nem o DTO
 * de resultado de um caso de uso. Não expõe stacktrace nem detalhe interno.
 *
 * @param timestamp momento da resposta
 * @param status    código HTTP
 * @param error     reason phrase (ex.: "Bad Request")
 * @param message   mensagem amigável
 * @param path      caminho da requisição que originou o erro
 * @param code      código estável legível por máquina (ex.: {@code STRATEGY_NOT_FOUND}), para quem opera
 *                  pela API — inclusive agentes — reagir sem interpretar a mensagem. Omitido nos erros
 *                  que ainda não têm código.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {
}
