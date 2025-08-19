package com.marmitt.ctrade.application.service;

import com.marmitt.ctrade.domain.entity.PriceAlert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para PriceAlertService.
 * Testa gerenciamento de alertas de preço e lógica de negócio.
 */
class PriceAlertServiceTest {

    private PriceAlertService priceAlertService;

    @BeforeEach
    void setUp() {
        priceAlertService = new PriceAlertService();
    }

    @Test
    void shouldAddAlertSuccessfully() {
        // Given
        PriceAlert alert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        
        // When
        priceAlertService.addAlert(alert);
        
        // Then
        List<PriceAlert> activeAlerts = priceAlertService.getActiveAlerts("BTCUSDT");
        assertThat(activeAlerts).hasSize(1);
        assertThat(activeAlerts.get(0)).isEqualTo(alert);
    }

    @Test
    void shouldAddMultipleAlertsForSameTradingPair() {
        // Given
        PriceAlert alert1 = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert alert2 = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        
        // When
        priceAlertService.addAlert(alert1);
        priceAlertService.addAlert(alert2);
        
        // Then
        List<PriceAlert> activeAlerts = priceAlertService.getActiveAlerts("BTCUSDT");
        assertThat(activeAlerts).hasSize(2);
        assertThat(activeAlerts).containsExactlyInAnyOrder(alert1, alert2);
    }

    @Test
    void shouldAddAlertsForDifferentTradingPairs() {
        // Given
        PriceAlert btcAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert ethAlert = new PriceAlert("ETHUSDT", new BigDecimal("3000"), PriceAlert.AlertType.BELOW);
        
        // When
        priceAlertService.addAlert(btcAlert);
        priceAlertService.addAlert(ethAlert);
        
        // Then
        assertThat(priceAlertService.getActiveAlerts("BTCUSDT")).containsExactly(btcAlert);
        assertThat(priceAlertService.getActiveAlerts("ETHUSDT")).containsExactly(ethAlert);
    }

    @Test
    void shouldReturnEmptyListForNonExistentTradingPair() {
        // When
        List<PriceAlert> alerts = priceAlertService.getActiveAlerts("NONEXISTENT");
        
        // Then
        assertThat(alerts).isEmpty();
    }

    @Test
    void shouldGetAllActiveAlertsAcrossAllPairs() {
        // Given
        PriceAlert btcAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert ethAlert = new PriceAlert("ETHUSDT", new BigDecimal("3000"), PriceAlert.AlertType.BELOW);
        PriceAlert adaAlert = new PriceAlert("ADAUSDT", new BigDecimal("2"), PriceAlert.AlertType.ABOVE);
        
        priceAlertService.addAlert(btcAlert);
        priceAlertService.addAlert(ethAlert);
        priceAlertService.addAlert(adaAlert);
        
        // When
        List<PriceAlert> allActiveAlerts = priceAlertService.getAllActiveAlerts();
        
        // Then
        assertThat(allActiveAlerts).hasSize(3);
        assertThat(allActiveAlerts).containsExactlyInAnyOrder(btcAlert, ethAlert, adaAlert);
    }

    @Test
    void shouldNotReturnInactiveAlertsInActiveAlertsList() {
        // Given
        PriceAlert activeAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert inactiveAlert = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        inactiveAlert.setActive(false);
        
        priceAlertService.addAlert(activeAlert);
        priceAlertService.addAlert(inactiveAlert);
        
        // When
        List<PriceAlert> activeAlerts = priceAlertService.getActiveAlerts("BTCUSDT");
        
        // Then
        assertThat(activeAlerts).hasSize(1);
        assertThat(activeAlerts).containsExactly(activeAlert);
    }

    @Test
    void shouldCheckAndTriggerAlertsWhenPriceMatchesCondition() {
        // Given
        PriceAlert aboveAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert belowAlert = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(aboveAlert);
        priceAlertService.addAlert(belowAlert);
        
        BigDecimal triggerPrice = new BigDecimal("55000"); // Should trigger ABOVE alert
        
        // When
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", triggerPrice);
        
        // Then
        assertThat(triggeredAlerts).hasSize(1);
        assertThat(triggeredAlerts.get(0)).isEqualTo(aboveAlert);
        assertThat(aboveAlert.isActive()).isFalse();
        assertThat(aboveAlert.getTriggeredAt()).isNotNull();
        assertThat(belowAlert.isActive()).isTrue(); // Should remain active
    }

    @Test
    void shouldTriggerMultipleAlertsWhenPriceMatchesMultipleConditions() {
        // Given
        PriceAlert aboveAlert1 = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert aboveAlert2 = new PriceAlert("BTCUSDT", new BigDecimal("52000"), PriceAlert.AlertType.ABOVE);
        
        priceAlertService.addAlert(aboveAlert1);
        priceAlertService.addAlert(aboveAlert2);
        
        BigDecimal triggerPrice = new BigDecimal("55000"); // Should trigger both alerts
        
        // When
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", triggerPrice);
        
        // Then
        assertThat(triggeredAlerts).hasSize(2);
        assertThat(triggeredAlerts).containsExactlyInAnyOrder(aboveAlert1, aboveAlert2);
        assertThat(aboveAlert1.isActive()).isFalse();
        assertThat(aboveAlert2.isActive()).isFalse();
    }

