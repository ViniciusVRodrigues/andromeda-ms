package andromeda.sei.ms.feature.github.presentation;

import andromeda.sei.ms.feature.github.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.github.application.dto.ProjectRepoSummaryOutput;
import andromeda.sei.ms.feature.github.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoMetricPointOutput;
import andromeda.sei.ms.feature.github.application.dto.RepoPullRequestPageOutput;
import andromeda.sei.ms.feature.github.application.usecase.GetPullRequestCommitsUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetRepoCommitActivityUseCase;
import andromeda.sei.ms.feature.github.application.usecase.GetRepoMetricsSeriesUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListProjectReposUseCase;
import andromeda.sei.ms.feature.github.application.usecase.ListRepoPullRequestsUseCase;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Read side of the GitHub integration: what the Repositórios screen shows.
 *
 * <p>Everything here is served from already-synced data, with one exception — a pull request's
 * commits, which GitHub is asked for at request time (see {@link GetPullRequestCommitsUseCase}).
 *
 * <p>Pagination mirrors the backlog endpoints: {@code page} and {@code size}, defaulting to 0
 * and 20.
 */
@Tag(name = "GitHub repositories (read)")
@RestController
@RequestMapping("/api/v1/github")
@RequiredArgsConstructor
public class GithubRepositoryReadController {

    private final ListProjectReposUseCase listProjectReposUseCase;
    private final ListRepoPullRequestsUseCase listRepoPullRequestsUseCase;
    private final GetRepoMetricsSeriesUseCase getRepoMetricsSeriesUseCase;
    private final GetRepoCommitActivityUseCase getRepoCommitActivityUseCase;
    private final GetPullRequestCommitsUseCase getPullRequestCommitsUseCase;

    @Operation(summary = "List a project's bound repositories with their health summary")
    @ApiResponse(responseCode = "200", description = "Repositories listed")
    @ApiResponse(responseCode = "403", description = "Missing GITHUB_READ permission")
    @GetMapping("/projects/{projectId}/repos")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<List<ProjectRepoSummaryOutput>> listProjectRepos(
            @PathVariable String projectId) {
        return ResponseEntity.ok(listProjectReposUseCase.execute(projectId));
    }

    @Operation(summary = "List a repository's pull requests, newest first")
    @ApiResponse(responseCode = "200", description = "Pull requests listed")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization")
    @ApiResponse(responseCode = "403", description = "Missing GITHUB_READ permission")
    @GetMapping("/repos/{repoId}/pull-requests")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<RepoPullRequestPageOutput> listPullRequests(
            @PathVariable String repoId,
            @RequestParam(required = false) PullRequestStatus status,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                listRepoPullRequestsUseCase.execute(repoId, status, author, page, size));
    }

    @Operation(summary = "Change Failure Rate series for a repository")
    @ApiResponse(responseCode = "200", description = "Metric series")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization")
    @GetMapping("/repos/{repoId}/metrics")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<List<RepoMetricPointOutput>> getMetrics(
            @PathVariable String repoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(getRepoMetricsSeriesUseCase.execute(repoId, from, to));
    }

    @Operation(summary = "Commit activity over the selected range, aggregated by period")
    @ApiResponse(responseCode = "200", description = "Activity series")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization")
    @GetMapping("/repos/{repoId}/commit-activity")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<List<CommitActivityPointOutput>> getCommitActivity(
            @PathVariable String repoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(getRepoCommitActivityUseCase.execute(repoId, from, to));
    }

    @Operation(summary = "Commits of a single pull request, fetched from GitHub on demand")
    @ApiResponse(responseCode = "200", description = "Commits listed")
    @ApiResponse(responseCode = "404", description = "Repository or pull request not found")
    @GetMapping("/repos/{repoId}/pull-requests/{pullRequestId}/commits")
    @PreAuthorize("hasAuthority('GITHUB_READ')")
    public ResponseEntity<List<PullRequestCommitOutput>> getPullRequestCommits(
            @PathVariable String repoId,
            @PathVariable String pullRequestId) {
        return ResponseEntity.ok(getPullRequestCommitsUseCase.execute(repoId, pullRequestId));
    }
}
