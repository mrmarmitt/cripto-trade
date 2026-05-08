package com.marmitt.binance.rest;

import com.marmitt.binance.auth.BinanceRequestSigner;
import com.marmitt.core.exceptions.ExchangeQueryException;
import com.marmitt.core.ports.outbound.http.HttpClientPort;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

@Slf4j
public class BinanceRestClient {

    private static final String EXCHANGE_ID = "BINANCE";
    private static final Map<String, String> API_KEY_HEADER_TEMPLATE = Map.of();

    private final String restBaseUrl;
    private final BinanceRequestSigner signer;
    private final HttpClientPort httpClient;

    public BinanceRestClient(String restBaseUrl, BinanceRequestSigner signer, HttpClientPort httpClient) {
        this.restBaseUrl = restBaseUrl;
        this.signer = signer;
        this.httpClient = httpClient;
    }

    public String getJson(String path, Map<String, String> params) {
        String query = buildSignedQuery(params);
        String url = restBaseUrl + path + "?" + query;
        Map<String, String> headers = apiKeyHeader();
        try {
            HttpClientPort.HttpResponse response = httpClient.get(url, headers);
            return handleResponse(response, "GET", path);
        } catch (ExchangeQueryException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Network error on GET " + path + ": " + e.getMessage(), e);
        }
    }

    public String postForm(String path, Map<String, String> params) {
        String query = buildSignedQuery(params);
        String url = restBaseUrl + path;
        Map<String, String> headers = apiKeyHeader();
        try {
            HttpClientPort.HttpResponse response = httpClient.postForm(url, headers, query);
            return handleResponse(response, "POST", path);
        } catch (ExchangeQueryException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Network error on POST " + path + ": " + e.getMessage(), e);
        }
    }

    public String deleteQuery(String path, Map<String, String> params) {
        String query = buildSignedQuery(params);
        String url = restBaseUrl + path + "?" + query;
        Map<String, String> headers = apiKeyHeader();
        try {
            HttpClientPort.HttpResponse response = httpClient.delete(url, headers);
            return handleResponse(response, "DELETE", path);
        } catch (ExchangeQueryException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Network error on DELETE " + path + ": " + e.getMessage(), e);
        }
    }

    public boolean isNotFound(String path, Map<String, String> params) {
        String query = buildSignedQuery(params);
        String url = restBaseUrl + path + "?" + query;
        try {
            HttpClientPort.HttpResponse response = httpClient.get(url, apiKeyHeader());
            if (response.statusCode() == 404) {
                return true;
            }
            handleResponse(response, "GET", path);
            return false;
        } catch (ExchangeQueryException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Network error on GET " + path + ": " + e.getMessage(), e);
        }
    }

    public String getJsonOrNull(String path, Map<String, String> params) {
        String query = buildSignedQuery(params);
        String url = restBaseUrl + path + "?" + query;
        try {
            HttpClientPort.HttpResponse response = httpClient.get(url, apiKeyHeader());
            if (response.statusCode() == 404) {
                return null;
            }
            // Binance returns 400 with code -2013 for missing/archived orders
            if (response.statusCode() == 400 && isBinanceOrderNotFound(response.body())) {
                log.debug("Binance order not found (-2013) on GET {}", path);
                return null;
            }
            return handleResponse(response, "GET", path);
        } catch (ExchangeQueryException e) {
            throw e;
        } catch (IOException e) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    "Network error on GET " + path + ": " + e.getMessage(), e);
        }
    }

    private boolean isBinanceOrderNotFound(String body) {
        // Fast path: check for the literal error code without JSON parsing
        return body != null && body.contains("-2013");
    }

    private String handleResponse(HttpClientPort.HttpResponse response, String method, String path) {
        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            return response.body();
        }
        if (code == 429 || code == 418) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.RATE_LIMIT,
                    method + " " + path + " rate-limited (HTTP " + code + "): " + response.body());
        }
        if (code == 401 || code == 403) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.AUTH,
                    method + " " + path + " auth error (HTTP " + code + "): " + response.body());
        }
        if (code == 400) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.INVALID_REQUEST,
                    method + " " + path + " bad request (HTTP 400): " + response.body());
        }
        if (code >= 500) {
            throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.TEMPORARY,
                    method + " " + path + " server error (HTTP " + code + "): " + response.body());
        }
        throw new ExchangeQueryException(EXCHANGE_ID, ExchangeQueryException.ErrorType.UNKNOWN,
                method + " " + path + " unexpected status (HTTP " + code + "): " + response.body());
    }

    private String buildSignedQuery(Map<String, String> params) {
        StringJoiner sj = new StringJoiner("&");
        params.forEach((k, v) -> sj.add(k + "=" + v));
        sj.add("timestamp=" + System.currentTimeMillis());
        return signer.signQueryString(sj.toString());
    }

    private Map<String, String> apiKeyHeader() {
        return Map.of("X-MBX-APIKEY", signer.getApiKey());
    }
}
