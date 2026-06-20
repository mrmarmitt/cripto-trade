package com.marmitt.core.ports.outbound.notification;

import com.marmitt.core.dto.notification.ErrorNotificationEvent;

/**
 * Porta de saída para reportar ativamente erros terminais ao operador.
 *
 * <p>Implementações (ex.: webhook Discord) devem ser <b>tolerantes a falha</b>: nunca
 * propagar exceção nem bloquear o fluxo principal — a notificação é colateral ao
 * tratamento do erro, não parte dele.
 */
public interface ErrorNotificationPort {

    void notifyError(ErrorNotificationEvent event);
}
