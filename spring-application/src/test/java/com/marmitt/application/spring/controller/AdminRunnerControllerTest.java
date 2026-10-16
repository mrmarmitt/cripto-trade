package com.marmitt.application.spring.controller;

import com.marmitt.application.spring.web.GlobalExceptionHandler;
import com.marmitt.core.dto.runner.request.UpdateRunnerLimitsRequest;
import com.marmitt.core.dto.runner.response.RunnerTransitionResult;
import com.marmitt.core.enums.RunnerStatus;
import com.marmitt.core.exceptions.RunnerNotFoundException;
import com.marmitt.core.exceptions.RunnerOperationException;
import com.marmitt.core.ports.inbound.portfolio.ManageSafeModePort;
import com.marmitt.core.ports.inbound.runner.HaltRunnerPort;
import com.marmitt.core.ports.inbound.runner.ManageRunnerLifecyclePort;
import com.marmitt.core.ports.inbound.runner.QueryRunnerPort;
import com.marmitt.core.ports.inbound.runner.RunnerOrderCommandPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T49: borda HTTP do ciclo de vida e dos comandos do runner. Regras no core; aqui so o mapeamento.
 */
class AdminRunnerControllerTest {

    private final ManageRunnerLifecyclePort lifecycle = mock(ManageRunnerLifecyclePort.class);
    private final RunnerOrderCommandPort commands = mock(RunnerOrderCommandPort.class);
    private final UUID runnerId = UUID.randomUUID();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(List.of(), mock(HaltRunnerPort.class),
                        mock(QueryRunnerPort.class), mock(ManageSafeModePort.class), lifecycle, commands))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void resumeReturnsTheTransition() throws Exception {
        when(lifecycle.resume(runnerId)).thenReturn(
                new RunnerTransitionResult(runnerId, "s", "BTCUSDT", RunnerStatus.HALTED, RunnerStatus.ACTIVE));

        mockMvc.perform(post("/api/admin/runners/{id}/resume", runnerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("HALTED"))
                .andExpect(jsonPath("$.newStatus").value("ACTIVE"));
    }

    @Test
    void refusedOperationIsConflictAndUnknownRunnerIsNotFound() throws Exception {
        when(lifecycle.start(runnerId)).thenThrow(new RunnerOperationException("reconciliation not completed"));
        UUID unknown = UUID.randomUUID();
        when(lifecycle.terminate(unknown)).thenThrow(new RunnerNotFoundException(unknown));

        mockMvc.perform(post("/api/admin/runners/{id}/start", runnerId)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/runners/{id}/terminate", unknown)).andExpect(status().isNotFound());
    }

    @Test
    void patchForwardsTheMutableLimits() throws Exception {
        when(lifecycle.updateLimits(runnerId, new UpdateRunnerLimitsRequest(new BigDecimal("0.3"), 2, null)))
                .thenReturn(new RunnerTransitionResult(runnerId, "s", "BTCUSDT", RunnerStatus.ACTIVE, RunnerStatus.ACTIVE));

        mockMvc.perform(patch("/api/admin/runners/{id}", runnerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAllocationPercent\":0.3,\"maxOpenPositions\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void ordersAndPositionsCommandsAreAccepted() throws Exception {
        UUID transactionId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();

        mockMvc.perform(post("/api/admin/runners/{id}/orders/{tx}/cancel", runnerId, transactionId))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/runners/{id}/positions/{pos}/close", runnerId, positionId))
                .andExpect(status().isAccepted());

        verify(commands).cancelOrder(runnerId, transactionId);
        verify(commands).closePosition(runnerId, positionId);
    }

    @Test
    void closeRefusedIsConflict() throws Exception {
        UUID positionId = UUID.randomUUID();
        doThrow(new RunnerOperationException("locked")).when(commands).closePosition(runnerId, positionId);

        mockMvc.perform(post("/api/admin/runners/{id}/positions/{pos}/close", runnerId, positionId))
                .andExpect(status().isConflict());
    }
}
