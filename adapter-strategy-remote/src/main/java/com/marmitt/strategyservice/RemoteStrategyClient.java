package com.marmitt.strategyservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marmitt.core.dto.strategy.StrategyContextDto;
import com.marmitt.core.dto.strategy.StrategyInputDto;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.exceptions.StrategyUnavailableException;
import com.marmitt.core.exceptions.StrategyUnavailableException.Reason;
import com.marmitt.core.ports.outbound.strategy.StrategyEvaluationPort;
import com.marmitt.strategyservice.wire.DecisionValidator;
import com.marmitt.strategyservice.wire.StrategyContractMapper;
import com.marmitt.strategyservice.wire.WireTypes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cliente do contrato do strategy service (T41, contrato 1.1).
 *
 * <ul>
 *   <li><b>Sem retry</b> (T40): qualquer falha vira {@link StrategyUnavailableException} e o tick é
 *       HOLD; o próximo tick segue normalmente.</li>
 *   <li><b>{@code tickId}/{@code sequence} por runner:</b> {@code sequence} cresce a cada chamada do
 *       runner e parte do relogio em microssegundos ({@code max(anterior + 1, agora em µs)}): continua
 *       crescente depois de um restart do ctrade sem precisar persistir o contador — um servico que
 *       guarda o ultimo valor visto nao rejeita os ticks novos. {@code tickId} = runner + instante do
 *       tick + sequence.</li>
 *   <li><b>Resposta validada</b> contra o contrato antes de virar decisão ({@link DecisionValidator}).</li>
 *   <li><b>Decisão velha</b> ({@code agora − input.timestamp > maxDecisionAge}) é descartada.</li>
 * </ul>
 * O timeout rígido da chamada é do {@link StrategyServiceHttpPort}.
 */
public class RemoteStrategyClient implements StrategyEvaluationPort {

    private final StrategyServiceHttpPort http;
    private final ObjectMapper objectMapper;
    private final Map<UUID, RemoteStrategyEndpoint> endpoints;
    private final Duration maxDecisionAge;
    private final Clock clock;
    private final Map<UUID, AtomicLong> sequenceByRunner = new ConcurrentHashMap<>();

    public RemoteStrategyClient(StrategyServiceHttpPort http, ObjectMapper objectMapper,
                                List<RemoteStrategyEndpoint> endpoints, Duration maxDecisionAge, Clock clock) {
        this.http = http;
        this.objectMapper = objectMapper;
        Map<UUID, RemoteStrategyEndpoint> byRef = new HashMap<>();
        endpoints.forEach(endpoint -> byRef.put(endpoint.strategyRef(), endpoint));
        this.endpoints = Map.copyOf(byRef);
        this.maxDecisionAge = maxDecisionAge;
        this.clock = clock;
    }

    @Override
    public StrategyOutputDto evaluate(UUID strategyRef, StrategyInputDto input, StrategyContextDto context) {
        RemoteStrategyEndpoint endpoint = endpoint(strategyRef);
        long sequence = nextSequence(context.runnerId());
        String tickId = context.runnerId() + ":" + input.timestamp().toEpochMilli() + ":" + sequence;
        String body = write(StrategyContractMapper.toRequest(strategyRef, tickId, sequence, input, context));

        StrategyServiceHttpPort.Response response = call(() -> http.postJson(endpoint.baseUrl() + "/evaluate",
                headers(endpoint), body), strategyRef);
        WireTypes.Decision decision = read(response.body(), WireTypes.Decision.class, strategyRef);
        List<String> violations = DecisionValidator.violations(decision);
        if (!violations.isEmpty()) {
            throw new StrategyUnavailableException(Reason.INVALID_RESPONSE,
                    "decision outside the contract for " + strategyRef + ": " + violations);
        }

        Duration age = Duration.between(input.timestamp(), Instant.now(clock));
        if (age.compareTo(maxDecisionAge) > 0) {
            throw new StrategyUnavailableException(Reason.STALE_DECISION,
                    "decision for " + strategyRef + " arrived " + age.toMillis() + "ms after the tick (max "
                            + maxDecisionAge.toMillis() + "ms)");
        }
        return StrategyContractMapper.toCore(decision);
    }

    /**
     * Proximo {@code sequence} do runner: estritamente crescente na vida do cliente e ancorado no
     * relogio, para que um restart do ctrade (contador zerado em memoria) nao volte abaixo do ultimo
     * valor que o servico viu. Nao precisa ser contiguo.
     */
    private long nextSequence(UUID runnerId) {
        long nowMicros = ChronoUnit.MICROS.between(Instant.EPOCH, Instant.now(clock));
        return sequenceByRunner.computeIfAbsent(runnerId, id -> new AtomicLong())
                .updateAndGet(previous -> Math.max(previous + 1, nowMicros));
    }

    @Override
    public RemoteStrategyIdentity describe(UUID strategyRef) {
        RemoteStrategyEndpoint endpoint = endpoint(strategyRef);
        StrategyServiceHttpPort.Response response = call(() -> http.get(
                endpoint.baseUrl() + "/meta?strategyRef=" + strategyRef, headers(endpoint)), strategyRef);
        WireTypes.Meta[] served = read(response.body(), WireTypes.Meta[].class, strategyRef);
        if (served == null) {
            throw new StrategyUnavailableException(Reason.INVALID_RESPONSE, "empty /meta response for " + strategyRef);
        }
        return Arrays.stream(served)
                .filter(meta -> meta != null && strategyRef.toString().equalsIgnoreCase(meta.strategyId()))
                .findFirst()
                .map(meta -> new RemoteStrategyIdentity(UUID.fromString(meta.strategyId()), meta.name(),
                        meta.version(), meta.contractVersion()))
                .orElseThrow(() -> new StrategyUnavailableException(Reason.INVALID_RESPONSE,
                        "service at " + endpoint.baseUrl() + " does not serve " + strategyRef));
    }

    private RemoteStrategyEndpoint endpoint(UUID strategyRef) {
        RemoteStrategyEndpoint endpoint = endpoints.get(strategyRef);
        if (endpoint == null) {
            throw new StrategyUnavailableException(Reason.UNREACHABLE, "no endpoint configured for " + strategyRef);
        }
        return endpoint;
    }

    private static Map<String, String> headers(RemoteStrategyEndpoint endpoint) {
        if (endpoint.token() == null || endpoint.token().isBlank()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + endpoint.token());
    }

    private StrategyServiceHttpPort.Response call(HttpCall call, UUID strategyRef) {
        StrategyServiceHttpPort.Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            throw new StrategyUnavailableException(Reason.UNREACHABLE,
                    "strategy service unreachable for " + strategyRef + ": " + e.getMessage(), e);
        }
        if (!response.isSuccessful()) {
            throw new StrategyUnavailableException(Reason.ERROR_RESPONSE,
                    "strategy service answered " + response.statusCode() + " for " + strategyRef);
        }
        return response;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize strategy request", e);
        }
    }

    private <T> T read(String body, Class<T> type, UUID strategyRef) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new StrategyUnavailableException(Reason.INVALID_RESPONSE,
                    "unreadable response for " + strategyRef + ": " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface HttpCall {
        StrategyServiceHttpPort.Response execute() throws IOException;
    }
}
