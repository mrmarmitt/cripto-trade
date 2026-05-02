package com.marmitt.binance.userdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import com.marmitt.binance.http.HttpClientPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class ListenKeyManager implements ListenKeyPort {

    private static final String USER_DATA_STREAM_PATH = "/api/v3/userDataStream";

    private final String restBaseUrl;
    private final BinanceCredentials credentials;
    private final HttpClientPort httpClient;
    private final ObjectMapper objectMapper;

    private volatile String listenKey;

    public ListenKeyManager(String restBaseUrl,
                            BinanceCredentials credentials,
                            HttpClientPort httpClient,
                            ObjectMapper objectMapper) {
        this.restBaseUrl = restBaseUrl;
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String obtainListenKey() throws IOException {
        Map<String, String> headers = Map.of("X-MBX-APIKEY", credentials.getApiKey());
        HttpClientPort.HttpResponse response = httpClient.post(restBaseUrl + USER_DATA_STREAM_PATH, headers);

        if (!response.isSuccessful()) {
            throw new IOException("Failed to obtain listen key: HTTP " + response.statusCode());
        }

        listenKey = objectMapper.readTree(response.body()).path("listenKey").asText();
        log.info("Listen key obtained for Binance user data stream");
        return listenKey;
    }

    @Override
    public void keepAlive() {
        if (listenKey == null) {
            log.warn("Cannot keepalive: no active listen key");
            return;
        }
        try {
            Map<String, String> headers = Map.of("X-MBX-APIKEY", credentials.getApiKey());
            HttpClientPort.HttpResponse response = httpClient.put(
                    restBaseUrl + USER_DATA_STREAM_PATH + "?listenKey=" + listenKey, headers);

            if (response.isSuccessful()) {
                log.debug("Listen key keepalive sent successfully");
            } else {
                log.warn("Listen key keepalive failed: HTTP {}", response.statusCode());
            }
        } catch (IOException e) {
            log.error("Error sending listen key keepalive", e);
        }
    }

    @Override
    public void revoke() {
        if (listenKey == null) {
            return;
        }
        try {
            Map<String, String> headers = Map.of("X-MBX-APIKEY", credentials.getApiKey());
            HttpClientPort.HttpResponse response = httpClient.delete(
                    restBaseUrl + USER_DATA_STREAM_PATH + "?listenKey=" + listenKey, headers);

            if (response.isSuccessful()) {
                log.info("Listen key revoked for Binance user data stream");
            } else {
                log.warn("Listen key revoke failed: HTTP {}", response.statusCode());
            }
        } catch (IOException e) {
            log.error("Error revoking listen key", e);
        } finally {
            listenKey = null;
        }
    }

    @Override
    public String getListenKey() {
        return listenKey;
    }
}
