package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.ApprovalQueueItemOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SplitOutput;
import andromeda.sei.ms.feature.allocation.application.usecase.DecideAllocationApprovalUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetApprovalQueueUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalForbiddenException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationApprovalSelfException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotAwaitingApprovalException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationNotFoundException;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationVersionConflictException;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** uso-ia-fora-de-entrega P3: the approval queue over the wire (standalone MockMvc, like the siblings). */
@ExtendWith(MockitoExtension.class)
class AllocationApprovalControllerTest {

    @Mock GetApprovalQueueUseCase getApprovalQueue;
    @Mock DecideAllocationApprovalUseCase decideApproval;
    @Mock CurrentUserPermissions currentUserPermissions;

    MockMvc mockMvc;
    UserPermissionSnapshot lead;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AllocationApprovalController(getApprovalQueue, decideApproval, currentUserPermissions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lead = new UserPermissionSnapshot("org-1", "lead", "r", "TECH_LEAD", List.of("AI_ALLOCATION_APPROVE"),
                List.of(), Set.of(), 1L);
        org.mockito.Mockito.lenient().when(currentUserPermissions.require()).thenReturn(lead);
    }

    @Test
    void queue_returnsTheItemsWithSplitsReasonsAndRepositories() throws Exception {
        AiUsageAllocation.ApprovalReason reason = new AiUsageAllocation.ApprovalReason(
                AiUsageAllocation.ApprovalReasonCode.REPO_OTHER_PROJECT, "org/repo", "p-1", "Andromeda",
                null, null, null, null, null, null, null, null);
        when(getApprovalQueue.execute(lead)).thenReturn(List.of(new ApprovalQueueItemOutput("a-1", "ana", "Ana",
                LocalDate.of(2026, 10, 28), new BigDecimal("4.20"), 1500,
                List.of(new SplitOutput(AllocationSplit.Kind.OUT_OF_DELIVERY, null, null, BigDecimal.ONE,
                        AllocationSplit.Category.STUDY, null)),
                List.of(reason), Instant.parse("2026-10-29T10:00:00Z"),
                List.of(new ApprovalQueueItemOutput.Repository("org/repo", "feature/x")))));

        mockMvc.perform(get("/api/v1/ai/allocations/approvals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].allocationId").value("a-1"))
                .andExpect(jsonPath("$[0].userName").value("Ana"))
                .andExpect(jsonPath("$[0].day").value("2026-10-28"))
                .andExpect(jsonPath("$[0].costUsd").value(4.20))
                .andExpect(jsonPath("$[0].tokens").value(1500))
                .andExpect(jsonPath("$[0].splits[0].kind").value("OUT_OF_DELIVERY"))
                .andExpect(jsonPath("$[0].reasons[0].code").value("REPO_OTHER_PROJECT"))
                .andExpect(jsonPath("$[0].repositories[0].name").value("org/repo"))
                .andExpect(jsonPath("$[0].repositories[0].branch").value("feature/x"));
    }

    @Test
    void approve_answers204() throws Exception {
        mockMvc.perform(post("/api/v1/ai/allocations/a-1/approve")).andExpect(status().isNoContent());
        verify(decideApproval).approve(lead, "a-1");
    }

    @Test
    void approve_answers404_403_403_409() throws Exception {
        doThrow(new AllocationNotFoundException("x")).when(decideApproval).approve(lead, "x");
        doThrow(new AllocationApprovalForbiddenException("f")).when(decideApproval).approve(lead, "f");
        doThrow(new AllocationApprovalSelfException("s")).when(decideApproval).approve(lead, "s");
        doThrow(new AllocationVersionConflictException("c")).when(decideApproval).approve(lead, "c");
        doThrow(new AllocationNotAwaitingApprovalException("n", "Outro Lead")).when(decideApproval).approve(lead, "n");

        mockMvc.perform(post("/api/v1/ai/allocations/x/approve")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ALLOCATION_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/ai/allocations/f/approve")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ALLOCATION_APPROVAL_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/ai/allocations/s/approve")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ALLOCATION_APPROVAL_SELF"));
        mockMvc.perform(post("/api/v1/ai/allocations/c/approve")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_CONFLICT"));
        mockMvc.perform(post("/api/v1/ai/allocations/n/approve")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_NOT_AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.decidedByName").value("Outro Lead"));
    }

    @Test
    void approveBatch_returnsApprovedAndSkippedWithReason_andTheLiteralPathWinsOverTheIdOne() throws Exception {
        when(decideApproval.approveBatch(eq(lead), eq(List.of("a-1", "a-2", "a-3")))).thenReturn(
                new DecideAllocationApprovalUseCase.BatchResult(List.of("a-1"), List.of(
                        new DecideAllocationApprovalUseCase.Skipped("a-2", DecideAllocationApprovalUseCase.SkipReason.SELF, null),
                        new DecideAllocationApprovalUseCase.Skipped("a-3",
                                DecideAllocationApprovalUseCase.SkipReason.NOT_AWAITING, "Outro Lead"))));

        mockMvc.perform(post("/api/v1/ai/allocations/approvals/approve").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"a-1\",\"a-2\",\"a-3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approved[0]").value("a-1"))
                .andExpect(jsonPath("$.skipped[0].id").value("a-2"))
                .andExpect(jsonPath("$.skipped[0].reason").value("SELF"))
                .andExpect(jsonPath("$.skipped[1].reason").value("NOT_AWAITING"))
                .andExpect(jsonPath("$.skipped[1].decidedByName").value("Outro Lead"));
        verify(decideApproval, never()).approve(any(), any());
    }

    @Test
    void approveBatch_answers400_forNoIdsOrMoreThan50() throws Exception {
        mockMvc.perform(post("/api/v1/ai/allocations/approvals/approve").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[]}")).andExpect(status().isBadRequest());
        String tooMany = IntStream.range(0, 51).mapToObj(i -> "\"a" + i + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{\"ids\":[", "]}"));
        mockMvc.perform(post("/api/v1/ai/allocations/approvals/approve").contentType(MediaType.APPLICATION_JSON)
                .content(tooMany)).andExpect(status().isBadRequest());
        verifyNoInteractions(decideApproval);
    }

    @Test
    void return_answers204_andPassesTheNote() throws Exception {
        mockMvc.perform(post("/api/v1/ai/allocations/a-1/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Separe o estudo\"}"))
                .andExpect(status().isNoContent());
        verify(decideApproval).returnToOwner(lead, "a-1", "Separe o estudo");
    }

    @Test
    void return_answers400_forBlankOrTooLongNote() throws Exception {
        mockMvc.perform(post("/api/v1/ai/allocations/a-1/return").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"  \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/ai/allocations/a-1/return").contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"" + "x".repeat(281) + "\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(decideApproval);
    }

    @Test
    void return_answers403Self() throws Exception {
        doThrow(new AllocationApprovalSelfException("s")).when(decideApproval).returnToOwner(lead, "s", "nota");

        mockMvc.perform(post("/api/v1/ai/allocations/s/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"nota\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ALLOCATION_APPROVAL_SELF"));
    }
}
