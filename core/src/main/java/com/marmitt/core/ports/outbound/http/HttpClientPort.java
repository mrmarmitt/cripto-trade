package com.marmitt.core.ports.outbound.http;

import java.io.IOException;
import java.util.Map;

public interface HttpClientPort {

    HttpResponse post(String url, Map<String, String> headers) throws IOException;

    HttpResponse put(String url, Map<String, String> headers) throws IOException;

    HttpResponse delete(String url, Map<String, String> headers) throws IOException;

    record HttpResponse(int statusCode, String body) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
