package com.marmitt.strategyservice.reference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.ports.outbound.strategy.TradingStrategy;
import com.marmitt.strategy.impl.simple_moving_avager.SimpleMovingAverageStrategy;
import com.marmitt.strategyservice.wire.StrategyContractMapper;
import com.marmitt.strategyservice.wire.WireTypes;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * App de referência do contrato do strategy service (T41, contrato 1.1). Serve uma
 * {@link TradingStrategy} in-process — por padrão o SMA — por HTTP, sob um {@code strategyRef} próprio.
 *
 * <ul>
 *   <li>Warm-up por {@code (strategyRef, runnerId)}: o SMA já guarda histórico por runner.</li>
 *   <li>Mesma {@code tickId} do mesmo runner devolve a decisão em cache (sem recalcular).</li>
 *   <li>{@code sequence} menor ou igual ao último aceito do runner, com {@code tickId} nova, não é
 *       avaliado (o SMA não sabe avaliar sem mexer no warm-up): responde HOLD.</li>
 *   <li>Readiness: o SMA expõe o aquecimento pelo HOLD "Aguardando historico"; aqui {@code ready} é
 *       sempre {@code true}.</li>
 * </ul>
 * Não faz parte do classpath do ctrade: sobe como processo separado ({@code gradle :strategy-service-reference:run}).
 */
@Slf4j
public class ReferenceStrategyServer {

    public static final String CONTRACT_VERSION = "1.1.0";
    public static final UUID DEFAULT_STRATEGY_REF = UUID.fromString("5ea5e0a0-0000-4000-a000-000000000001");

    private final UUID strategyRef;
    private final TradingStrategy strategy;
    private final String token;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, RunnerState> stateByRunner = new ConcurrentHashMap<>();
    private HttpServer server;

    public ReferenceStrategyServer(UUID strategyRef, TradingStrategy strategy, String token) {
        this.strategyRef = strategyRef;
        this.strategy = strategy;
        this.token = token;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8090"));
        UUID ref = UUID.fromString(System.getenv().getOrDefault("STRATEGY_REF", DEFAULT_STRATEGY_REF.toString()));
        ReferenceStrategyServer server = new ReferenceStrategyServer(ref, new SimpleMovingAverageStrategy(),
                System.getenv("STRATEGY_TOKEN"));
        server.start(port);
        log.info("Reference strategy service on port {} serving {} as {}", server.port(), "SMA", ref);
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/v1/evaluate", exchange -> handle(exchange, this::evaluate));
        server.createContext("/v1/meta", exchange -> handle(exchange, this::meta));
        server.createContext("/v1/health", exchange -> handle(exchange, this::health));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    // ---------------------------------------------------------------- endpoints

    private Reply evaluate(HttpExchange exchange) throws IOException {
        WireTypes.EvaluateRequest request = objectMapper.readValue(exchange.getRequestBody(), WireTypes.EvaluateRequest.class);
        if (!strategyRef.toString().equalsIgnoreCase(request.strategyRef())) {
            return new Reply(404, new WireTypes.Error("strategyRef not served: " + request.strategyRef()));
        }
        StrategyContextDto context = StrategyContractMapper.toCore(request.context());
        RunnerState state = stateByRunner.computeIfAbsent(context.runnerId(), id -> new RunnerState());
        synchronized (state) {
            WireTypes.Decision cached = state.decisionsByTickId.get(request.tickId());
            if (cached != null) {
                return new Reply(200, cached);
            }
            WireTypes.Decision decision;
            if (request.sequence() <= state.lastSequence) {
                decision = StrategyContractMapper.toWire(StrategyOutputDto.hold(strategy.getStrategyName(),
                        "out of sequence: " + request.sequence() + " <= " + state.lastSequence));
            } else {
                state.lastSequence = request.sequence();
                decision = StrategyContractMapper.toWire(
                        strategy.executeStrategy(StrategyContractMapper.toCore(request.input()), context));
            }
            state.remember(request.tickId(), decision);
            return new Reply(200, decision);
        }
    }

    private Reply meta(HttpExchange exchange) {
        String filter = query(exchange.getRequestURI(), "strategyRef");
        if (filter != null && !strategyRef.toString().equalsIgnoreCase(filter)) {
            return new Reply(404, new WireTypes.Error("strategyRef not served: " + filter));
        }
        return new Reply(200, List.of(new WireTypes.Meta(strategyRef.toString(), strategy.getStrategyName(),
                strategy.getStrategyVersion(), CONTRACT_VERSION)));
    }

    private Reply health(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        String scoped = path.length() > "/v1/health/".length() ? path.substring("/v1/health/".length()) : null;
        if (scoped != null && !strategyRef.toString().equalsIgnoreCase(scoped)) {
            return new Reply(404, new WireTypes.Error("strategyRef not served: " + scoped));
        }
        return new Reply(200, new WireTypes.Health("UP", true, null, scoped, query(exchange.getRequestURI(), "runnerId")));
    }

    // ---------------------------------------------------------------- infraestrutura HTTP

    /**
     * Resolve a resposta (inclusive o 400 de payload invalido — JSON malformado e IOException do
     * Jackson) antes de escrever, e so fecha a exchange depois de enviar.
     */
    private void handle(HttpExchange exchange, Endpoint endpoint) throws IOException {
        try {
            send(exchange, reply(exchange, endpoint));
        } finally {
            exchange.close();
        }
    }

    private Reply reply(HttpExchange exchange, Endpoint endpoint) {
        if (token != null && !token.isBlank()
                && !("Bearer " + token).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            return new Reply(401, new WireTypes.Error("invalid token"));
        }
        try {
            return endpoint.reply(exchange);
        } catch (IOException | RuntimeException e) {
            log.warn("reference: invalid request {} - {}", exchange.getRequestURI(), e.getMessage());
            return new Reply(400, new WireTypes.Error(String.valueOf(e.getMessage())));
        }
    }

    private void send(HttpExchange exchange, Reply reply) throws IOException {
        byte[] body = objectMapper.writeValueAsBytes(reply.body());
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String query(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts[0].equals(name) && parts.length == 2) {
                return java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private record Reply(int status, Object body) {
    }

    @FunctionalInterface
    private interface Endpoint {
        Reply reply(HttpExchange exchange) throws IOException;
    }

    /** Estado do runner: último sequence aceito e as decisões recentes por tickId (cache curto). */
    private static final class RunnerState {
        private static final int CACHE_SIZE = 32;
        private long lastSequence;
        private final Map<String, WireTypes.Decision> decisionsByTickId = new LinkedHashMap<>(CACHE_SIZE, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, WireTypes.Decision> eldest) {
                return size() > CACHE_SIZE;
            }
        };

        private void remember(String tickId, WireTypes.Decision decision) {
            decisionsByTickId.put(tickId, decision);
        }
    }
}
