package com.marmitt.binance.rest;

import java.io.IOException;
import java.util.Map;

public interface HttpClientPort {

    HttpResponse get(String url, Map<String, String> headers) throws IOException;

    HttpResponse post(String url, Map<String, String> headers) throws IOException;

    HttpResponse postForm(String url, Map<String, String> headers, String body) throws IOException;

    HttpResponse put(String url, Map<String, String> headers) throws IOException;

    HttpResponse delete(String url, Map<String, String> headers) throws IOException;

    record HttpResponse(int statusCode, String body) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
