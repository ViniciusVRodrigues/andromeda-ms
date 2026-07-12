package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateSprintMetricsUseCaseTest {

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Mock private WorkItemRepository workItemRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private SprintMetricsRepository sprintMetricsRepository;

    private CalculateSprintMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CalculateSprintMetricsUseCase(workItemRepository, sprintRepository, sprintMetricsRepository);
        when(sprintMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void countsVelocityOnlyForDoneItemsWithPoints() {
        Sprint sprint = sprint(SPRINT_ID);
        WorkItem doneWithPoints = workItem("wi-1", WorkItem.ResolvedStatus.DONE, 5.0, 10.0, 20.0, SPRINT_ID);
        WorkItem inProgressWithPoints = workItem("wi-2", WorkItem.ResolvedStatus.IN_PROGRESS, 3.0, null, null, SPRINT_ID);
        WorkItem doneNoPoints = workItem("wi-3", WorkItem.ResolvedStatus.DONE, null, 8.0, 15.0, SPRINT_ID);

        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(doneWithPoints, inProgressWithPoints, doneNoPoints));

        useCase.execute(ORG_ID, PROJECT_ID);

        ArgumentCaptor<SprintMetrics> captor = ArgumentCaptor.forClass(SprintMetrics.class);
        verify(sprintMetricsRepository).save(captor.capture());
        SprintMetrics saved = captor.getValue();

        assertThat(saved.getVelocity()).isEqualTo(5.0);
        assertThat(saved.getCompletedItemCount()).isEqualTo(2);
        assertThat(saved.getTotalItemCount()).isEqualTo(3);
        assertThat(saved.getItemsWithPoints()).isEqualTo(2);
    }

    @Test
    void avgCycleTimeIsNullWhenNoCompletedItemsHaveCycleTime() {
        Sprint sprint = sprint(SPRINT_ID);
        WorkItem inProgress = workItem("wi-1", WorkItem.ResolvedStatus.IN_PROGRESS, null, null, null, SPRINT_ID);

        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID))
                .thenReturn(List.of(inProgress));

        useCase.execute(ORG_ID, PROJECT_ID);

        ArgumentCaptor<SprintMetrics> captor = ArgumentCaptor.forClass(SprintMetrics.class);
        verify(sprintMetricsRepository).save(captor.capture());
        SprintMetrics saved = captor.getValue();

        assertThat(saved.getAvgCycleTime()).isNull();
        assertThat(saved.getAvgLeadTime()).isNull();
        assertThat(saved.getVelocity()).isEqualTo(0.0);
        assertThat(saved.getCompletedItemCount()).isEqualTo(0);
    }

    @Test
    void sprintWithZeroItemsProducesZeroOrNullValues() {
        Sprint sprint = sprint(SPRINT_ID);

        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(sprint));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of());

        useCase.execute(ORG_ID, PROJECT_ID);

        ArgumentCaptor<SprintMetrics> captor = ArgumentCaptor.forClass(SprintMetrics.class);
        verify(sprintMetricsRepository).save(captor.capture());
        SprintMetrics saved = captor.getValue();

        assertThat(saved.getVelocity()).isEqualTo(0.0);
        assertThat(saved.getAvgCycleTime()).isNull();
        assertThat(saved.getAvgLeadTime()).isNull();
        assertThat(saved.getCompletedItemCount()).isEqualTo(0);
        assertThat(saved.getTotalItemCount()).isEqualTo(0);
        assertThat(saved.getItemsWithPoints()).isEqualTo(0);
    }

    private Sprint sprint(String id) {
        return new Sprint(id, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + id, "team-1", "Team One", "Sprint 1", "/path",
                NOW, NOW.plusSeconds(1_209_600), Sprint.TimeFrame.PAST,
                true, null, NOW, NOW);
    }

    private WorkItem workItem(String id, WorkItem.ResolvedStatus status, Double storyPoints,
                               Double cycleTimeHours, Double leadTimeHours, String sprintId) {
        WorkItem item = new WorkItem(id, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                id, NOW, "Title", "Story", WorkItem.ResolvedType.STORY,
                status.name(), status, null, storyPoints, List.of(),
                sprintId, null, null, null, null, NOW, null,
                status == WorkItem.ResolvedStatus.DONE ? NOW.plusSeconds(3600) : null,
                cycleTimeHours, leadTimeHours, null, true, null, NOW, null, null, null, null, null, null, null, null);
        return item;
    }
}
