package com.marmitt.core.ports.outbound.exchange;

import com.marmitt.core.dto.runner.OrderDispatchCommand;

/**
 * Porta de saída para envio de ordens a uma exchange.
 * <p>
 * Fire-and-forget: envia a ordem e retorna imediatamente.
 * A confirmação (SUBMITTED) e a rejeição (REJECTED) chegam de forma assíncrona
 * via pipeline de mensagens WebSocket → {@code OrderConciliationUseCase}.
 * <p>
 * <b>Contrato de exceções:</b> lança exceção apenas em falhas técnicas de envio
 * (WebSocket desconectado, falha de serialização). A transação permanece {@code PENDING}
 * e o Boot Sequence reconcilia o estado via {@code clientOrderId}.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1, 6.3</a>
 */
public interface OrderDispatchPort {

    /**
     * Despacha uma ordem para a exchange via WebSocket.
     *
     * @param command dados da ordem a enviar
     * @throws IllegalStateException se o adapter da exchange não for encontrado
     * @throws RuntimeException      se o envio falhar por razão técnica
     */
    void dispatch(OrderDispatchCommand command);
}
