package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.PendingBatchOutput;
import andromeda.sei.ms.feature.allocation.application.dto.ReviewOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SuggestionItemOutput;
import andromeda.sei.ms.feature.allocation.application.usecase.AcceptAllocationUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.ConfirmAllocationDaysUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationCandidatesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetAllocationHistoryUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationBatchesUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.GetPendingAllocationsUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.ManualAllocateUseCase;
import andromeda.sei.ms.feature.allocation.application.usecase.SaveAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** uso-ia-fora-de-entrega P1: the split with kind/category/note over the wire (standalone MockMvc, like the siblings). */
@ExtendWith(MockitoExtension.class)
class AiUsageAllocationControllerOutOfDeliveryTest {

    @Mock GetPendingAllocationsUseCase getPendingAllocations;
    @Mock GetAllocationCandidatesUseCase getAllocationCandidates;
    @Mock SaveAllocationUseCase saveAllocation;
    @Mock GetPendingAllocationBatchesUseCase getPendingAllocationBatches;
    @Mock AcceptAllocationUseCase acceptAllocation;
    @Mock ConfirmAllocationDaysUseCase confirmAllocationDays;
    @Mock ManualAllocateUseCase manualAllocate;
    @Mock GetAllocationHistoryUseCase getAllocationHistory;
    @Mock andromeda.sei.ms.feature.allocation.application.usecase.PreviewAllocationApprovalUseCase previewApproval;
    @Mock andromeda.sei.ms.feature.allocation.application.usecase.GetOutOfDeliveryBudgetUseCase getOutOfDeliveryBudget;
    @Mock andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions currentUserPermissions;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AiUsageAllocationController controller = new AiUsageAllocationController(
                getPendingAllocations, getAllocationCandidates, saveAllocation,
                getPendingAllocationBatches, acceptAllocation, confirmAllocationDays, manualAllocate, getAllocationHistory,
                previewApproval, getOutOfDeliveryBudget, currentUserPermissions);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
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
    @SuppressWarnings("unchecked")
    void manual_acceptsAnOutOfDeliverySplit_withoutWorkItemId() throws Exception {
        String body = """
                {"day":"2026-06-03","splits":[
                  {"workItemId":"wi-1","workItemTitle":"Fix","percentage":0.7},
                  {"kind":"OUT_OF_DELIVERY","category":"STUDY","note":"curso","percentage":0.3}]}
                """;
        when(manualAllocate.execute(any(), any(), any(), any(), any())).thenReturn(
                new ManualAllocateUseCase.Result("alloc-1", AiUsageAllocation.Status.VALIDATED, null));

        mockMvc.perform(post("/api/v1/ai/allocations/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<List<AllocationSplit>> captor = ArgumentCaptor.forClass(List.class);
        verify(manualAllocate).execute(eq("org-1"), eq("user-1"), any(), eq(LocalDate.of(2026, 6, 3)), captor.capture());
        AllocationSplit item = captor.getValue().get(0);
        AllocationSplit out = captor.getValue().get(1);
        assertThat(item.getKind()).isEqualTo(AllocationSplit.Kind.ITEM);
        assertThat(out.getKind()).isEqualTo(AllocationSplit.Kind.OUT_OF_DELIVERY);
        assertThat(out.getCategory()).isEqualTo(AllocationSplit.Category.STUDY);
        assertThat(out.getNote()).isEqualTo("curso");
        assertThat(out.getWorkItemId()).isNull();
    }

    @Test
    void manual_answers422WithDetail_whenTheSplitShapeIsInvalid() throws Exception {
        doThrow(new AllocationSplitsInvalidException("note is required when category is OTHER"))
                .when(manualAllocate).execute(anyString(), anyString(), any(), any(), any());
        String body = """
                {"day":"2026-06-03","splits":[{"kind":"OUT_OF_DELIVERY","category":"OTHER","percentage":1.0}]}
                """;

        mockMvc.perform(post("/api/v1/ai/allocations/manual").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ALLOCATION_SPLITS_INVALID"))
                .andExpect(jsonPath("$.message").value("note is required when category is OTHER"));
    }

    @Test
    void pendingBatches_returnsKindCategoryAndNoteOfTheSuggestion() throws Exception {
        when(getPendingAllocationBatches.execute(anyString(), anyString())).thenReturn(List.of(
                new PendingBatchOutput(LocalDate.of(2026, 6, 3), 10, 5, 0, 0, 2, BigDecimal.ONE, "a1", List.of("a1"),
                        List.of(new SuggestionItemOutput("wi-1", "Fix", new BigDecimal("0.7000"), "AB#1", null, null),
                                new SuggestionItemOutput(null, null, new BigDecimal("0.3000"), null, null, null,
                                        AllocationSplit.Kind.OUT_OF_DELIVERY, AllocationSplit.Category.SUPPORT, "ajuda")),
                        new ReviewOutput("SUGGESTED", List.of(), List.of()))));

        mockMvc.perform(get("/api/v1/ai/allocations/pending-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].suggestion[0].kind").value("ITEM"))
                .andExpect(jsonPath("$[0].suggestion[1].kind").value("OUT_OF_DELIVERY"))
                .andExpect(jsonPath("$[0].suggestion[1].category").value("SUPPORT"))
                .andExpect(jsonPath("$[0].suggestion[1].note").value("ajuda"));
    }
}
