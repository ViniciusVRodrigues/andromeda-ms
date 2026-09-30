package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.usecase.CalculatePullRequestMetricsUseCase;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateSprintMetricsUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateTokensPerSpUseCase;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateWorkItemMetricsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectMetricsRecalculatorTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";

    @Mock private CalculateWorkItemMetricsUseCase calculateWorkItemMetricsUseCase;
    @Mock private CalculateSprintMetricsUseCase calculateSprintMetricsUseCase;
    @Mock private CalculateTokensPerSpUseCase calculateTokensPerSpUseCase;
    @Mock private CalculatePullRequestMetricsUseCase calculatePrMetricsUseCase;
    @Mock private VcsReadPort vcsReadPort;

    @InjectMocks
    private ProjectMetricsRecalculator recalculator;

    @Test
    void shouldRunEveryCalculationForTheProject() {
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(repo("repo-1"), repo("repo-2")));

        recalculator.recalculate(ORG, PROJECT);

        verify(calculateWorkItemMetricsUseCase).execute(ORG, PROJECT);
        verify(calculateSprintMetricsUseCase).execute(ORG, PROJECT);
        verify(calculateTokensPerSpUseCase).execute(ORG, PROJECT);
        verify(calculatePrMetricsUseCase).execute(ORG, "repo-1");
        verify(calculatePrMetricsUseCase).execute(ORG, "repo-2");
    }

    @Test
    void shouldKeepCalculatingWhenOneCalculationFails() {
        doThrow(new RuntimeException("boom")).when(calculateWorkItemMetricsUseCase).execute(ORG, PROJECT);
        doThrow(new RuntimeException("boom")).when(calculateSprintMetricsUseCase).execute(ORG, PROJECT);
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(repo("repo-1")));

        recalculator.recalculate(ORG, PROJECT);

        verify(calculateTokensPerSpUseCase).execute(ORG, PROJECT);
        verify(calculatePrMetricsUseCase).execute(ORG, "repo-1");
    }

    @Test
    void shouldKeepCalculatingOtherReposWhenOneRepoFails() {
        when(vcsReadPort.repositoriesOfProject(ORG, PROJECT)).thenReturn(List.of(repo("repo-1"), repo("repo-2")));
        doThrow(new RuntimeException("boom")).when(calculatePrMetricsUseCase).execute(ORG, "repo-1");

        recalculator.recalculate(ORG, PROJECT);

        verify(calculatePrMetricsUseCase).execute(ORG, "repo-2");
    }

    private static VcsRepository repo(String id) {
        Instant now = Instant.now();
        return VcsLegacyFixtures.repository(id, ORG, "ext-" + id, "acme", id, "acme/" + id, now, true, now, now, 0L);
    }
}
