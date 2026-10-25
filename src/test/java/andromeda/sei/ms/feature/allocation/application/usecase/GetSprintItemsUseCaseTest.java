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

        List<SprintItemOutput> result = new GetSprintItemsUseCase(sprintRepository, workItemRepository)
                .execute(ORG, USER);

        assertThat(result).extracting(SprintItemOutput::workItemId).containsExactly("wi-active");
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
