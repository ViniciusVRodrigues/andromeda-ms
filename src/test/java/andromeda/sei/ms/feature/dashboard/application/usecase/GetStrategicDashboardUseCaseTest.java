package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.StrategicDashboardOutput;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStrategicDashboardUseCaseTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private WorkItemRepository workItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private GetDeveloperDashboardUseCase getDeveloperDashboardUseCase;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;
    @Mock private GithubRepoRepository githubRepoRepository;
    @Mock private RepoMetricsRepository repoMetricsRepository;

    private GetStrategicDashboardUseCase useCase;

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";

    @BeforeEach
    void setUp() {
        useCase = new GetStrategicDashboardUseCase(
                projectRepository, workItemRepository, new andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver(),
                userRepository, getDeveloperDashboardUseCase, githubRepoBindingRepository,
                githubRepoRepository, repoMetricsRepository);
        lenient().when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        lenient().when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(any(), any()))
                .thenReturn(List.of());
    }

    // ── Task 6.6-6.9: F-15 Change Failure Rate ───────────────────────────

    @Test
    void should_useRealCfr_estimatedFalse_when_repoMetricsPresent() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();

        Project project = activeProject();
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(doneWorkItem(from, to)));

        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        // BUG 1 fix: the binding's "owner/name" fullName must be resolved to the github_repos
        // Mongo id BEFORE querying repo_metrics (metrics rows are keyed by Mongo id).
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                eq(ORG), eq(List.of("octo/hello"))))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));
        when(repoMetricsRepository.findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(
                eq(ORG), eq(List.of("mongo-repo-1")), any(), any()))
                .thenReturn(List.of(repoMetrics("mongo-repo-1", 3.5)));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.changeFailureRatePercent()).isEqualTo(3.5);
        assertThat(dora.estimated()).isFalse();
    }

    @Test
    void should_fallBackToProxyCfr_estimatedTrue_when_noBoundRepos() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();

        Project project = activeProject();
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(doneWorkItem(from, to)));

        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.estimated()).isTrue();
        // proxy: bugCount(0) * 100.0 / doneItems.size()(1) = 0.0
        assertThat(dora.changeFailureRatePercent()).isEqualTo(0.0);
    }

    @Test
    void should_fallBackToProxyCfr_estimatedTrue_when_boundReposHaveNoSyncedMetricsForPeriod() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();

        Project project = activeProject();
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(doneWorkItem(from, to)));

        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/hello")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                eq(ORG), eq(List.of("octo/hello"))))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/hello")));
        when(repoMetricsRepository.findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(
                eq(ORG), eq(List.of("mongo-repo-1")), any(), any()))
                .thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.estimated()).isTrue();
    }

    @Test
    void should_fallBackToProxyCfr_estimatedTrue_when_boundFullNameNeverSyncedToGithubRepos() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();

        Project project = activeProject();
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(doneWorkItem(from, to)));

        // BUG 1 fix: an unresolvable fullName is skipped — the proxy CFR (estimated=true)
        // applies, exactly as if the project had no synced metrics.
        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/never-synced")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                eq(ORG), eq(List.of("octo/never-synced"))))
                .thenReturn(List.of());

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.estimated()).isTrue();
        // proxy: bugCount(0) * 100.0 / doneItems.size()(1) = 0.0
        assertThat(dora.changeFailureRatePercent()).isEqualTo(0.0);
    }

    @Test
    void should_averageCfr_across_multipleBoundReposWithMetrics() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();

        Project project = activeProject();
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));
        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(doneWorkItem(from, to)));

        when(githubRepoBindingRepository.findAllByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(List.of(binding("octo/one"), binding("octo/two")));
        when(githubRepoRepository.findAllByOrganizationIdAndFullNameInAndActiveTrue(
                eq(ORG), eq(List.of("octo/one", "octo/two"))))
                .thenReturn(List.of(repo("mongo-repo-1", "octo/one"), repo("mongo-repo-2", "octo/two")));
        when(repoMetricsRepository.findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(
                eq(ORG), eq(List.of("mongo-repo-1", "mongo-repo-2")), any(), any()))
                .thenReturn(List.of(repoMetrics("mongo-repo-1", 2.0), repoMetrics("mongo-repo-2", 8.0)));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        StrategicDashboardOutput.DoraMetrics dora = output.projects().get(0).dora();
        assertThat(dora.changeFailureRatePercent()).isEqualTo(5.0);
        assertThat(dora.estimated()).isFalse();
    }

    private Project activeProject() {
        return new Project(PROJECT, ORG, "My Project", "my-project", "desc",
                true, List.of(), 1L, Instant.now(), Instant.now());
    }

    private WorkItem doneWorkItem(LocalDate from, LocalDate to) {
        Instant completedAt = to.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(3600);
        return new WorkItem("wi-1", ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "wi-1", Instant.now(), "Title",
                "Story", WorkItem.ResolvedType.STORY,
                "Closed", WorkItem.ResolvedStatus.DONE,
                "Medium", 5.0, List.of(),
                null, "ext-user", "user-1", null, null,
                Instant.now(), null, completedAt,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, 5.0, null, null, null);
    }

    private GithubRepoBinding binding(String repoId) {
        return new GithubRepoBinding("binding-" + repoId, ORG, PROJECT, repoId, true, null, true,
                Instant.now(), Instant.now(), 1L);
    }

    private GithubRepo repo(String id, String fullName) {
        return new GithubRepo(id, ORG, "ext-" + id, "octo", "hello", fullName,
                Instant.now(), true, Instant.now(), Instant.now(), 1L);
    }

    private RepoMetrics repoMetrics(String repoId, double cfr) {
        return new RepoMetrics("rm-" + repoId, ORG, repoId, Instant.now().minusSeconds(86400), Instant.now(),
                cfr, false, Instant.now(), true, Instant.now(), Instant.now(), 1L);
    }

    @Test
    void should_includeFeatureTypedWorkItem_inProjectSummaryVelocity_notDropIt() {
        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now();
        Instant completedAt = to.atStartOfDay(ZoneOffset.UTC).toInstant().minusSeconds(3600);

        Project project = new Project(PROJECT, ORG, "My Project", "my-project", "desc",
                true, List.of(), 1L, Instant.now(), Instant.now());
        when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(project));

        WorkItem feature = new WorkItem("wi-feat", ORG, PROJECT, WorkItem.Source.AZURE_DEVOPS,
                "wi-feat", Instant.now(), "Feature Title",
                "Feature", WorkItem.ResolvedType.FEATURE,
                "Closed", WorkItem.ResolvedStatus.DONE,
                "Medium", 8.0, List.of(),
                null, "ext-user", "user-1", null, null,
                Instant.now(), null, completedAt,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, 8.0, null, null, null);

        when(workItemRepository.findAllByOrganizationIdAndProjectId(ORG, PROJECT)).thenReturn(List.of(feature));

        StrategicDashboardOutput output = useCase.execute(ORG, "auth-user", null, null, from, to);

        assertThat(output.projects()).hasSize(1);
        // A FEATURE-typed item must be counted like a STORY here, not silently dropped (0 sp).
        assertThat(output.projects().get(0).storyPointsDelivered()).isEqualTo(8.0);
        // (uses WorkItem.getStoryPoints(), the dual-written legacy field, per buildProjectSummary)
    }
}
