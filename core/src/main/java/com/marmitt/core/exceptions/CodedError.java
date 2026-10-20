package com.marmitt.core.exceptions;

/**
 * Recusa de negocio com codigo estavel (T55). O codigo e o contrato com quem opera pela API —
 * inclusive agentes —, que reage a ele sem interpretar a mensagem. Uma vez publicado, um codigo nao
 * muda de significado.
 */
public interface CodedError {

    String code();
}
