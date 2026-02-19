package com.marmitt.core.ports.outbound.exchange;

import com.marmitt.core.dto.runner.OrderAck;
import com.marmitt.core.dto.runner.OrderDispatchCommand;

/**
 * Porta de saída para envio de ordens a uma exchange.
 * <p>
 * Cada exchange tem sua própria implementação (Binance, Mock, etc.).
 * A implementação deve:
 * <ul>
 *   <li>Enviar a ordem para a exchange usando o protocolo adequado (REST/WebSocket)</li>
 *   <li>Aguardar confirmação síncrona dentro do SLA configurado</li>
 *   <li>Retornar {@link OrderAck#accepted(String)} se a exchange atribuiu um ID</li>
 *   <li>Retornar {@link OrderAck#rejected(String)} se a exchange recusou explicitamente</li>
 *   <li>Retornar {@link OrderAck#timeout()} se o SLA expirou sem resposta</li>
 * </ul>
 * <p>
 * <b>Contrato de exceções:</b> o adapter NUNCA deve lançar exceção por rejeição ou timeout
 * — o {@link OrderAck} é o contrato. Exceções técnicas (falha de conexão, serialização)
 * podem ser propagadas e serão tratadas pelo chamador.
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 6.2.1, 6.3.2</a>
 */
public interface OrderDispatchPort {

    /**
     * Despacha uma ordem para a exchange e aguarda confirmação síncrona.
     *
     * @param command dados da ordem a enviar
     * @return {@link OrderAck} com o resultado do dispatch
     */
    OrderAck dispatch(OrderDispatchCommand command);
}
