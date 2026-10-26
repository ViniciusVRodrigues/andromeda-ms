package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.WorkItemCandidateOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAllocationCandidatesUseCaseTest {

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;

    private GetAllocationCandidatesUseCase useCase;

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final LocalDate DAY = LocalDate.of(2026, 6, 3);

    @BeforeEach
    void setUp() {
        useCase = new GetAllocationCandidatesUseCase(sprintRepository, workItemRepository, new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver());
    }

    @Test
    void should_includeFeatureTypedWorkItem_asCandidate_notDropIt() {
        Sprint sprint = new Sprint("sprint-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));

        WorkItem feature = new WorkItem("wi-feat", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "wi-feat", Instant.now(), "Feature Title",
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", 3.0, List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, null, null, null, null);

        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, "sprint-1"))
                .thenReturn(List.of(feature));

        List<WorkItemCandidateOutput> result = useCase.execute(ORG, USER, DAY);

        // A FEATURE-typed item must be returned as a candidate, not silently dropped (empty list).
        assertThat(result).hasSize(1);
        assertThat(result.get(0).workItemId()).isEqualTo("wi-feat");
    }

    @Test
    void should_ignoreInactiveWorkItems() {
        Sprint sprint = new Sprint("sprint-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint));
        WorkItem active = story("wi-active", true);
        WorkItem removed = story("wi-removed", false);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, "sprint-1"))
                .thenReturn(List.of(active, removed));

        List<WorkItemCandidateOutput> result = useCase.execute(ORG, USER, DAY);

        assertThat(result).extracting(WorkItemCandidateOutput::workItemId).containsExactly("wi-active");
    }

    private Sprint sprint() {
        return new Sprint("sprint-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    private WorkItem item(String id, WorkItem.ResolvedType type, String parent, Double complexity, Double effort, Double sp) {
        WorkItem wi = story(id, true);
        wi.setResolvedType(type);
        wi.setParentExternalId(parent);
        wi.setComplexity(complexity);
        wi.setEffort(effort);
        wi.setStoryPoints(sp);
        return wi;
    }

    @Test
    void should_returnComplexityAndTaskEffort_forStoryWithTasks() {
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint()));
        // own effort keeps the level at STORY (candidate selection is unchanged by this change)
        WorkItem story = item("s1", WorkItem.ResolvedType.STORY, null, 3.0, 3.0, 3.0);
        WorkItem t1 = item("t1", WorkItem.ResolvedType.TASK, "s1", null, 4.0, null);
        WorkItem t2 = item("t2", WorkItem.ResolvedType.TASK, "s1", null, 8.0, null);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, "sprint-1"))
                .thenReturn(List.of(story, t1, t2));

        List<WorkItemCandidateOutput> result = useCase.execute(ORG, USER, DAY);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexity()).isEqualTo(3.0);
        assertThat(result.get(0).effort()).isEqualTo(12.0);
        assertThat(result.get(0).storyPoints()).isEqualTo(3.0);
    }

    @Test
    void should_returnOnlyEffort_forTaskAlone() {
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint()));
        WorkItem task = item("t1", WorkItem.ResolvedType.TASK, null, null, 4.0, null);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, "sprint-1"))
                .thenReturn(List.of(task));

        WorkItemCandidateOutput out = useCase.execute(ORG, USER, DAY).get(0);

        assertThat(out.effort()).isEqualTo(4.0);
        assertThat(out.complexity()).isNull();
    }

    @Test
    void should_returnNulls_forItemWithNothing() {
        when(sprintRepository.findByOrganizationIdAndDay(ORG, DAY)).thenReturn(List.of(sprint()));
        WorkItem bare = item("s1", WorkItem.ResolvedType.STORY, null, null, null, null);
        when(workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(ORG, USER, "sprint-1"))
                .thenReturn(List.of(bare));

        WorkItemCandidateOutput out = useCase.execute(ORG, USER, DAY).get(0);

        assertThat(out.complexity()).isNull();
        assertThat(out.effort()).isNull();
    }

    private WorkItem story(String id, boolean active) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Title " + id,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", 3.0, List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, null, active, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }
}
