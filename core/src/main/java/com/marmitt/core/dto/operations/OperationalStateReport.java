package com.marmitt.core.dto.operations;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Estado operacional consolidado (T55): todas as verificacoes, inclusive as {@code OK}, para quem opera
 * saber o que foi verificado. O status geral e a maior severidade encontrada.
 */
public record OperationalStateReport(OperationalSeverity status, Instant generatedAt, List<OperationalCheck> checks) {

    public static OperationalStateReport of(Instant generatedAt, List<OperationalCheck> checks) {
        OperationalSeverity worst = checks.stream()
                .map(OperationalCheck::status)
                .max(Comparator.naturalOrder())
                .orElse(OperationalSeverity.OK);
        return new OperationalStateReport(worst, generatedAt, List.copyOf(checks));
    }
}
