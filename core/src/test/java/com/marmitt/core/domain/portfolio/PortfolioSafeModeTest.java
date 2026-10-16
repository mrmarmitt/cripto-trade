package com.marmitt.core.domain.portfolio;

import com.marmitt.core.enums.CapitalPoolingMode;
import com.marmitt.core.enums.SafeModeOrigin;
import com.marmitt.core.enums.SafeModeStatus;
import com.marmitt.core.exceptions.SafeModeTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.marmitt.core.enums.SafeModeOrigin.AUTOMATIC;
import static com.marmitt.core.enums.SafeModeOrigin.MANUAL;
import static com.marmitt.core.enums.SafeModeStatus.CANCEL_ALL;
import static com.marmitt.core.enums.SafeModeStatus.HALT;
import static com.marmitt.core.enums.SafeModeStatus.NORMAL;
import static com.marmitt.core.enums.SafeModeStatus.PANIC_SELL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T45: regras de transicao do Safe Mode (Blueprint 11.1.B.1 e 11.1.C).
 */
class PortfolioSafeModeTest {

    @Test
    void raisingRecordsAuditAndUpdatesState() {
        Portfolio portfolio = at(NORMAL);

        SafeModeChange change = portfolio.changeSafeMode(HALT, MANUAL, "drawdown manual", false).orElseThrow();

        assertEquals(NORMAL, change.from());
        assertEquals(HALT, change.to());
        assertEquals(MANUAL, change.origin());
        assertEquals(HALT, portfolio.getSafeModeStatus());
        assertEquals("drawdown manual", portfolio.getSafeModeReason());
        assertEquals(change.changedAt(), portfolio.getSafeModeChangedAt());
    }

    @Test
    void sameLevelIsIdempotent() {
        Portfolio portfolio = at(CANCEL_ALL);

        assertTrue(portfolio.changeSafeMode(CANCEL_ALL, MANUAL, "repeat", true).isEmpty());
        assertEquals(CANCEL_ALL, portfolio.getSafeModeStatus());
    }

    @Test
    void massActionLevelsRequireConfirmation() {
        assertThrows(SafeModeTransitionException.class,
                () -> at(NORMAL).changeSafeMode(CANCEL_ALL, MANUAL, "no confirm", false));
        assertThrows(SafeModeTransitionException.class,
                () -> at(HALT).changeSafeMode(PANIC_SELL, MANUAL, "no confirm", false));
        assertTrue(at(NORMAL).changeSafeMode(PANIC_SELL, MANUAL, "confirmed jump", true).isPresent());
    }

    @Test
    void loweringMustGoOneLevelAtATime() {
        assertThrows(SafeModeTransitionException.class,
                () -> at(PANIC_SELL).changeSafeMode(HALT, MANUAL, "skip", false));
        assertThrows(SafeModeTransitionException.class,
                () -> at(CANCEL_ALL).changeSafeMode(NORMAL, MANUAL, "skip", false));

        Portfolio portfolio = at(PANIC_SELL);
        portfolio.changeSafeMode(CANCEL_ALL, MANUAL, "step", false).orElseThrow();
        portfolio.changeSafeMode(HALT, MANUAL, "step", false).orElseThrow();
        portfolio.changeSafeMode(NORMAL, MANUAL, "step", false).orElseThrow();
        assertEquals(NORMAL, portfolio.getSafeModeStatus());
    }

    @Test
    void automaticTriggerOnlyRaisesToHalt() {
        assertThrows(SafeModeTransitionException.class,
                () -> at(NORMAL).changeSafeMode(CANCEL_ALL, AUTOMATIC, "auto", true));
        assertThrows(SafeModeTransitionException.class,
                () -> at(HALT).changeSafeMode(NORMAL, AUTOMATIC, "auto resume", false));

        Portfolio portfolio = at(NORMAL);
        assertTrue(portfolio.changeSafeMode(HALT, AUTOMATIC, "daily loss", false).isPresent());
        assertEquals(AUTOMATIC, portfolio.getSafeModeOrigin());
    }

    @Test
    void automaticHaltDoesNotLowerAHigherManualLevel() {
        Portfolio portfolio = at(PANIC_SELL);

        Optional<SafeModeChange> change = portfolio.changeSafeMode(HALT, AUTOMATIC, "daily loss", false);

        assertTrue(change.isEmpty());
        assertEquals(PANIC_SELL, portfolio.getSafeModeStatus());
    }

    @Test
    void reasonIsMandatory() {
        assertThrows(SafeModeTransitionException.class,
                () -> at(NORMAL).changeSafeMode(HALT, MANUAL, " ", false));
        assertThrows(SafeModeTransitionException.class,
                () -> at(NORMAL).changeSafeMode(HALT, MANUAL, null, false));
    }

    private static Portfolio at(SafeModeStatus status) {
        return new Portfolio(UUID.randomUUID(), "p", true, Instant.now(), status, CapitalPoolingMode.SHARED,
                status == NORMAL ? null : SafeModeOrigin.MANUAL, status == NORMAL ? null : "setup",
                status == NORMAL ? null : Instant.now(), 0L);
    }
}
