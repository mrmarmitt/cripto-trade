package com.marmitt.strategyservice.wire;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Valida a decisão recebida contra o contrato antes de ela virar transação: variante pelo campo
 * {@code decision} (oneOf), campos obrigatórios de cada variante e faixas pelos mesmos {@code pattern}
 * do {@code openapi.yaml} ({@code PositiveDecimal}, {@code UnitFractionDecimal},
 * {@code NullablePositiveDecimal}). A resposta da estratégia é entrada não confiável.
 */
public final class DecisionValidator {

    private static final Pattern POSITIVE_DECIMAL = Pattern.compile("^(0*[1-9]\\d*(\\.\\d+)?|0+\\.\\d*[1-9]\\d*)$");
    private static final Pattern UNIT_FRACTION = Pattern.compile("^(0(\\.\\d+)?|1(\\.0+)?)$");
    private static final Set<String> ACTIONS = Set.of("SHOULD_HOLD", "SHOULD_BUY", "SHOULD_SELL", "SHOULD_CANCEL");

    private DecisionValidator() {
    }

    /**
     * @return violações encontradas; vazio = decisão válida
     */
    public static List<String> violations(WireTypes.Decision decision) {
        List<String> violations = new ArrayList<>();
        if (decision == null) {
            violations.add("empty body");
            return violations;
        }
        if (decision.strategyName() == null) {
            violations.add("strategyName is required");
        }
        if (decision.reasoning() == null) {
            violations.add("reasoning is required");
        }
        if (decision.decision() == null || !ACTIONS.contains(decision.decision())) {
            violations.add("decision must be one of " + ACTIONS + " but was " + decision.decision());
            return violations;
        }
        switch (decision.decision()) {
            case "SHOULD_BUY", "SHOULD_SELL" -> {
                require(violations, "quantity", decision.quantity(), POSITIVE_DECIMAL);
                require(violations, "confidence", decision.confidence(), UNIT_FRACTION);
                if (decision.limitPrice() != null && !POSITIVE_DECIMAL.matcher(decision.limitPrice()).matches()) {
                    violations.add("limitPrice must be a positive decimal or null: " + decision.limitPrice());
                }
                if ("SHOULD_SELL".equals(decision.decision())) {
                    optionalUuid(violations, "targetLotId", decision.targetLotId());
                }
            }
            case "SHOULD_CANCEL" -> {
                if (decision.targetTransactionId() == null) {
                    violations.add("targetTransactionId is required for SHOULD_CANCEL");
                } else {
                    optionalUuid(violations, "targetTransactionId", decision.targetTransactionId());
                }
            }
            default -> { }
        }
        return violations;
    }

    private static void require(List<String> violations, String field, String value, Pattern pattern) {
        if (value == null) {
            violations.add(field + " is required");
        } else if (!pattern.matcher(value).matches()) {
            violations.add(field + " out of range: " + value);
        }
    }

    private static void optionalUuid(List<String> violations, String field, String value) {
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            violations.add(field + " is not a uuid: " + value);
        }
    }
}
