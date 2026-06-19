package com.marmitt.core.dto.exchange;

import java.math.BigDecimal;

/**
 * Saldo de um único asset na exchange, agnóstico de provider.
 *
 * <p>Contrato de saída do caminho de consulta de saldo (T28/T29). Substitui o uso
 * direto de {@code AccountDataDto} (DTO de WebSocket) como contrato externo.
 *
 * @param asset  símbolo do ativo (ex.: "USDT", "BTC")
 * @param free   saldo disponível
 * @param locked saldo bloqueado em ordens abertas
 * @param total  {@code free + locked}
 */
public record BalanceDto(
        String asset,
        BigDecimal free,
        BigDecimal locked,
        BigDecimal total
) {

    public static BalanceDto of(String asset, BigDecimal free, BigDecimal locked) {
        BigDecimal safeFree = free != null ? free : BigDecimal.ZERO;
        BigDecimal safeLocked = locked != null ? locked : BigDecimal.ZERO;
        return new BalanceDto(asset, safeFree, safeLocked, safeFree.add(safeLocked));
    }
}
