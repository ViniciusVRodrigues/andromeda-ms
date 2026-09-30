package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintMetrics;
import andromeda.sei.ms.feature.workitem.domain.SprintMetricsRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
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

    private static final String ORG_ID = WorkItemFixture.ORG;
    private static final String PROJECT_ID = WorkItemFixture.PROJECT;
    private static final String SPRINT_ID = "sprint-1";
    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Mock private WorkItemRepository workItemRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private SprintMetricsRepository sprintMetricsRepository;

    private CalculateSprintMetricsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CalculateSprintMetricsUseCase(workItemRepository, sprintRepository, sprintMetricsRepository,
                new EffortBearingLevelResolver());
        when(sprintMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(sprint()));
    }

    @Test
    void should_sumEffortAndComputeP50_when_sprintHasCompletedStoriesWithEffort() {
        givenItems(story("a", WorkItem.ResolvedStatus.DONE, 5.0, 10.0),
                story("b", WorkItem.ResolvedStatus.DONE, 3.0, 20.0),
                story("c", WorkItem.ResolvedStatus.DONE, 8.0, 15.0));

        SprintMetrics saved = calculate();

        assertThat(saved.getVelocity()).isEqualTo(16.0);
        assertThat(saved.getPlannedEffort()).isEqualTo(16.0);
        assertThat(saved.getCycleTimeP50()).isEqualTo(15.0);
        assertThat(saved.getCycleTimeP90()).isEqualTo(20.0);
        assertThat(saved.getAvgCycleTime()).isEqualTo(15.0);
        assertThat(saved.getCompletedItemCount()).isEqualTo(3);
        assertThat(saved.getItemsWithPoints()).isEqualTo(3);
    }

    @Test
    void should_rollTaskEffortIntoVelocity_when_teamEstimatesOnTasks() {
        WorkItem storyA = story("sa", WorkItem.ResolvedStatus.DONE, null, null);
        WorkItem storyB = story("sb", WorkItem.ResolvedStatus.IN_PROGRESS, null, null);
        givenItems(storyA, storyB,
                task("t1", WorkItem.ResolvedStatus.DONE, 2.0, storyA),
                task("t2", WorkItem.ResolvedStatus.DONE, 3.0, storyA),
                task("t3", WorkItem.ResolvedStatus.TODO, 4.0, storyB));

        SprintMetrics saved = calculate();

        assertThat(saved.getVelocity()).isEqualTo(5.0);
        assertThat(saved.getPlannedEffort()).isEqualTo(9.0);
    }

    @Test
    void should_ignoreLegacyStoryPoints_when_computingVelocity() {
        WorkItem legacy = story("a", WorkItem.ResolvedStatus.DONE, null, 10.0);
        legacy.setStoryPoints(13.0);
        givenItems(legacy);

        SprintMetrics saved = calculate();

        assertThat(saved.getVelocity()).isEqualTo(0.0);
        assertThat(saved.getItemsWithPoints()).isZero();
    }

    @Test
    void should_reportZeroVelocityAndNullPercentiles_when_nothingCompleted() {
        givenItems(story("a", WorkItem.ResolvedStatus.TODO, 3.0, null),
                story("b", WorkItem.ResolvedStatus.IN_PROGRESS, 2.0, null));

        SprintMetrics saved = calculate();

        assertThat(saved.getVelocity()).isEqualTo(0.0);
        assertThat(saved.getCycleTimeP50()).isNull();
        assertThat(saved.getLeadTimeP50()).isNull();
        assertThat(saved.getAvgCycleTime()).isNull();
        assertThat(saved.getCompletedItemCount()).isZero();
        assertThat(saved.getTotalItemCount()).isEqualTo(2);
    }

    @Test
    void should_produceZerosAndNulls_when_sprintHasNoItems() {
        givenItems();

        SprintMetrics saved = calculate();

        assertThat(saved.getVelocity()).isEqualTo(0.0);
        assertThat(saved.getPlannedEffort()).isEqualTo(0.0);
        assertThat(saved.getCycleTimeP50()).isNull();
        assertThat(saved.getTotalItemCount()).isZero();
        assertThat(saved.getItemsWithPoints()).isZero();
    }

    @Test
    void should_writeOneDocumentOnCanonicalTwin_when_twoTeamsShareTheIteration() {
        Sprint twin = new Sprint("sprint-team-b", ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + SPRINT_ID, "team-2", "Team Two", "Sprint 1", "/path",
                NOW, NOW.plusSeconds(1_209_600), Sprint.TimeFrame.PAST, true, null, NOW.plusSeconds(60), NOW);
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(twin, sprint()));
        givenItems(story("a", WorkItem.ResolvedStatus.DONE, 5.0, 10.0));

        SprintMetrics saved = calculate();

        assertThat(saved.getSprintId()).isEqualTo(SPRINT_ID);
        assertThat(saved.getVelocity()).isEqualTo(5.0);
    }

    private void givenItems(WorkItem... items) {
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID)).thenReturn(List.of(items));
    }

    private SprintMetrics calculate() {
        useCase.execute(ORG_ID, PROJECT_ID);
        ArgumentCaptor<SprintMetrics> captor = ArgumentCaptor.forClass(SprintMetrics.class);
        verify(sprintMetricsRepository).save(captor.capture());
        return captor.getValue();
    }

    private Sprint sprint() {
        return new Sprint(SPRINT_ID, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                "ext-" + SPRINT_ID, "team-1", "Team One", "Sprint 1", "/path",
                NOW, NOW.plusSeconds(1_209_600), Sprint.TimeFrame.PAST,
                true, null, NOW, NOW);
    }

    private WorkItem story(String id, WorkItem.ResolvedStatus status, Double effort, Double cycleTime) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.STORY, status, effort, null);
        item.setSprintId(SPRINT_ID);
        item.setCycleTimeHours(cycleTime);
        return item;
    }

    private WorkItem task(String id, WorkItem.ResolvedStatus status, Double effort, WorkItem parent) {
        WorkItem item = WorkItemFixture.item(id, WorkItem.ResolvedType.TASK, status, effort, parent.getExternalId());
        item.setSprintId(SPRINT_ID);
        return item;
    }
}
