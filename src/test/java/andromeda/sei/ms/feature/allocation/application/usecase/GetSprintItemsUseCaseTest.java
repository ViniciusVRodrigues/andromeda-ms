package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.dto.SprintItemOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSprintItemsUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    @Mock private SprintRepository sprintRepository;
    @Mock private WorkItemRepository workItemRepository;

    @Test
    void should_ignoreInactiveWorkItems() {
        Sprint sprint = new Sprint("sprint-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint));
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS))
                .thenReturn(List.of(story("wi-active", true), story("wi-removed", false)));

        List<SprintItemOutput> result = useCase()
                .execute(ORG, USER);

        assertThat(result).extracting(SprintItemOutput::workItemId).containsExactly("wi-active");
    }

    private GetSprintItemsUseCase useCase() {
        return new GetSprintItemsUseCase(sprintRepository, workItemRepository,
                new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver());
    }

    private Sprint sprint() {
        return new Sprint("sprint-1", ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", "team-1", "Team A", "Sprint 23", "/path",
                Instant.now().minusSeconds(86400), Instant.now().plusSeconds(86400),
                Sprint.TimeFrame.CURRENT, true, 1L, Instant.now(), Instant.now());
    }

    private WorkItem item(String id, WorkItem.ResolvedType type, String parent, Double complexity, Double effort) {
        WorkItem wi = story(id, true);
        wi.setResolvedType(type);
        wi.setParentExternalId(parent);
        wi.setComplexity(complexity);
        wi.setEffort(effort);
        return wi;
    }

    @Test
    void should_returnComplexityAndTaskEffort_forStoryWithTasks() {
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint()));
        WorkItem story = item("s1", WorkItem.ResolvedType.STORY, null, 3.0, null);
        WorkItem t1 = item("t1", WorkItem.ResolvedType.TASK, "s1", null, 4.0);
        WorkItem t2 = item("t2", WorkItem.ResolvedType.TASK, "s1", null, 8.0);
        t1.setAssigneeUserId("other");
        t2.setAssigneeUserId("other");
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(story));
        when(workItemRepository.findAllByOrganizationIdAndProjectIdAndSprintId(ORG, "proj-1", "sprint-1"))
                .thenReturn(List.of(story, t1, t2));

        List<SprintItemOutput> result = useCase().execute(ORG, USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).complexity()).isEqualTo(3.0);
        assertThat(result.get(0).effort()).isEqualTo(12.0);
    }

    @Test
    void should_returnOnlyEffort_forTaskAlone() {
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint()));
        WorkItem task = item("t1", WorkItem.ResolvedType.TASK, null, null, 4.0);
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(task));

        SprintItemOutput out = useCase().execute(ORG, USER).get(0);

        assertThat(out.effort()).isEqualTo(4.0);
        assertThat(out.complexity()).isNull();
    }

    @Test
    void should_returnNulls_forItemWithNothing() {
        when(sprintRepository.findCurrentSprintsByOrganizationId(ORG)).thenReturn(List.of(sprint()));
        WorkItem bare = item("s1", WorkItem.ResolvedType.STORY, null, null, null);
        when(workItemRepository.findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                ORG, "sprint-1", USER, WorkItem.ResolvedStatus.IN_PROGRESS)).thenReturn(List.of(bare));

        SprintItemOutput out = useCase().execute(ORG, USER).get(0);

        assertThat(out.complexity()).isNull();
        assertThat(out.effort()).isNull();
    }

    private WorkItem story(String id, boolean active) {
        return new WorkItem(id, ORG, "proj-1", WorkItem.Source.AZURE_DEVOPS,
                id, Instant.now(), "Title " + id,
                "Story", WorkItem.ResolvedType.STORY,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", List.of(),
                "sprint-1", "ext-user", USER, null, null,
                Instant.now(), Instant.now(), null,
                null, null, active, 1L, Instant.now(), null, null, null, null, null, null, null, null);
    }
}
