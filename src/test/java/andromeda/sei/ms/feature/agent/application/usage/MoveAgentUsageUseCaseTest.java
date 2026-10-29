package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTaskNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotAgentException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiUsageNotInAllocationException;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveAgentUsageUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");
    static final LocalDate DAY = LocalDate.of(2026, 10, 29);

    AiUsageAllocationRepository allocations = mock(AiUsageAllocationRepository.class);
    ResolveAgentTaskUseCase resolveTask = mock(ResolveAgentTaskUseCase.class);
    AgentUsageQueries queries = mock(AgentUsageQueries.class);
    CalculateAiRatiosUseCase ratios = mock(CalculateAiRatiosUseCase.class);
    WorkItemRepository workItems = mock(WorkItemRepository.class);
    MoveAgentUsageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new MoveAgentUsageUseCase(allocations, resolveTask, queries, ratios, workItems);
        when(allocations.save(any())).thenAnswer(inv -> {
            AiUsageAllocation a = inv.getArgument(0);
            if (a.getId() == null) a.setId("alloc-new");
            return a;
        });
        when(resolveTask.resolveItem(ORG, USER, "wi-200")).thenReturn(Optional.of(
                new ResolvedTask("wi-200", "proj-2", "Task 200", "AB#200", AttributionSource.TASK_REF)));
    }

    AiUsageAllocation allocation(AiUsageAllocation.AssignedBy by, String owner, String... ids) {
        return new AiUsageAllocation("alloc-1", ORG, owner, DAY, new ArrayList<>(List.of(ids)),
                List.of(new AllocationSplit("wi-123", "Task 123", new BigDecimal("1.0000"))),
                AiUsageAllocation.Status.VALIDATED, by, true, 0L, NOW, NOW);
    }

    @Test
    void should_moveToANewManualAllocationAndKeepTheRest_when_onlySomeRowsMove() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, "u1", "u2", "u3")));

        MoveAgentUsageUseCase.MoveResult r = useCase.execute(ORG, USER, "alloc-1", List.of("u1", "u2"), "wi-200");

        assertThat(r.target().assignedBy()).isEqualTo(AiUsageAllocation.AssignedBy.MANUAL);
        assertThat(r.target().status()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(r.target().movedFromAgent()).isTrue();
        assertThat(r.target().day()).isEqualTo(DAY);
        assertThat(r.target().aiUsageIds()).containsExactly("u1", "u2");
        assertThat(r.target().splits()).hasSize(1);
        assertThat(r.target().splits().get(0).workItemId()).isEqualTo("wi-200");
        assertThat(r.target().splits().get(0).percentage()).isEqualByComparingTo("1");
        assertThat(r.source().aiUsageIds()).containsExactly("u3");
        assertThat(r.source().active()).isTrue();
        verify(allocations).updateAllocationIdOnUsageRecords(List.of("u1", "u2"), "alloc-new");
        verify(queries).stampTask(eq(ORG), any(), eq("wi-200"), eq("proj-2"), eq("AB#200"), eq("MANUAL"));
        verify(ratios).execute(ORG, "proj-2");
    }

    @Test
    void should_deactivateTheAgentAllocation_when_itRunsEmpty() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, "u1")));

        MoveAgentUsageUseCase.MoveResult r = useCase.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-200");

        assertThat(r.source().active()).isFalse();
        assertThat(r.source().aiUsageIds()).isEmpty();
        ArgumentCaptor<AiUsageAllocation> saved = ArgumentCaptor.forClass(AiUsageAllocation.class);
        verify(allocations, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(1).isActive()).isFalse();
    }

    @Test
    void should_fail404_when_theAllocationDoesNotExistOrIsOfSomeoneElse() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-200"))
                .isInstanceOf(AiAllocationNotFoundException.class);

        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, "other-user", "u1")));
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-200"))
                .isInstanceOf(AiAllocationNotFoundException.class);
        verify(allocations, never()).save(any());
    }

    @Test
    void should_fail422_when_theAllocationIsNotAgent() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.MANUAL, USER, "u1")));

        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-200"))
                .isInstanceOf(AiAllocationNotAgentException.class);
        verify(allocations, never()).save(any());
    }

    @Test
    void should_fail422_when_theRowsAreNotInTheAllocationOrTheListIsEmpty() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, "u1")));

        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of("u1", "ux"), "wi-200"))
                .isInstanceOf(AiUsageNotInAllocationException.class);
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of(), "wi-200"))
                .isInstanceOf(AiUsageNotInAllocationException.class);
        verify(allocations, never()).updateAllocationIdOnUsageRecords(anyList(), anyString());
    }

    @Test
    void should_fail422_when_theDestinationTaskIsNotFound() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, "u1")));
        when(resolveTask.resolveItem(ORG, USER, "wi-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1", List.of("u1"), "wi-x"))
                .isInstanceOf(AgentTaskNotFoundException.class);
        verify(allocations, never()).save(any());
    }
}
