package com.marmitt.strategyservice;

import java.io.IOException;
import java.util.Map;

/**
 * Transporte HTTP usado pelo cliente do strategy service. Implementado pela camada de composição
 * (mesmo padrão do {@code HttpClientPort} do adapter Binance), que também aplica o timeout rígido da
 * chamada. Falha de rede ou timeout = {@link IOException}.
 */
public interface StrategyServiceHttpPort {

    Response get(String url, Map<String, String> headers) throws IOException;

    Response postJson(String url, Map<String, String> headers, String body) throws IOException;

    record Response(int statusCode, String body) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
