package com.marmitt.core.dto.exchange;

import com.marmitt.core.enums.ExchangeBalanceStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Conferencia de saldo de uma exchange (T57): soma do que os portfolios do ctrade esperam ter na conta,
 * por moeda/ativo, contra o saldo da conta.
 *
 * @param exchange   exchange conferida
 * @param status     pior status entre os ativos, ou {@code SKIPPED}/{@code FAILED} quando nao conferido
 * @param code       codigo estavel do resultado
 * @param message    explicacao legivel
 * @param portfolios portfolios com runner nao arquivado nesta exchange (os afetados por um HALT)
 * @param assets     resultado por moeda/ativo; vazio quando nao conferido
 */
public record ExchangeBalanceReport(
        String exchange,
        ExchangeBalanceStatus status,
        String code,
        String message,
        List<UUID> portfolios,
        List<AssetBalance> assets
) {

    public ExchangeBalanceReport {
        portfolios = List.copyOf(portfolios);
        assets = List.copyOf(assets);
    }

    /**
     * @param asset         moeda de cotacao (ex.: USDT) ou ativo comprado (ex.: BTC)
     * @param localExpected o que os portfolios esperam ter na conta
     * @param exchangeTotal {@code free + locked} na conta
     * @param signedDelta   {@code exchangeTotal - localExpected}; negativo e falta na conta
     * @param unallocated   excedente da conta fora de qualquer portfolio (zero quando falta)
     * @param inFlight      quanto as ordens em voo podem ter movido na conta sem conciliacao local
     * @param status        {@code OK}, {@code PENDING} ou {@code DEFICIT}
     */
    public record AssetBalance(String asset, BigDecimal localExpected, BigDecimal exchangeTotal,
                               BigDecimal signedDelta, BigDecimal unallocated, BigDecimal inFlight,
                               ExchangeBalanceStatus status) {
    }

    public static ExchangeBalanceReport checked(String exchange, List<UUID> portfolios, List<AssetBalance> assets) {
        ExchangeBalanceStatus worst = assets.stream().map(AssetBalance::status)
                .max(Comparator.naturalOrder()).orElse(ExchangeBalanceStatus.OK);
        String message = switch (worst) {
            case DEFICIT -> "ctrade expects more than the exchange account holds";
            case PENDING -> "account short only by orders in flight not yet reconciled";
            default -> "exchange account covers every portfolio";
        };
        return new ExchangeBalanceReport(exchange, worst, worst.name(), message, portfolios, assets);
    }

    public static ExchangeBalanceReport notChecked(String exchange, ExchangeBalanceStatus status, String code,
                                                   String message, List<UUID> portfolios) {
        return new ExchangeBalanceReport(exchange, status, code, message, portfolios, List.of());
    }

    /** Ativos com falta na conta alem das ordens em voo. */
    public List<AssetBalance> deficits() {
        return assets.stream().filter(a -> a.status() == ExchangeBalanceStatus.DEFICIT).toList();
    }

    /** Resumo legivel para log e motivo do HALT. */
    public String details() {
        if (assets.isEmpty()) {
            return "exchange=" + exchange + " status=" + status + " code=" + code + " message=" + message;
        }
        return "exchange=" + exchange + " status=" + status + " " + assets.stream()
                .map(a -> a.asset() + "[expected=" + a.localExpected().toPlainString()
                        + " exchange=" + a.exchangeTotal().toPlainString()
                        + " delta=" + a.signedDelta().toPlainString() + " " + a.status() + "]")
                .collect(Collectors.joining(" "));
    }
}
