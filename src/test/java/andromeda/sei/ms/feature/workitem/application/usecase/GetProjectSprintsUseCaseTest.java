package andromeda.sei.ms.feature.workitem.application.usecase;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** metrics-correctness 1.3 — the sprint selector lists a shared iteration once, via its canonical twin. */
@ExtendWith(MockitoExtension.class)
class GetProjectSprintsUseCaseTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final Instant T0 = Instant.parse("2026-06-01T00:00:00Z");

    @Mock private SprintRepository sprintRepository;
    @Mock private ProjectRepository projectRepository;

    @InjectMocks private GetProjectSprintsUseCase useCase;

    @Test
    void should_listSharedIterationOnce_throughItsCanonicalTwin() {
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(
                new Project(PROJECT, ORG, "P", "p", "d", true, new ArrayList<>(), 0L, T0, T0)));
        Sprint teamB = sprint("s-25-b", "team-b", "Proj\\Sprint 25", T0.plusSeconds(60), Sprint.TimeFrame.CURRENT);
        Sprint teamA = sprint("s-25-a", "team-a", "Proj\\Sprint 25", T0, Sprint.TimeFrame.CURRENT);
        Sprint past = sprint("s-24", "team-a", "Proj\\Sprint 24", T0, Sprint.TimeFrame.PAST);
        when(sprintRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(teamB, past, teamA));

        assertThat(useCase.execute(ORG, PROJECT)).extracting(Sprint::getId).containsExactly("s-25-a", "s-24");
    }

    private static Sprint sprint(String id, String teamId, String path, Instant createdAt, Sprint.TimeFrame timeFrame) {
        return new Sprint(id, ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS, "iter-" + path, teamId, teamId, path, path,
                T0, T0.plusSeconds(1_209_600), timeFrame, true, 0L, createdAt, createdAt);
    }
}
