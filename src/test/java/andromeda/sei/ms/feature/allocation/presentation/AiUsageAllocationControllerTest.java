package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryOutput;
import andromeda.sei.ms.feature.allocation.application.dto.PendingAllocationDayOutput;
import andromeda.sei.ms.feature.allocation.application.dto.WorkItemCandidateOutput;
import andromeda.sei.ms.feature.allocation.application.usecase.AcceptAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.ConfirmAllocationDaysUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationCandidatesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationHistoryUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationBatchesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.ManualAllocateUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.SaveAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationInvalidSumException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiUsageAllocationControllerTest {

    @Mock GetPendingAllocationsUseCase getPendingAllocations;
    @Mock GetAllocationCandidatesUseCase getAllocationCandidates;
    @Mock SaveAllocationUseCase saveAllocation;
    @Mock GetPendingAllocationBatchesUseCase getPendingAllocationBatches;
    @Mock AcceptAllocationUseCase acceptAllocation;
    @Mock ConfirmAllocationDaysUseCase confirmAllocationDays;
    @Mock ManualAllocateUseCase manualAllocate;
    @Mock GetAllocationHistoryUseCase getAllocationHistory;
    @Mock andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions currentUserPermissions;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AiUsageAllocationController controller = new AiUsageAllocationController(
                getPendingAllocations, getAllocationCandidates, saveAllocation,
                getPendingAllocationBatches, acceptAllocation, confirmAllocationDays, manualAllocate, getAllocationHistory,
                currentUserPermissions);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        TenantContextHolder.set("org-1");

        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1").permissions(List.of("AI_READ_OWN"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void pendingBatches_exposesCacheTokens() throws Exception {
        when(getPendingAllocationBatches.execute(anyString(), anyString())).thenReturn(List.of(
                new andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput(
                        LocalDate.of(2026, 6, 3), 10, 5, 3000, 400, 2, java.math.BigDecimal.ONE, null, List.of(), List.of(),
                        new andromeda.sei.ms.feature.allocation.application.dto.ReviewOutput("SUGGESTED", List.of(), List.of()))));

        mockMvc.perform(get("/api/v1/ai/allocations/pending-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalCacheReadTokens").value(3000))
                .andExpect(jsonPath("$[0].totalCacheWriteTokens").value(400));
    }

    @Test
    void getCandidates_returns_complexity_and_effort_without_storyPoints() throws Exception {
        when(getAllocationCandidates.execute(anyString(), anyString(), any(LocalDate.class))).thenReturn(List.of(
                new WorkItemCandidateOutput("wi-1", "T", "Sprint 1", "STORY", "10", 3.0, 12.0)));

        mockMvc.perform(get("/api/v1/ai/allocations/2026-06-03/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storyPoints").doesNotExist())
                .andExpect(jsonPath("$[0].complexity").value(3.0))
                .andExpect(jsonPath("$[0].effort").value(12.0));
    }

    @Test
    void getPending_returns_list() throws Exception {
        PendingAllocationDayOutput output = new PendingAllocationDayOutput(
                LocalDate.of(2026, 6, 3),
                AiUsageAllocation.Status.PENDING_VALIDATION,
                List.of(new AllocationEntryOutput("wi-1", "Fix login", BigDecimal.ONE, AiUsageAllocation.AssignedBy.AUTO))
        );
        when(getPendingAllocations.execute(anyString(), anyString())).thenReturn(List.of(output));

        mockMvc.perform(get("/api/v1/ai/allocations/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].day").value("2026-06-03"))
                .andExpect(jsonPath("$[0].status").value("PENDING_VALIDATION"));
    }

    @Test
    void save_returns_204_on_success() throws Exception {
        String body = """
                [{"workItemId":"wi-1","workItemTitle":"Fix login","percentage":1.0}]
                """;

        mockMvc.perform(put("/api/v1/ai/allocations/2026-06-03")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    @Test
    void save_returns_422_on_invalid_sum() throws Exception {
        doThrow(new AllocationInvalidSumException())
                .when(saveAllocation).execute(anyString(), anyString(), any(), any());

        String body = """
                [{"workItemId":"wi-1","workItemTitle":"Fix login","percentage":0.5}]
                """;

        mockMvc.perform(put("/api/v1/ai/allocations/2026-06-03")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALLOCATION_INVALID_SUM"));
    }

    private static final String DAY_BODY = """
            {"day":"2026-06-03","splits":[{"workItemId":"wi-1","workItemTitle":"Fix login","percentage":1.0}]}
            """;

    @Test
    void manualForDay_returns_204_on_success() throws Exception {
        mockMvc.perform(post("/api/v1/ai/allocations/manual")
                        .contentType(MediaType.APPLICATION_JSON).content(DAY_BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void manualForDay_returns_409_when_day_already_validated() throws Exception {
        doThrow(new andromeda.sei.ms.feature.allocation.domain.exception.AllocationAlreadyValidatedException("day"))
                .when(manualAllocate).execute(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/ai/allocations/manual")
                        .contentType(MediaType.APPLICATION_JSON).content(DAY_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALLOCATION_ALREADY_VALIDATED"));
    }

    @Test
    void manualForDay_returns_422_when_nothing_to_allocate() throws Exception {
        doThrow(new andromeda.sei.ms.feature.allocation.domain.exception.AllocationNothingToAllocateException(
                LocalDate.of(2026, 6, 3)))
                .when(manualAllocate).execute(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/ai/allocations/manual")
                        .contentType(MediaType.APPLICATION_JSON).content(DAY_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALLOCATION_NOTHING_TO_ALLOCATE"));
    }

    @Test
    void manualForDay_returns_404_when_work_item_is_not_of_the_organization() throws Exception {
        doThrow(new andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException("wi-1"))
                .when(manualAllocate).execute(anyString(), anyString(), any(), any(), any());

        mockMvc.perform(post("/api/v1/ai/allocations/manual")
                        .contentType(MediaType.APPLICATION_JSON).content(DAY_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORK_ITEM_NOT_FOUND"));
    }

    @Test
    void save_returns_422_when_work_item_type_is_not_eligible() throws Exception {
        doThrow(new andromeda.sei.ms.feature.allocation.domain.exception.AllocationWorkItemTypeNotEligibleException("Feature"))
                .when(saveAllocation).execute(anyString(), anyString(), any(), any());

        String body = """
                [{"workItemId":"wi-1","workItemTitle":"Fix login","percentage":1.0}]
                """;

        mockMvc.perform(put("/api/v1/ai/allocations/2026-06-03")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALLOCATION_WORK_ITEM_TYPE_NOT_ELIGIBLE"));
    }

    // ── POST /confirm (atribuicao-a-prova-de-esquecimento 1.3) ─────────────────────────────

    private org.springframework.test.web.servlet.ResultActions confirm(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/ai/allocations/confirm")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void confirm_returns_confirmedAndSkipped() throws Exception {
        when(confirmAllocationDays.execute("org-1", "user-1",
                List.of(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)), false))
                .thenReturn(new ConfirmAllocationDaysUseCase.Result(List.of(LocalDate.of(2026, 10, 27)),
                        List.of(new ConfirmAllocationDaysUseCase.Skipped(LocalDate.of(2026, 10, 28),
                                ConfirmAllocationDaysUseCase.SkipReason.NEEDS_REVIEW))));

        confirm("{\"days\":[\"2026-10-27\",\"2026-10-28\"],\"includeReview\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed[0]").value("2026-10-27"))
                .andExpect(jsonPath("$.skipped[0].day").value("2026-10-28"))
                .andExpect(jsonPath("$.skipped[0].reason").value("NEEDS_REVIEW"));
    }

    @Test
    void confirm_passesIncludeReview() throws Exception {
        when(confirmAllocationDays.execute("org-1", "user-1", List.of(LocalDate.of(2026, 10, 27)), true))
                .thenReturn(new ConfirmAllocationDaysUseCase.Result(List.of(LocalDate.of(2026, 10, 27)), List.of()));

        confirm("{\"days\":[\"2026-10-27\"],\"includeReview\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmed[0]").value("2026-10-27"));
    }

    @Test
    void confirm_accepts31Days() throws Exception {
        when(confirmAllocationDays.execute(anyString(), anyString(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new ConfirmAllocationDaysUseCase.Result(List.of(), List.of()));
        confirm(daysBody(31)).andExpect(status().isOk());
    }

    @Test
    void confirm_returns400_with32Days() throws Exception {
        confirm(daysBody(32)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirm_returns400_withoutDays() throws Exception {
        confirm("{\"days\":[],\"includeReview\":false}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirm_returns400_withRepeatedDays() throws Exception {
        confirm("{\"days\":[\"2026-10-27\",\"2026-10-27\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void confirm_returns400_withMalformedOrImpossibleDate() throws Exception {
        confirm("{\"days\":[\"27/10/2026\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        confirm("{\"days\":[\"2026-02-30\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private static String daysBody(int n) {
        StringBuilder sb = new StringBuilder("{\"days\":[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(LocalDate.of(2026, 1, 1).plusDays(i)).append('"');
        }
        return sb.append("]}").toString();
    }
}
