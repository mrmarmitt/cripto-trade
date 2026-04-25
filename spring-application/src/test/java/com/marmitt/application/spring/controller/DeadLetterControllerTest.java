package com.marmitt.application.spring.controller;

import com.marmitt.core.dto.portfolio.DeadLetterEntryDto;
import com.marmitt.core.dto.portfolio.ReprocessDeadLetterResponse;
import com.marmitt.core.dto.portfolio.ResolveDeadLetterResponse;
import com.marmitt.core.enums.DlqReason;
import com.marmitt.core.ports.inbound.portfolio.ManageDeadLetterPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeadLetterControllerTest {

    private final ManageDeadLetterPort manageDeadLetter = mock(ManageDeadLetterPort.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DeadLetterController(manageDeadLetter)).build();
    }

    @Test
    void listUnresolvedShouldReturnEntriesAndForwardFilters() throws Exception {
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.listUnresolved(portfolioId, runnerId, 25)).thenReturn(List.of(
                new DeadLetterEntryDto(
                        deadLetterId,
                        portfolioId,
                        runnerId,
                        "client-1",
                        "EX_1",
                        "{\"source\":\"test\"}",
                        DlqReason.RECONCILIATION_CONFLICT,
                        false,
                        null,
                        null,
                        Instant.parse("2026-04-25T12:00:00Z")
                )
        ));

        mockMvc.perform(get("/api/dead-letters")
                        .param("portfolioId", portfolioId.toString())
                        .param("runnerId", runnerId.toString())
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(deadLetterId.toString()))
                .andExpect(jsonPath("$[0].portfolioId").value(portfolioId.toString()))
                .andExpect(jsonPath("$[0].runnerId").value(runnerId.toString()))
                .andExpect(jsonPath("$[0].reason").value("RECONCILIATION_CONFLICT"));

        verify(manageDeadLetter).listUnresolved(portfolioId, runnerId, 25);
    }

    @Test
    void resolveShouldReturnOkWhenEntryIsResolved() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        DeadLetterEntryDto entry = new DeadLetterEntryDto(
                deadLetterId,
                portfolioId,
                runnerId,
                "client-1",
                "EX_1",
                "{\"source\":\"test\"}",
                DlqReason.RECONCILIATION_CONFLICT,
                true,
                "operator@test",
                Instant.parse("2026-04-25T12:10:00Z"),
                Instant.parse("2026-04-25T12:00:00Z")
        );
        when(manageDeadLetter.resolve(deadLetterId, "operator@test", "manual review"))
                .thenReturn(ResolveDeadLetterResponse.success(entry, "Dead letter entry resolved successfully"));

        mockMvc.perform(post("/api/dead-letters/{id}/resolve", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolvedBy": "operator@test",
                                  "resolutionNote": "manual review"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.deadLetterId").value(deadLetterId.toString()))
                .andExpect(jsonPath("$.entry.resolvedBy").value("operator@test"));
    }

    @Test
    void resolveShouldReturnNotFoundWhenEntryDoesNotExist() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.resolve(deadLetterId, "operator@test", "manual review"))
                .thenReturn(ResolveDeadLetterResponse.failure(deadLetterId, "Dead letter entry not found"));

        mockMvc.perform(post("/api/dead-letters/{id}/resolve", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolvedBy": "operator@test",
                                  "resolutionNote": "manual review"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resolved").value(false))
                .andExpect(jsonPath("$.message").value("Dead letter entry not found"));
    }

    @Test
    void resolveShouldReturnConflictWhenEntryIsAlreadyResolved() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.resolve(deadLetterId, "operator@test", "manual review"))
                .thenReturn(ResolveDeadLetterResponse.failure(deadLetterId, "Dead letter entry is already resolved"));

        mockMvc.perform(post("/api/dead-letters/{id}/resolve", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolvedBy": "operator@test",
                                  "resolutionNote": "manual review"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resolved").value(false))
                .andExpect(jsonPath("$.message").value("Dead letter entry is already resolved"));
    }

    @Test
    void resolveShouldReturnBadRequestWhenUseCaseRejectsInput() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.resolve(deadLetterId, "", "manual review"))
                .thenThrow(new IllegalArgumentException("resolvedBy cannot be blank"));

        mockMvc.perform(post("/api/dead-letters/{id}/resolve", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolvedBy": "",
                                  "resolutionNote": "manual review"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resolved").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request: resolvedBy cannot be blank"));
    }

    @Test
    void reprocessShouldReturnOkWhenEntryIsReprocessed() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        UUID portfolioId = UUID.randomUUID();
        UUID runnerId = UUID.randomUUID();
        DeadLetterEntryDto entry = newEntry(deadLetterId, portfolioId, runnerId, true, "operator@test");
        when(manageDeadLetter.reprocess(deadLetterId, "operator@test", "retry capital flow"))
                .thenReturn(ReprocessDeadLetterResponse.success(entry, "Dead letter entry reprocessed successfully"));

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "retry capital flow"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reprocessed").value(true))
                .andExpect(jsonPath("$.deadLetterId").value(deadLetterId.toString()))
                .andExpect(jsonPath("$.entry.resolvedBy").value("operator@test"));
    }

    @Test
    void reprocessShouldReturnConflictWhenEntryCannotBeReprocessedAutomatically() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.reprocess(deadLetterId, "operator@test", "retry capital flow"))
                .thenReturn(ReprocessDeadLetterResponse.failure(
                        deadLetterId,
                        "Dead letter entry cannot be reprocessed automatically"
                ));

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedBy": "operator@test",
                                  "resolutionNote": "retry capital flow"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reprocessed").value(false))
                .andExpect(jsonPath("$.message").value("Dead letter entry cannot be reprocessed automatically"));
    }

    @Test
    void reprocessShouldReturnBadRequestWhenUseCaseRejectsInput() throws Exception {
        UUID deadLetterId = UUID.randomUUID();
        when(manageDeadLetter.reprocess(deadLetterId, "", "retry capital flow"))
                .thenThrow(new IllegalArgumentException("requestedBy cannot be blank"));

        mockMvc.perform(post("/api/dead-letters/{id}/reprocess", deadLetterId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedBy": "",
                                  "resolutionNote": "retry capital flow"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reprocessed").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request: requestedBy cannot be blank"));
    }

    private static DeadLetterEntryDto newEntry(UUID deadLetterId,
                                               UUID portfolioId,
                                               UUID runnerId,
                                               boolean resolved,
                                               String resolvedBy) {
        return new DeadLetterEntryDto(
                deadLetterId,
                portfolioId,
                runnerId,
                "client-1",
                "EX_1",
                "{\"source\":\"test\"}",
                DlqReason.RECONCILIATION_CONFLICT,
                resolved,
                resolvedBy,
                resolved ? Instant.parse("2026-04-25T12:10:00Z") : null,
                Instant.parse("2026-04-25T12:00:00Z")
        );
    }
}
