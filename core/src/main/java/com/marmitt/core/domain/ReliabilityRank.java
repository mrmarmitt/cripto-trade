package com.marmitt.core.domain;

import lombok.Getter;

/**
 * Enum para classificar a confiabilidade de conexões WebSocket.
 * 
 * Baseado em múltiplos fatores como uptime, consistência de mensagens,
 * taxa de erro e frequência de reconexão.
 */
@Getter
public enum ReliabilityRank {
    A_PLUS("A+", 95, 100, "Exceptional - Enterprise grade reliability"),
    A("A", 85, 94, "Excellent - Very reliable for production use"),
    B("B", 70, 84, "Good - Suitable for most trading applications"),
    C("C", 55, 69, "Fair - May have occasional interruptions"),
    D("D", 40, 54, "Poor - Frequent issues, requires monitoring"),
    F("F", 0, 39, "Failing - Unreliable, not recommended for trading");

    private final String displayName;
    private final int minScore;
    private final int maxScore;
    private final String description;

    ReliabilityRank(String displayName, int minScore, int maxScore, String description) {
        this.displayName = displayName;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.description = description;
    }

    /**
     * Determina o rank baseado no score de confiabilidade.
     * 
     * @param reliabilityScore score de 0-100
     * @return rank correspondente
     */
    public static ReliabilityRank fromScore(double reliabilityScore) {
        int score = (int) Math.round(reliabilityScore);
        
        for (ReliabilityRank rank : values()) {
            if (score >= rank.minScore && score <= rank.maxScore) {
                return rank;
            }
        }
        
        return F; // Default para scores inválidos
    }

    /**
     * Verifica se o rank é considerado aceitável para trading.
     */
    public boolean isAcceptableForTrading() {
        return this.ordinal() <= B.ordinal(); // A+, A, B são aceitáveis
    }

    /**
     * Verifica se o rank requer atenção urgente.
     */
    public boolean requiresAttention() {
        return this.ordinal() >= D.ordinal(); // D, F requerem atenção
    }
}