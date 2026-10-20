package com.marmitt.binance.processor.receive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.websocket.MessageContext;
import com.marmitt.core.dto.websocket.data.OrderDataDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * T46: o executionReport carrega o ativo da comissao (N) ate o core.
 */
class ExecutionReportProcessorFeeTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutionReportProcessor processor = new ExecutionReportProcessor(objectMapper);

    @Test
    void mapsCommissionAmountAndAsset() throws Exception {
        OrderDataDto dto = process("""
                {"e":"executionReport","s":"BTCUSDT","c":"cid","S":"BUY","o":"LIMIT","X":"FILLED",
                 "i":1,"q":"0.00100000","z":"0.00100000","Z":"66.23370000","p":"66300.00000000",
                 "n":"0.00000100","N":"BTC","T":1700000000000}
                """);

        assertEquals(0, dto.fee().compareTo(new BigDecimal("0.00000100")));
        assertEquals("BTC", dto.feeAsset());
    }

    @Test
    void eventWithoutTradeHasNoFeeAsset() throws Exception {
        OrderDataDto dto = process("""
                {"e":"executionReport","s":"BTCUSDT","c":"cid","S":"BUY","o":"LIMIT","X":"NEW",
                 "i":1,"q":"0.00100000","z":"0","Z":"0","p":"66300.00000000",
                 "n":"0","N":null,"T":1700000000000}
                """);

        assertNull(dto.feeAsset());
    }

    @Test
    void cancellationIsRoutedByTheOriginalClientOrderId() throws Exception {
        OrderDataDto dto = process("""
                {"e":"executionReport","s":"BTCUSDT","c":"web_cancel_request_id","C":"v1r3895t1792527338568s001B_d7a68480e",
                 "S":"BUY","o":"LIMIT","X":"CANCELED","i":5175888,"q":"0.00100000","z":"0","Z":"0",
                 "p":"78519.54000000","n":"0","N":null,"T":1700000000000}
                """);

        assertEquals("v1r3895t1792527338568s001B_d7a68480e", dto.clientOrderId(),
                "no cancelamento o id da ordem original vem em C; c e o id do pedido de cancelamento");
    }

    @Test
    void ordinaryEventsKeepTheClientOrderIdFromC() throws Exception {
        OrderDataDto dto = process("""
                {"e":"executionReport","s":"BTCUSDT","c":"cid","C":"","S":"BUY","o":"LIMIT","X":"NEW",
                 "i":1,"q":"0.00100000","z":"0","Z":"0","p":"66300.00000000","n":"0","N":null,"T":1700000000000}
                """);

        assertEquals("cid", dto.clientOrderId());
    }

    private OrderDataDto process(String json) throws Exception {
        return (OrderDataDto) processor.process(objectMapper.readTree(json),
                        MessageContext.createUserData("BINANCE", UUID.randomUUID()))
                .getData().orElseThrow();
    }
}
