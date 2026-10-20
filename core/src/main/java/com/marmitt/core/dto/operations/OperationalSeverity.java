package com.marmitt.core.dto.operations;

/**
 * Severidade de uma verificacao do estado operacional (T55). A ordem importa: o status geral e a maior
 * severidade entre as verificacoes.
 */
public enum OperationalSeverity {
    OK,
    WARN,
    CRITICAL
}
