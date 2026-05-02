package com.marmitt.application.spring.adapter.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.binance.auth.BinanceCredentials;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;

@Slf4j
public class ListenKeyManager {

    private static final String USER_DATA_STREAM_PATH = "/api/v3/userDataStream";
    private static final MediaType EMPTY_BODY = MediaType.parse("application/x-www-form-urlencoded");

    private final String restBaseUrl;
    private final BinanceCredentials credentials;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private volatile String listenKey;

    public ListenKeyManager(String restBaseUrl,
                            BinanceCredentials credentials,
                            OkHttpClient httpClient,
                            ObjectMapper objectMapper) {
        this.restBaseUrl = restBaseUrl;
        this.credentials = credentials;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public String obtainListenKey() throws IOException {
        Request request = new Request.Builder()
                .url(restBaseUrl + USER_DATA_STREAM_PATH)
                .addHeader("X-MBX-APIKEY", credentials.getApiKey())
                .post(RequestBody.create("", EMPTY_BODY))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to obtain listen key: HTTP " + response.code());
            }
            String body = response.body().string();
            JsonNode json = objectMapper.readTree(body);
            listenKey = json.path("listenKey").asText();
            log.info("Listen key obtained for Binance user data stream");
            return listenKey;
        }
    }

    public void keepAlive() {
        if (listenKey == null) {
            log.warn("Cannot keepalive: no active listen key");
            return;
        }
        Request request = new Request.Builder()
                .url(restBaseUrl + USER_DATA_STREAM_PATH + "?listenKey=" + listenKey)
                .addHeader("X-MBX-APIKEY", credentials.getApiKey())
                .put(RequestBody.create("", EMPTY_BODY))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                log.debug("Listen key keepalive sent successfully");
            } else {
                log.warn("Listen key keepalive failed: HTTP {}", response.code());
            }
        } catch (IOException e) {
            log.error("Error sending listen key keepalive", e);
        }
    }

    public void revoke() {
        if (listenKey == null) {
            return;
        }
        Request request = new Request.Builder()
                .url(restBaseUrl + USER_DATA_STREAM_PATH + "?listenKey=" + listenKey)
                .addHeader("X-MBX-APIKEY", credentials.getApiKey())
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                log.info("Listen key revoked for Binance user data stream");
            } else {
                log.warn("Listen key revoke failed: HTTP {}", response.code());
            }
        } catch (IOException e) {
            log.error("Error revoking listen key", e);
        } finally {
            listenKey = null;
        }
    }

    public String getListenKey() {
        return listenKey;
    }
}
