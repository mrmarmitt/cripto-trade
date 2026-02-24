package com.marmitt.core.domain.portfolio;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
public class Balance {

    private Asset available;
    private Asset invested;
    private final Asset initialCapital;
    private BigDecimal realizedPnL;
    
    public Balance(Asset initialCapital) {
        this.initialCapital = initialCapital;
        this.available = initialCapital;
        this.invested = Asset.fiat(BigDecimal.ZERO, initialCapital.currency());
        this.realizedPnL = BigDecimal.ZERO;
    }
    
    private Balance(Asset available, Asset invested, Asset initialCapital, BigDecimal realizedPnL) {
        this.available = available;
        this.invested = invested;
        this.initialCapital = initialCapital;
        this.realizedPnL = realizedPnL;
    }

    public static Balance withInitialCapital(Asset initialCapital) {
        return new Balance(initialCapital);
    }
    
    public void allocate(Asset amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (!hasAvailableAmount(amount)) {
            throw new IllegalArgumentException("Insufficient available balance");
        }

        this.available = available.subtract(amount);
        this.invested = invested.add(amount);
    }
    
    public void deallocate(Asset amount) {
        Objects.requireNonNull(amount, "Amount cannot be null");

        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (amount.amount().compareTo(invested.amount()) > 0) {
            throw new IllegalArgumentException("Cannot deallocate more than invested amount");
        }

        this.invested = invested.subtract(amount);
        this.available = available.add(amount);
    }

    /**
     * Realiza uma venda, removendo o custo do invested e adicionando o valor de venda ao available.
     * A diferença entre saleValue e cost representa o lucro/prejuízo realizado.
     *
     * @param cost custo original da posição vendida (quantity × averagePrice)
     * @param saleValue valor recebido na venda (quantity × salePrice)
     */
    public void realizeSale(Asset cost, Asset saleValue) {
        Objects.requireNonNull(cost, "Cost cannot be null");
        Objects.requireNonNull(saleValue, "Sale value cannot be null");

        if (!cost.isPositive()) {
            throw new IllegalArgumentException("Cost must be positive");
        }

        if (!saleValue.isPositive()) {
            throw new IllegalArgumentException("Sale value must be positive");
        }

        // Se cost exceder invested (devido a arredondamentos ou múltiplas compras com fees),
        // usar o mínimo para evitar invested negativo
        Asset effectiveCost = cost.amount().compareTo(invested.amount()) > 0 ? invested : cost;

        this.invested = invested.subtract(effectiveCost);
        this.available = available.add(saleValue);
        this.realizedPnL = this.realizedPnL.add(saleValue.amount().subtract(effectiveCost.amount()));
    }
    
    public boolean hasAvailableAmount(Asset amount) {
        return amount.amount().compareTo(available.amount()) <= 0;
    }
    
    public Asset getTotal() {
        return available.add(invested);
    }
    
    public Asset getAllocated() {
        return invested;
    }
}