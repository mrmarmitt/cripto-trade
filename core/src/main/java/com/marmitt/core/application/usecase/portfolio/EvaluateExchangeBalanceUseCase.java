package com.marmitt.core.application.usecase.portfolio;

import com.marmitt.core.application.usecase.exchange.ExchangeBalanceReconciliation;
import com.marmitt.core.dto.exchange.ExchangeBalanceReport;
import com.marmitt.core.enums.ExchangeBalanceStatus;
import com.marmitt.core.enums.SafeModeTrigger;
import com.marmitt.core.ports.inbound.portfolio.EvaluateExchangeBalancePort;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gatilho de divergencia de saldo durante a operacao (T57): a mesma conferencia do boot
 * ({@link ExchangeBalanceReconciliation}), avaliada periodicamente. Exchange em {@code DEFICIT} em
 * <b>duas leituras seguidas</b> poe em HALT todos os seus portfolios pelo {@link AutomaticSafeModeTrigger}
 * (gatilho {@code BALANCE_DIVERGENCE}); a primeira leitura so registra. Duas leituras porque a confirmacao
 * de capital de uma SELL executada e assincrona: por um instante o lote ja fechou e o saldo ainda nao
 * recebeu a venda. A falta coberta por ordens em voo ({@code PENDING}) nao conta.
 */
@Slf4j
public class EvaluateExchangeBalanceUseCase implements EvaluateExchangeBalancePort {

    private final ExchangeBalanceReconciliation reconciliation;
    private final AutomaticSafeModeTrigger safeModeTrigger;
    /** Exchanges em DEFICIT na leitura anterior. */
    private final Set<String> deficitLastReading = new HashSet<>();

    public EvaluateExchangeBalanceUseCase(ExchangeBalanceReconciliation reconciliation,
                                          AutomaticSafeModeTrigger safeModeTrigger) {
        this.reconciliation = reconciliation;
        this.safeModeTrigger = safeModeTrigger;
    }

    @Override
    public synchronized void evaluate() {
        List<ExchangeBalanceReport> reports = reconciliation.reconcileAll();
        Set<String> deficitNow = new HashSet<>();
        for (ExchangeBalanceReport report : reports) {
            if (report.status() != ExchangeBalanceStatus.DEFICIT) {
                continue;
            }
            deficitNow.add(report.exchange());
            if (!deficitLastReading.contains(report.exchange())) {
                log.warn("exchangeBalance: deficit on first reading, confirming on the next - {}", report.details());
                continue;
            }
            log.error("exchangeBalance: deficit confirmed, halting portfolios={} - {}",
                    report.portfolios().size(), report.details());
            report.portfolios().forEach(portfolioId -> safeModeTrigger.activate(portfolioId,
                    SafeModeTrigger.BALANCE_DIVERGENCE, report.details()));
        }
        deficitLastReading.clear();
        deficitLastReading.addAll(deficitNow);
    }
}
