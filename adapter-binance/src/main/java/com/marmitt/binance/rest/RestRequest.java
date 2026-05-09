package com.marmitt.binance.rest;

import java.util.Map;

public record RestRequest(String method, String url, Map<String, String> headers, String body) {}
