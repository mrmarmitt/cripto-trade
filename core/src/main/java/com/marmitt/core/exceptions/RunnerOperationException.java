package com.marmitt.core.exceptions;

/**
 * Operação de ciclo de vida ou comando de runner recusado pelo estado atual (T49): transição
 * inválida, exchange indisponível, reconciliação incompleta, alvo que não pertence ao runner.
 * Mapeada para HTTP 409 na borda.
 *
 * <p>Estende {@link IllegalStateException}: é a mesma classe de erro que as transições do
 * {@code StrategyRunner} sempre lançaram, agora com tipo próprio para a API.
 */
public class RunnerOperationException extends IllegalStateException {

    public RunnerOperationException(String message) {
        super(message);
    }
}
