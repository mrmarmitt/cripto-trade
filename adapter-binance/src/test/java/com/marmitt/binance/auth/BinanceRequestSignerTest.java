package com.marmitt.binance.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BinanceRequestSignerTest {

    // Test vectors from Binance API documentation
    // https://developers.binance.com/docs/binance-spot-api-docs/rest-api/endpoint-security-type
    private static final String TEST_API_KEY = "vmPUZE6mv9SD5VNHk4HlWFsOr6aKE2zvsw0MuIgwCIPy6utIco14y7Ju91duEh8A";
    private static final String TEST_API_SECRET = "NhqRiosBVtrqZnU1l0Stgstrse3XQn67pvwoQpttj7I=";

    private BinanceRequestSigner signer;

    @BeforeEach
    void setUp() {
        signer = new BinanceRequestSigner(new BinanceCredentials(TEST_API_KEY, TEST_API_SECRET));
    }

    @Test
    void signQueryString_matchesBinanceDocumentationVector() {
        // Fixed vector from Binance REST API documentation
        String queryString = "symbol=LTCBTC&side=BUY&type=LIMIT&timeInForce=GTC" +
                             "&quantity=1&price=0.1&recvWindow=5000&timestamp=1499827319559";

        String signed = signer.signQueryString(queryString);

        assertTrue(signed.endsWith("&signature=25c4b890074eecf05582cb87073da81a2bd85b2d4ae7784bd4faa002e7aae8c3"),
                "Signature must match Binance documentation test vector");
    }

    @Test
    void signWebSocketParams_containsRequiredFields() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", "BTCUSDT");
        params.put("side", "BUY");
        params.put("type", "LIMIT");
        params.put("quantity", "1.00");
        params.put("price", "30000.00");
        params.put("timeInForce", "GTC");
        params.put("newClientOrderId", "my-order-001");

        Map<String, Object> signed = signer.signWebSocketParams(params);

        assertEquals(TEST_API_KEY, signed.get("apiKey"), "apiKey must be present in signed params");
        assertNotNull(signed.get("timestamp"), "timestamp must be present in signed params");
        assertNotNull(signed.get("signature"), "signature must be present in signed params");
        assertFalse(signed.get("signature").toString().isBlank(), "signature must not be blank");
    }

    @Test
    void signWebSocketParams_signatureIsValidHmac() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", "BTCUSDT");
        params.put("side", "BUY");
        params.put("type", "MARKET");
        params.put("quantity", "0.5");
        params.put("newClientOrderId", "test-order");

        Map<String, Object> signed = signer.signWebSocketParams(params);

        // Signature must be a 64-character hex string (32 bytes HMAC-SHA256)
        String signature = signed.get("signature").toString();
        assertEquals(64, signature.length(), "HMAC-SHA256 hex signature must be 64 characters");
        assertTrue(signature.matches("[0-9a-f]+"), "Signature must be lowercase hex");
    }

    @Test
    void signWebSocketParams_doesNotMutateOriginalParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("symbol", "BTCUSDT");
        params.put("quantity", "1.00");

        int originalSize = params.size();
        signer.signWebSocketParams(params);

        assertEquals(originalSize, params.size(), "Original params map must not be mutated");
        assertNull(params.get("signature"), "Original params must not contain signature");
        assertNull(params.get("apiKey"), "Original params must not contain apiKey");
    }

    @Test
    void signWebSocketParams_timestampDiffersBetweenConsecutiveCalls() throws InterruptedException {
        Map<String, Object> params = Map.of("symbol", "BTCUSDT", "side", "BUY");

        Map<String, Object> first = signer.signWebSocketParams(params);
        Thread.sleep(2);
        Map<String, Object> second = signer.signWebSocketParams(params);

        assertNotEquals(first.get("timestamp"), second.get("timestamp"),
                "Timestamp must not be reused between calls");
    }

    @Test
    void credentials_rejectsBlankApiKey() {
        assertThrows(IllegalArgumentException.class, () -> new BinanceCredentials("", "secret"));
    }

    @Test
    void credentials_rejectsBlankApiSecret() {
        assertThrows(IllegalArgumentException.class, () -> new BinanceCredentials("key", ""));
    }

    @Test
    void getApiKey_returnsConfiguredKey() {
        assertEquals(TEST_API_KEY, signer.getApiKey());
    }
}
