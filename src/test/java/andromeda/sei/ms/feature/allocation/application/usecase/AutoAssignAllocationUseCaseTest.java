package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoAssignAllocationUseCaseTest {

    @Mock AiUsageAllocationRepository allocationRepository;
    @Mock SprintRepository sprintRepository;
    @Mock WorkItemRepository workItemRepository;

    AutoAssignAllocationUseCase useCase;

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new AutoAssignAllocationUseCase(allocationRepository, sprintRepository, workItemRepository);
    }

    @Test
    void skips_when_validated_allocation_exists() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(true);

        useCase.execute(ORG, USER, DAY);

        verify(sprintRepository, never()).findCurrentSprintsByOrganizationId(anyString());
        verify(allocationRepository, never()).saveAll(any());
    }

    @Test
    void creates_no_allocations_when_no_in_progress_items() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(false);
        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of());

        useCase.execute(ORG, USER, DAY);

        verify(allocationRepository, never()).saveAll(any());
    }

    @Test
    void creates_100_percent_allocation_for_single_item() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(false);
        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint));
        WorkItem item = workItem("wi-1", "Fix login bug");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(item));

        useCase.execute(ORG, USER, DAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiUsageAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(captor.capture());

        List<AiUsageAllocation> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPercentage()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(saved.get(0).getStatus()).isEqualTo(AiUsageAllocation.Status.PENDING_VALIDATION);
        assertThat(saved.get(0).getAssignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.AUTO);
    }

    @Test
    void splits_evenly_for_multiple_items() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, USER, DAY)).thenReturn(false);
        Sprint sprint = sprint("sprint-1");
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(workItem("wi-1", "A"), workItem("wi-2", "B"), workItem("wi-3", "C")));

        useCase.execute(ORG, USER, DAY);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiUsageAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAll(captor.capture());

        List<AiUsageAllocation> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        BigDecimal expected = new BigDecimal("0.3333");
        saved.forEach(a -> assertThat(a.getPercentage()).isEqualByComparingTo(expected));
    }

    private Sprint sprint(String id) {
        return new Sprint(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    private WorkItem workItem(String id, String title) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), title,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", 3.0, List.of(),
                "sprint-1", "ext-user", USER,
                Instant.now(), Instant.now(), null,
                null, null, true, 1L, Instant.now());
    }
}
