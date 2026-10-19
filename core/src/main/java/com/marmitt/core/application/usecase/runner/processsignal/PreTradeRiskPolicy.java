package com.marmitt.core.application.usecase.runner.processsignal;

import com.marmitt.core.domain.runner.StrategyRunner;
import com.marmitt.core.dto.strategy.StrategyOutputDto;
import com.marmitt.core.enums.TradingAction;
import com.marmitt.core.ports.outbound.metrics.SignalDecision;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Limites de risco aplicados ao sinal antes de materializar a transacao (T50; IG §7.1 passo 4 e
 * §7.3 V5). Mesmo ponto das guardas de execucao: nada e persistido nem reservado quando recusa.
 *
 * <ul>
 *   <li><b>Sanidade de preco</b> (BUY e SELL): {@code limitPrice} a mais de
 *       {@link #MAX_PRICE_DEVIATION} do preco do tick e descartado — pega erro grosseiro da estrategia
 *       (ex.: casas decimais trocadas). Sinal sem {@code limitPrice} usa o preco do tick e nao tem
 *       desvio; as saidas decididas pelo core (MARKET) nunca trazem preco.</li>
 *   <li><b>{@code maxOpenPositions} / {@code maxPendingOrders}</b> (so BUY): limites do runner contra
 *       o {@link RunnerExposureService.ExposureSnapshot}. SELL so reduz exposicao e nao e barrada,
 *       para nunca travar uma saida (stop do lote, liquidacao).</li>
 * </ul>
 */
@Slf4j
class PreTradeRiskPolicy {

    /** Decisao de negocio da T50: 10% do preco do tick (sugestao do IG §7.3 V5), BUY e SELL. */
    static final BigDecimal MAX_PRICE_DEVIATION = new BigDecimal("0.10");

    /**
     * @param snapshot exposicao do runner; obrigatorio para BUY, ignorado em SELL
     * @return o desfecho de rejeicao, ou vazio quando o sinal pode seguir
     */
    Optional<SignalDecision> rejection(StrategyRunner runner, StrategyOutputDto signal, BigDecimal tickPrice,
                                       RunnerExposureService.ExposureSnapshot snapshot) {
        if (signal.limitPrice() != null && deviates(signal.limitPrice(), tickPrice)) {
            log.warn("preTradeRisk: PRICE_DEVIATION runnerId={} decision={} limitPrice={} tickPrice={} maxDeviation={}",
                    runner.getId(), signal.decision(), signal.limitPrice(), tickPrice, MAX_PRICE_DEVIATION);
            return Optional.of(SignalDecision.REJECTED_PRICE_DEVIATION);
        }
        if (signal.decision() != TradingAction.SHOULD_BUY) {
            return Optional.empty();
        }
        if (snapshot.openPositions() >= runner.getMaxOpenPositions()) {
            log.info("preTradeRisk: MAX_POSITIONS_REACHED runnerId={} openPositions={} maxOpenPositions={}",
                    runner.getId(), snapshot.openPositions(), runner.getMaxOpenPositions());
            return Optional.of(SignalDecision.REJECTED_MAX_POSITIONS);
        }
        if (snapshot.inFlightOrders() >= runner.getMaxPendingOrders()) {
            log.info("preTradeRisk: MAX_PENDING_REACHED runnerId={} inFlightOrders={} maxPendingOrders={}",
                    runner.getId(), snapshot.inFlightOrders(), runner.getMaxPendingOrders());
            return Optional.of(SignalDecision.REJECTED_MAX_PENDING);
        }
        return Optional.empty();
    }

    private static boolean deviates(BigDecimal limitPrice, BigDecimal tickPrice) {
        BigDecimal deviation = limitPrice.subtract(tickPrice).abs().divide(tickPrice, 8, RoundingMode.HALF_UP);
        return deviation.compareTo(MAX_PRICE_DEVIATION) > 0;
    }
}
