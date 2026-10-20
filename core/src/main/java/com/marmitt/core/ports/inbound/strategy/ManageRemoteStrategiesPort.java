package com.marmitt.core.ports.inbound.strategy;

import com.marmitt.core.domain.strategy.RemoteStrategyEvent;
import com.marmitt.core.dto.strategy.request.RegisterRemoteStrategyRequest;
import com.marmitt.core.dto.strategy.request.UpdateRemoteStrategyRequest;
import com.marmitt.core.dto.strategy.response.RemoteStrategyView;

import java.util.List;
import java.util.UUID;

/**
 * Administracao do catalogo de estrategias remotas (T54). Toda mudanca exige {@code reason}, fica no
 * historico e devolve o estado resultante; repetir uma mudanca ja aplicada nao falha nem gera evento.
 * Recusas sao {@link com.marmitt.core.exceptions.StrategyCatalogException} com codigo estavel.
 */
public interface ManageRemoteStrategiesPort {

    /**
     * Cadastra e confere a estrategia na hora. Servico fora do ar nao impede o cadastro: fica
     * {@code FAILED} e a conferencia e repetida periodicamente.
     */
    RemoteStrategyView register(RegisterRemoteStrategyRequest request, String reason);

    /**
     * Troca endereco e/ou token e confere de novo.
     */
    RemoteStrategyView update(UUID strategyRef, UpdateRemoteStrategyRequest request, String reason);

    RemoteStrategyView enable(UUID strategyRef, String reason);

    /**
     * Tira a estrategia de uso; os runners que apontam para ela ficam em HOLD (a protecao do lote segue
     * valendo). Nada e parado a forca.
     */
    RemoteStrategyView disable(UUID strategyRef, String reason);

    List<RemoteStrategyView> list();

    RemoteStrategyView get(UUID strategyRef);

    List<RemoteStrategyEvent> events(UUID strategyRef);
}
