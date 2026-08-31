package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.github.application.dto.ProjectRepoSummaryOutput;
import andromeda.sei.ms.feature.github.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoMetricPointOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestPageOutput;
import andromeda.sei.ms.feature.github.application.usecase.GetPullRequestCommitsUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetRepoCommitActivityUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetRepoMetricsSeriesUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListProjectReposUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListRepoPullRequestsUseCase;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
import andromeda.sei.ms.feature.github.domain.exception.PullRequestCommitsUnavailableException;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubRepositoryReadControllerTest {

    @Mock private ListProjectReposUseCase listProjectReposUseCase;
    @Mock private ListRepoPullRequestsUseCase listRepoPullRequestsUseCase;
    @Mock private GetRepoMetricsSeriesUseCase getRepoMetricsSeriesUseCase;
    @Mock private GetRepoCommitActivityUseCase getRepoCommitActivityUseCase;
    @Mock private GetPullRequestCommitsUseCase getPullRequestCommitsUseCase;

    @InjectMocks
    private GithubRepositoryReadController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldListProjectReposWithHealthSummary() throws Exception {
        when(listProjectReposUseCase.execute("proj-1")).thenReturn(List.of(
                new ProjectRepoSummaryOutput("repo-1", "owner/andromeda-ms", 2, 1,
                        24.0, 12.5, 0.1, false, Instant.parse("2026-08-14T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/github/projects/proj-1/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("owner/andromeda-ms"))
                .andExpect(jsonPath("$[0].openPrCount").value(2))
                .andExpect(jsonPath("$[0].stalePrCount").value(1));
    }

    @Test
    void shouldReturnEmptyList_when_projectHasNoBoundRepos() throws Exception {
        when(listProjectReposUseCase.execute("proj-1")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/github/projects/proj-1/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldDefaultPaginationToFirstPageOfTwenty() throws Exception {
        when(listRepoPullRequestsUseCase.execute(eq("repo-1"), any(), any(), eq(0), eq(20)))
                .thenReturn(new RepoPullRequestPageOutput(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/pull-requests"))
                .andExpect(status().isOk());

        verify(listRepoPullRequestsUseCase).execute("repo-1", null, null, 0, 20);
    }

    @Test
    void shouldPassStatusAndAuthorFilters() throws Exception {
        when(listRepoPullRequestsUseCase.execute(eq("repo-1"), eq(PullRequestStatus.OPEN),
                eq("octocat"), eq(0), eq(20)))
                .thenReturn(new RepoPullRequestPageOutput(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/pull-requests")
                        .param("status", "OPEN")
                        .param("author", "octocat"))
                .andExpect(status().isOk());

        verify(listRepoPullRequestsUseCase).execute("repo-1", PullRequestStatus.OPEN, "octocat", 0, 20);
    }

    @Test
    void shouldExposeWorkItemLink_when_pullRequestCarriesOne() throws Exception {
        when(listRepoPullRequestsUseCase.execute(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new RepoPullRequestPageOutput(List.of(
                        new RepoPullRequestOutput("pr-1", 10, "Add repos tab",
                                PullRequestStatus.MERGED, "octocat", "user-1",
                                Instant.parse("2026-08-01T00:00:00Z"), null, null,
                                1.5, 0.5, "123", "wi-1", "Isolar consultas por organizationId")),
                        0, 20, 1));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/pull-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].linkedWorkItemExternalId").value("123"))
                .andExpect(jsonPath("$.content[0].linkedWorkItemId").value("wi-1"));
    }

    @Test
    void shouldReturn404_when_repoBelongsToAnotherOrganization() throws Exception {
        when(listRepoPullRequestsUseCase.execute(eq("repo-x"), any(), any(), anyInt(), anyInt()))
                .thenThrow(new GithubRepoNotFoundException("repo-x"));

        mockMvc.perform(get("/api/v1/github/repos/repo-x/pull-requests"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnMetricSeries() throws Exception {
        when(getRepoMetricsSeriesUseCase.execute(eq("repo-1"), any(), any())).thenReturn(List.of(
                new RepoMetricPointOutput(Instant.parse("2026-07-15T00:00:00Z"),
                        Instant.parse("2026-08-14T00:00:00Z"), 0.0, false)));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estimated").value(false));
    }

    @Test
    void shouldReturnCommitActivitySeries() throws Exception {
        when(getRepoCommitActivityUseCase.execute(eq("repo-1"), any(), any())).thenReturn(List.of(
                new CommitActivityPointOutput(Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-08T00:00:00Z"), 12)));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/commit-activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].commitCount").value(12));
    }

    @Test
    void shouldReturnPullRequestCommits() throws Exception {
        when(getPullRequestCommitsUseCase.execute("repo-1", "pr-1")).thenReturn(List.of(
                new PullRequestCommitOutput("abc1234", "Add repos tab", "octocat",
                        Instant.parse("2026-08-01T00:00:00Z"))));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/pull-requests/pr-1/commits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sha").value("abc1234"));
    }


    /**
     * O restante do detalhe do pull request vem de dados sincronizados e continua válido, então
     * um token expirado ou rate limit esgotado não pode derrubar o painel inteiro.
     */
    @Test
    void shouldReport503_when_githubCannotBeReachedForCommits() throws Exception {
        when(getPullRequestCommitsUseCase.execute("repo-1", "pr-1"))
                .thenThrow(new PullRequestCommitsUnavailableException(10, new RuntimeException("401")));

        mockMvc.perform(get("/api/v1/github/repos/repo-1/pull-requests/pr-1/commits"))
                .andExpect(status().isServiceUnavailable());
    }
}
