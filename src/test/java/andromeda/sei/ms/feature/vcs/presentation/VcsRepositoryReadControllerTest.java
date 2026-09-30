package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import andromeda.sei.ms.feature.vcs.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.DeliveryMetricPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestPageOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.GetPullRequestCommitsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryCommitActivityUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryDeliveryMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryPullRequestsUseCase;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.exception.PullRequestCommitsUnavailableException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * vcs-repository-read-api (task 6.3). Standalone MockMvc — {@code @WebMvcTest} does not run under
 * Boot 4 in this repo, so {@code @PreAuthorize} is not exercised here.
 */
@ExtendWith(MockitoExtension.class)
class VcsRepositoryReadControllerTest {

    @Mock private ListProjectRepositoriesUseCase listProjectRepositoriesUseCase;
    @Mock private ListRepositoryPullRequestsUseCase listRepositoryPullRequestsUseCase;
    @Mock private GetRepositoryDeliveryMetricsUseCase getRepositoryDeliveryMetricsUseCase;
    @Mock private GetRepositoryCommitActivityUseCase getRepositoryCommitActivityUseCase;
    @Mock private GetPullRequestCommitsUseCase getPullRequestCommitsUseCase;
    @Mock private DataFreshnessResolver dataFreshnessResolver;

    @InjectMocks
    private VcsRepositoryReadController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void should_listProjectRepositories_withLegacyAndNeutralFields() throws Exception {
        when(listProjectRepositoriesUseCase.execute("proj-1", null, null)).thenReturn(List.of(
                new ProjectRepositoryOutput("repo-1", "owner/andromeda-ms", 2, 1, 24.0, 12.5, 10.0, true,
                        Instant.parse("2026-08-14T00:00:00Z"), DurationStat.empty(), ReviewMetrics.empty(),
                        DeliveryMetrics.unavailable(), "repo-1", VcsProvider.GITHUB, "owner/andromeda-ms", "main")));
        when(dataFreshnessResolver.resolve(any(), eq("proj-1")))
                .thenReturn(new DataFreshness(Instant.parse("2026-09-27T10:00:00Z"), true));

        mockMvc.perform(get("/api/v1/repos/projects/proj-1/repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repos[0].repoId").value("repo-1"))
                .andExpect(jsonPath("$.repos[0].repositoryId").value("repo-1"))
                .andExpect(jsonPath("$.repos[0].fullName").value("owner/andromeda-ms"))
                .andExpect(jsonPath("$.repos[0].displayName").value("owner/andromeda-ms"))
                .andExpect(jsonPath("$.repos[0].provider").value("GITHUB"))
                .andExpect(jsonPath("$.repos[0].defaultBranch").value("main"))
                .andExpect(jsonPath("$.repos[0].openPrCount").value(2))
                .andExpect(jsonPath("$.repos[0].stalePrCount").value(1))
                .andExpect(jsonPath("$.repos[0].review.slaHours").value(24))
                .andExpect(jsonPath("$.repos[0].delivery").exists())
                .andExpect(jsonPath("$.repos[0].prCycleTime").exists())
                .andExpect(jsonPath("$.dataFreshness.refreshRunning").value(true));
    }

