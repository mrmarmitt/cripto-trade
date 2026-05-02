package com.marmitt.application.spring.adapter.binance;

import com.marmitt.binance.http.HttpClientPort;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Map;

public class OkHttpClientAdapter implements HttpClientPort {

    private static final MediaType EMPTY_BODY = MediaType.parse("application/x-www-form-urlencoded");

    private final OkHttpClient client;

    public OkHttpClientAdapter(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public HttpResponse post(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", EMPTY_BODY));
        headers.forEach(builder::addHeader);
        return execute(builder.build());
    }

    @Override
    public HttpResponse put(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .put(RequestBody.create("", EMPTY_BODY));
        headers.forEach(builder::addHeader);
        return execute(builder.build());
    }

    @Override
    public HttpResponse delete(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .delete();
        headers.forEach(builder::addHeader);
        return execute(builder.build());
    }

    private HttpResponse execute(Request request) throws IOException {
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            return new HttpResponse(response.code(), body);
        }
    }
}
