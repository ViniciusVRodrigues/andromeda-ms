package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotFoundException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ListAgentAllocationRowsUseCaseTest {

    static final String ORG = "org-1";
    static final String USER = "user-1";
    static final Instant NOW = Instant.parse("2026-10-29T15:30:00Z");

    AiUsageAllocationRepository allocations = mock(AiUsageAllocationRepository.class);
    AgentUsageQueries queries = mock(AgentUsageQueries.class);
    ListAgentAllocationRowsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListAgentAllocationRowsUseCase(allocations, queries);
    }

    AiUsageAllocation allocation(AiUsageAllocation.AssignedBy by, String owner, boolean active) {
        return new AiUsageAllocation("alloc-1", ORG, owner, LocalDate.of(2026, 10, 29), List.of("u1"), List.of(),
                AiUsageAllocation.Status.VALIDATED, by, active, 0L, NOW, NOW);
    }

    AIUsage row(String id) {
        AIUsage u = new AIUsage(id, ORG, USER, null, "agent", LocalDate.of(2026, 10, 29), "claude-opus", null,
                "github.com/a/b", "dev-1", "NOTE", 1, 2, 1, "claude-code", "alloc-1", null, NOW, true, 0L, NOW, NOW);
        u.setBucketStart(NOW);
        u.setTool("claude-code");
        u.setBranch("main");
        return u;
    }

    @Test
    void should_returnTheRows_when_theAgentAllocationIsOfTheUser() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, true)));
        when(queries.findActiveRowsOfAllocation(ORG, USER, "alloc-1")).thenReturn(List.of(row("u1")));

        List<AIUsage> rows = useCase.execute(ORG, USER, "alloc-1");

        assertThat(rows).extracting(AIUsage::getId).containsExactly("u1");
        verify(queries).findActiveRowsOfAllocation(ORG, USER, "alloc-1");
    }

    @Test
    void should_fail404_when_theAllocationIsOfAnotherUser() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, "other", true)));
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1"))
                .isInstanceOf(AiAllocationNotFoundException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void should_fail404_when_theAllocationIsManualOrAuto() {
        for (AiUsageAllocation.AssignedBy by : new AiUsageAllocation.AssignedBy[]{
                AiUsageAllocation.AssignedBy.MANUAL, AiUsageAllocation.AssignedBy.AUTO}) {
            when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(allocation(by, USER, true)));
            assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1"))
                    .isInstanceOf(AiAllocationNotFoundException.class);
        }
        verifyNoInteractions(queries);
    }

    @Test
    void should_fail404_when_theAllocationIsInactive() {
        when(allocations.findById("alloc-1", ORG)).thenReturn(Optional.of(
                allocation(AiUsageAllocation.AssignedBy.AGENT, USER, false)));
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "alloc-1"))
                .isInstanceOf(AiAllocationNotFoundException.class);
        verifyNoInteractions(queries);
    }

    @Test
    void should_fail404_when_theAllocationDoesNotExistInTheOrganization() {
        when(allocations.findById("alloc-1", "org-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute("org-2", USER, "alloc-1"))
                .isInstanceOf(AiAllocationNotFoundException.class);
        verify(allocations).findById("alloc-1", "org-2");
        verifyNoInteractions(queries);
    }
}
