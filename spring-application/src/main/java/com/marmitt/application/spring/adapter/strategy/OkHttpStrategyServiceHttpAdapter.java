package com.marmitt.application.spring.adapter.strategy;

import com.marmitt.strategyservice.StrategyServiceHttpPort;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

/**
 * Transporte OkHttp do cliente de estratégias remotas (T41). O {@link OkHttpClient} recebido já traz
 * o {@code callTimeout} rígido; estourar o tempo vira {@link IOException}.
 */
public class OkHttpStrategyServiceHttpAdapter implements StrategyServiceHttpPort {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient client;

    public OkHttpStrategyServiceHttpAdapter(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Response get(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        headers.forEach(builder::addHeader);
        return execute(builder.build());
    }

    @Override
    public Response postJson(String url, Map<String, String> headers, String body) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).post(RequestBody.create(body, JSON));
        headers.forEach(builder::addHeader);
        return execute(builder.build());
    }

    private Response execute(Request request) throws IOException {
        try (okhttp3.Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            return new Response(response.code(), body);
        }
    }
}
