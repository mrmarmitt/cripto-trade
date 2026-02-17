package com.marmitt.core.domain.shared;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chave única de roteamento e idempotência, gerada pelo StrategyRunner antes do dispatch.
 * <p>
 * Formato: {@code v{version}r{runner_short}t{timestamp}s{sequence}{type}_{transaction_uuid}}
 * <p>
 * Exemplo: {@code v1r01ft1700000000000s001N_8da233214f11b12a}
 * <p>
 * Tamanho total: ~36 caracteres (compatível com limite da maioria das exchanges).
 *
 * @see <a href="docs/IMPLEMENTATION_GUIDE.md">IG Seção 3.4.1</a>
 */
public record ClientOrderId(
        String rawValue,
        int version,
        String runnerShort,
        long timestamp,
        int sequence,
        char type,
        String transactionUuid
) {
    /**
     * Tipos de ordem suportados no clientOrderId
     */
    public static final char TYPE_NEW = 'N';
    public static final char TYPE_SELL = 'S';
    public static final char TYPE_CANCEL = 'C';

    private static final int CURRENT_VERSION = 1;
    private static final Pattern PARSE_PATTERN = Pattern.compile(
            "^v(\\d+)r([a-zA-Z0-9]{2,4})t(\\d{13})s(\\d{3})([NSC])_([a-f0-9]{16})$"
    );

    public ClientOrderId {
        Objects.requireNonNull(rawValue, "rawValue cannot be null");
        Objects.requireNonNull(runnerShort, "runnerShort cannot be null");
        Objects.requireNonNull(transactionUuid, "transactionUuid cannot be null");

        if (runnerShort.length() < 2 || runnerShort.length() > 4) {
            throw new IllegalArgumentException("runnerShort must be 2-4 characters, got: " + runnerShort);
        }
        if (type != TYPE_NEW && type != TYPE_SELL && type != TYPE_CANCEL) {
            throw new IllegalArgumentException("Invalid type: " + type + ". Expected N, S, or C");
        }
    }

    /**
     * Gera um novo ClientOrderId para uma transação.
     *
     * @param shortCode     ShortCode do StrategyRunner (2-4 chars)
     * @param transactionId UUID da Transaction
     * @param type          Tipo da ordem: N (New/Buy), S (Sell), C (Cancel)
     * @param sequence      Sequência dentro do mesmo milissegundo (0-999)
     * @return novo ClientOrderId
     */
    public static ClientOrderId generate(String shortCode, UUID transactionId, char type, int sequence) {
        Objects.requireNonNull(shortCode, "shortCode cannot be null");
        Objects.requireNonNull(transactionId, "transactionId cannot be null");

        long ts = Instant.now().toEpochMilli();
        String truncatedUuid = transactionId.toString().replace("-", "").substring(0, 16);
        String raw = String.format("v%dr%st%ds%03d%c_%s",
                CURRENT_VERSION, shortCode, ts, sequence, type, truncatedUuid);

        return new ClientOrderId(raw, CURRENT_VERSION, shortCode, ts, sequence, type, truncatedUuid);
    }

    /**
     * Gera um novo ClientOrderId com sequence = 0.
     */
    public static ClientOrderId generate(String shortCode, UUID transactionId, char type) {
        return generate(shortCode, transactionId, type, 0);
    }

    /**
     * Faz parse de um clientOrderId raw retornado pela exchange.
     * Se a versão for desconhecida, lança exceção (deve ser encaminhado para DLQ).
     *
     * @param rawString o clientOrderId completo
     * @return ClientOrderId parseado
     * @throws IllegalArgumentException se o formato for inválido ou versão desconhecida
     */
    public static ClientOrderId parse(String rawString) {
        Objects.requireNonNull(rawString, "rawString cannot be null");

        Matcher matcher = PARSE_PATTERN.matcher(rawString);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid clientOrderId format: " + rawString);
        }

        int parsedVersion = Integer.parseInt(matcher.group(1));
        if (parsedVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unknown clientOrderId version: " + parsedVersion);
        }

        return new ClientOrderId(
                rawString,
                parsedVersion,
                matcher.group(2),
                Long.parseLong(matcher.group(3)),
                Integer.parseInt(matcher.group(4)),
                matcher.group(5).charAt(0),
                matcher.group(6)
        );
    }

    /**
     * Retorna o shortCode do Runner — usado pelo Portfolio para roteamento de callbacks.
     */
    public String getRunnerShort() {
        return runnerShort;
    }

    /**
     * Retorna o UUID truncado da Transaction — usado para reconciliação com o DB.
     */
    public String getTransactionUuid() {
        return transactionUuid;
    }

    @Override
    public String toString() {
        return rawValue;
    }
}