    @Test
    void should_passDateWindow_when_listingProjectRepositories() throws Exception {
        when(listProjectRepositoriesUseCase.execute(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/repos/projects/proj-1/repositories")
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repos").isEmpty());

        verify(listProjectRepositoriesUseCase).execute("proj-1", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-28"));
    }

    @Test
    void should_defaultPagination_and_passFilters() throws Exception {
        when(listRepositoryPullRequestsUseCase.execute(eq("repo-1"), any(), any(), anyInt(), anyInt()))
                .thenReturn(new RepositoryPullRequestPageOutput(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/pull-requests")
                        .param("status", "OPEN").param("author", "octocat"))
                .andExpect(status().isOk());

        verify(listRepositoryPullRequestsUseCase).execute("repo-1", PullRequestStatus.OPEN, "octocat", 0, 20);
    }

    @Test
    void should_exposeAuthorLoginAndProvider_insteadOfGithubLogin() throws Exception {
        when(listRepositoryPullRequestsUseCase.execute(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new RepositoryPullRequestPageOutput(List.of(new RepositoryPullRequestOutput("pr-1", 10,
                        "Add repos tab", PullRequestStatus.MERGED, "octocat", "user-1",
                        Instant.parse("2026-08-01T00:00:00Z"), null, null, 1.5, 0.5, "123", "wi-1", "Isolar",
                        true, 20.25, VcsProvider.GITHUB)), 0, 20, 1));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/pull-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].authorLogin").value("octocat"))
                .andExpect(jsonPath("$.content[0].authorGithubLogin").doesNotExist())
                .andExpect(jsonPath("$.content[0].authorUserId").value("user-1"))
                .andExpect(jsonPath("$.content[0].provider").value("GITHUB"))
                .andExpect(jsonPath("$.content[0].linkedWorkItemId").value("wi-1"))
                .andExpect(jsonPath("$.content[0].timeToFirstApprovalHours").value(20.25))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_answer404_when_repositoryOfAnotherOrganization() throws Exception {
        when(listRepositoryPullRequestsUseCase.execute(eq("repo-x"), any(), any(), anyInt(), anyInt()))
                .thenThrow(new RepoNotFoundException("repo-x"));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-x/pull-requests"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND"));
    }

    @Test
    void should_returnDeliveryMetricSeries() throws Exception {
        when(getRepositoryDeliveryMetricsUseCase.execute(eq("repo-1"), any(), any())).thenReturn(List.of(
                new DeliveryMetricPointOutput(Instant.parse("2026-07-15T00:00:00Z"),
                        Instant.parse("2026-07-22T00:00:00Z"), 25.0, 4.0, 4, 1)));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/delivery-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].changeFailRatePercent").value(25.0))
                .andExpect(jsonPath("$[0].deploymentsPerWeek").value(4.0))
                .andExpect(jsonPath("$[0].mergedPrs").value(4))
                .andExpect(jsonPath("$[0].remediationPrs").value(1));
    }

    @Test
    void should_convertDateParamsToHalfOpenWindow_when_seriesRequestedByDate() throws Exception {
        when(getRepositoryDeliveryMetricsUseCase.execute(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/delivery-metrics")
                        .param("startDate", "2026-07-01").param("endDate", "2026-07-28"))
                .andExpect(status().isOk());

        verify(getRepositoryDeliveryMetricsUseCase).execute("repo-1", Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-29T00:00:00Z"));
    }

    @Test
    void should_returnCommitActivity() throws Exception {
        when(getRepositoryCommitActivityUseCase.execute(eq("repo-1"), any(), any())).thenReturn(List.of(
                new CommitActivityPointOutput(Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-08-08T00:00:00Z"), 12)));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/commit-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commitCount").value(12));
    }

    @Test
    void should_returnPullRequestCommits() throws Exception {
        when(getPullRequestCommitsUseCase.execute("repo-1", "pr-1")).thenReturn(List.of(
                new PullRequestCommitOutput("abc1234", "Add repos tab", "octocat", Instant.parse("2026-08-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/pull-requests/pr-1/commits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sha").value("abc1234"))
                .andExpect(jsonPath("$[0].authorLogin").value("octocat"));
    }

    @Test
    void should_answer502_when_providerCannotBeReachedForCommits() throws Exception {
        when(getPullRequestCommitsUseCase.execute("repo-1", "pr-1"))
                .thenThrow(new PullRequestCommitsUnavailableException(10, new RuntimeException("401")));

        mockMvc.perform(get("/api/v1/repos/repositories/repo-1/pull-requests/pr-1/commits"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PR_COMMITS_UNAVAILABLE"));
    }
}