    @Test
    void shouldNotTriggerAlertsWhenPriceDoesNotMatchCondition() {
        // Given
        PriceAlert aboveAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert belowAlert = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(aboveAlert);
        priceAlertService.addAlert(belowAlert);
        
        BigDecimal currentPrice = new BigDecimal("47000"); // Should not trigger any alert
        
        // When
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", currentPrice);
        
        // Then
        assertThat(triggeredAlerts).isEmpty();
        assertThat(aboveAlert.isActive()).isTrue();
        assertThat(belowAlert.isActive()).isTrue();
    }

    @Test
    void shouldReturnEmptyListWhenCheckingAlertsForNonExistentPair() {
        // Given
        BigDecimal currentPrice = new BigDecimal("50000");
        
        // When
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("NONEXISTENT", currentPrice);
        
        // Then
        assertThat(triggeredAlerts).isEmpty();
    }

    @Test
    void shouldRemoveAlertById() {
        // Given
        PriceAlert alert1 = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert alert2 = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(alert1);
        priceAlertService.addAlert(alert2);
        
        // When
        boolean removed = priceAlertService.removeAlert(alert1.getId());
        
        // Then
        assertThat(removed).isTrue();
        assertThat(priceAlertService.getActiveAlerts("BTCUSDT")).containsExactly(alert2);
    }

    @Test
    void shouldReturnFalseWhenRemovingNonExistentAlert() {
        // Given
        PriceAlert alert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        priceAlertService.addAlert(alert);
        
        // When
        boolean removed = priceAlertService.removeAlert("NON_EXISTENT_ID");
        
        // Then
        assertThat(removed).isFalse();
        assertThat(priceAlertService.getActiveAlerts("BTCUSDT")).containsExactly(alert);
    }

    @Test
    void shouldRemoveAlertFromCorrectTradingPair() {
        // Given
        PriceAlert btcAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert ethAlert = new PriceAlert("ETHUSDT", new BigDecimal("3000"), PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(btcAlert);
        priceAlertService.addAlert(ethAlert);
        
        // When
        boolean removed = priceAlertService.removeAlert(btcAlert.getId());
        
        // Then
        assertThat(removed).isTrue();
        assertThat(priceAlertService.getActiveAlerts("BTCUSDT")).isEmpty();
        assertThat(priceAlertService.getActiveAlerts("ETHUSDT")).containsExactly(ethAlert);
    }

    @Test
    void shouldClearInactiveAlerts() {
        // Given
        PriceAlert activeAlert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        PriceAlert inactiveAlert1 = new PriceAlert("BTCUSDT", new BigDecimal("45000"), PriceAlert.AlertType.BELOW);
        PriceAlert inactiveAlert2 = new PriceAlert("ETHUSDT", new BigDecimal("3000"), PriceAlert.AlertType.ABOVE);
        
        // Trigger some alerts to make them inactive
        inactiveAlert1.trigger();
        inactiveAlert2.trigger();
        
        priceAlertService.addAlert(activeAlert);
        priceAlertService.addAlert(inactiveAlert1);
        priceAlertService.addAlert(inactiveAlert2);
        
        // When
        priceAlertService.clearInactiveAlerts();
        
        // Then
        assertThat(priceAlertService.getAllActiveAlerts()).containsExactly(activeAlert);
        assertThat(priceAlertService.getActiveAlerts("BTCUSDT")).containsExactly(activeAlert);
        assertThat(priceAlertService.getActiveAlerts("ETHUSDT")).isEmpty();
    }

    @Test
    void shouldHandleConcurrentAccess() {
        // Given
        PriceAlert alert = new PriceAlert("BTCUSDT", new BigDecimal("50000"), PriceAlert.AlertType.ABOVE);
        
        // When - Simulate concurrent operations
        priceAlertService.addAlert(alert);
        List<PriceAlert> activeAlerts1 = priceAlertService.getActiveAlerts("BTCUSDT");
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", new BigDecimal("55000"));
        List<PriceAlert> activeAlerts2 = priceAlertService.getActiveAlerts("BTCUSDT");
        
        // Then
        assertThat(activeAlerts1).hasSize(1);
        assertThat(triggeredAlerts).hasSize(1);
        assertThat(activeAlerts2).isEmpty(); // Alert should be inactive after trigger
    }

    @Test
    void shouldHandleEdgeCasesInPriceTriggers() {
        // Given
        BigDecimal threshold = new BigDecimal("50000.00");
        PriceAlert aboveAlert = new PriceAlert("BTCUSDT", threshold, PriceAlert.AlertType.ABOVE);
        PriceAlert belowAlert = new PriceAlert("BTCUSDT", threshold, PriceAlert.AlertType.BELOW);
        
        priceAlertService.addAlert(aboveAlert);
        priceAlertService.addAlert(belowAlert);
        
        // When & Then - Price exactly equals threshold (should not trigger)
        List<PriceAlert> triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", threshold);
        assertThat(triggeredAlerts).isEmpty();
        
        // When & Then - Price slightly above threshold
        BigDecimal slightlyAbove = new BigDecimal("50000.01");
        triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", slightlyAbove);
        assertThat(triggeredAlerts).hasSize(1);
        assertThat(triggeredAlerts.get(0).getAlertType()).isEqualTo(PriceAlert.AlertType.ABOVE);
        
        // Reset for below test
        aboveAlert.setActive(true);
        
        // When & Then - Price slightly below threshold
        BigDecimal slightlyBelow = new BigDecimal("49999.99");
        triggeredAlerts = priceAlertService.checkAndTriggerAlerts("BTCUSDT", slightlyBelow);
        assertThat(triggeredAlerts).hasSize(1);
        assertThat(triggeredAlerts.get(0).getAlertType()).isEqualTo(PriceAlert.AlertType.BELOW);
    }
}