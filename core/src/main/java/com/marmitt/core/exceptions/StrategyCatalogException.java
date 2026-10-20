package com.marmitt.core.exceptions;

import java.util.Objects;

/**
 * Recusa de uma operacao no catalogo de estrategias remotas (T54), com codigo estavel para quem opera
 * pela API (inclusive agentes) reagir sem interpretar a mensagem.
 */
public class StrategyCatalogException extends RuntimeException implements CodedError {

    private final Code code;

    public StrategyCatalogException(Code code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code cannot be null");
    }

    public Code catalogCode() {
        return code;
    }

    @Override
    public String code() {
        return code.name();
    }

    /**
     * Codigos de recusa. {@link Kind} diz a natureza da recusa; a camada HTTP traduz em status.
     */
    public enum Code {
        /** Nao ha estrategia com esse {@code strategyRef} no catalogo. */
        STRATEGY_NOT_FOUND(Kind.NOT_FOUND),
        /** Ja existe cadastro com esse {@code strategyRef} e dados diferentes (nome, versao, endereco ou token). */
        STRATEGY_DEFINITION_CONFLICT(Kind.CONFLICT),
        /** O {@code strategyRef} ja e usado por uma estrategia interna do ctrade. */
        STRATEGY_REF_IN_USE(Kind.CONFLICT),
        /** Campo ausente ou invalido (ex.: URL que nao e http(s) absoluta). */
        INVALID_STRATEGY_DEFINITION(Kind.INVALID),
        /** Toda mudanca exige {@code reason}. */
        REASON_REQUIRED(Kind.INVALID);

        private final Kind kind;

        Code(Kind kind) {
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }

    public enum Kind {
        NOT_FOUND,
        CONFLICT,
        INVALID
    }
}
