package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateWorkItemMetricsUseCaseTest {

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant BASE = Instant.parse("2025-01-01T00:00:00Z");

    @Mock
    private WorkItemRepository workItemRepository;

    private CalculateWorkItemMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CalculateWorkItemMetricsUseCase(workItemRepository);
    }

    @Test
    void computesCorrectMetricsForCompletedItem() {
        Instant createdAt = BASE;
        Instant startedAt = BASE.plusSeconds(3600);   // +1h
        Instant completedAt = BASE.plusSeconds(7200);  // +2h
        WorkItem item = workItem("wi-1", createdAt, startedAt, completedAt);

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(item));

        useCase.execute(ORG_ID, PROJECT_ID);

        ArgumentCaptor<Double> cycleCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> leadCaptor = ArgumentCaptor.forClass(Double.class);
        verify(workItemRepository).updateMetrics(eq("wi-1"), cycleCaptor.capture(), leadCaptor.capture());

        assertThat(cycleCaptor.getValue()).isEqualTo(1.0); // 1h cycle
        assertThat(leadCaptor.getValue()).isEqualTo(2.0);  // 2h lead
    }

    @Test
    void returnsNullCycleTimeWhenStartedAtIsNull() {
        WorkItem item = workItem("wi-2", BASE, null, BASE.plusSeconds(7200));

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(item));

        useCase.execute(ORG_ID, PROJECT_ID);

        verify(workItemRepository).updateMetrics(eq("wi-2"), isNull(), notNull());
    }

    @Test
    void returnsNullBothWhenCompletedAtIsNull() {
        WorkItem item = workItem("wi-3", BASE, BASE.plusSeconds(3600), null);

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(item));

        useCase.execute(ORG_ID, PROJECT_ID);

        verify(workItemRepository).updateMetrics(eq("wi-3"), isNull(), isNull());
    }

    @Test
    void returnsZeroCycleTimeWhenStartedEqualsCompleted() {
        WorkItem item = workItem("wi-4", BASE, BASE.plusSeconds(3600), BASE.plusSeconds(3600));

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(item));

        useCase.execute(ORG_ID, PROJECT_ID);

        ArgumentCaptor<Double> cycleCaptor = ArgumentCaptor.forClass(Double.class);
        verify(workItemRepository).updateMetrics(eq("wi-4"), cycleCaptor.capture(), notNull());
        assertThat(cycleCaptor.getValue()).isEqualTo(0.0);
    }

    @Test
    void doesNothingWhenNoWorkItems() {
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of());

        useCase.execute(ORG_ID, PROJECT_ID);

        verify(workItemRepository).findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID);
        verifyNoMoreInteractions(workItemRepository);
    }

    private WorkItem workItem(String id, Instant createdAt, Instant startedAt, Instant completedAt) {
        return new WorkItem(id, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                id, null, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                null, null, List.of(), null, null, null,
                createdAt, startedAt, completedAt, null, null, true, null, Instant.now());
    }
}
