package andromeda.sei.ms.feature.allocation.presentation;

import andromeda.sei.ms.feature.allocation.application.dto.AllocationEntryOutput;
import andromeda.sei.ms.feature.allocation.application.dto.PendingAllocationDayOutput;
import andromeda.sei.ms.feature.allocation.application.dto.WorkItemCandidateOutput;
import andromeda.sei.ms.feature.allocation.application.usecase.AcceptAllocationUseCase;
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
    @Mock ManualAllocateUseCase manualAllocate;
    @Mock GetAllocationHistoryUseCase getAllocationHistory;
    @Mock andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions currentUserPermissions;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AiUsageAllocationController controller = new AiUsageAllocationController(
                getPendingAllocations, getAllocationCandidates, saveAllocation,
                getPendingAllocationBatches, acceptAllocation, manualAllocate, getAllocationHistory,
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
}
