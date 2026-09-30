package andromeda.sei.ms.feature.vcs.presentation;

import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.vcs.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.DeliveryMetricPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.PullRequestCommitOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestPageOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.GetPullRequestCommitsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryCommitActivityUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryDeliveryMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryPullRequestsUseCase;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.presentation.dto.ProjectRepositoriesResponse;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Provider-neutral read side of repositories (vcs-repository-read-api): what the Repositorios screen
 * shows, for every provider. Served from synced data, except a pull request's commits, fetched live
 * through the provider port. Every item carries its {@code provider}. Pagination: {@code page} and
 * {@code size}, default 0 and 20.
 */
@Tag(name = "Repositories (read)")
@RestController
@RequestMapping("/api/v1/repos")
@RequiredArgsConstructor
public class VcsRepositoryReadController {

    private final ListProjectRepositoriesUseCase listProjectRepositoriesUseCase;
    private final ListRepositoryPullRequestsUseCase listRepositoryPullRequestsUseCase;
    private final GetRepositoryDeliveryMetricsUseCase getRepositoryDeliveryMetricsUseCase;
    private final GetRepositoryCommitActivityUseCase getRepositoryCommitActivityUseCase;
    private final GetPullRequestCommitsUseCase getPullRequestCommitsUseCase;
    private final DataFreshnessResolver dataFreshnessResolver;

    @Operation(summary = "List a project's bound repositories (any provider) with their health summary")
    @ApiResponse(responseCode = "200", description = "Repositories listed")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_READ")
    @GetMapping("/projects/{projectId}/repositories")
    @PreAuthorize("@perm.onProject(#projectId, 'REPOS_READ')")
    public ResponseEntity<ProjectRepositoriesResponse> listProjectRepositories(
            @PathVariable String projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(new ProjectRepositoriesResponse(
                listProjectRepositoriesUseCase.execute(projectId, startDate, endDate),
                dataFreshnessResolver.resolve(TenantContextHolder.get(), projectId)));
    }

    @Operation(summary = "List a repository's pull requests, newest first",
            description = "Optional filters: status, author (provider login).")
    @ApiResponse(responseCode = "200", description = "Pull requests listed")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization (REPO_NOT_FOUND)")
    @ApiResponse(responseCode = "403", description = "Missing REPOS_READ")
    @GetMapping("/repositories/{repositoryId}/pull-requests")
    @PreAuthorize("@perm.onRepository(#repositoryId, 'REPOS_READ')")
    public ResponseEntity<RepositoryPullRequestPageOutput> listPullRequests(
            @PathVariable String repositoryId,
            @RequestParam(required = false) PullRequestStatus status,
            @RequestParam(required = false) String author,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(listRepositoryPullRequestsUseCase.execute(repositoryId, status, author, page, size));
    }

    @Operation(summary = "Weekly delivery metrics of a repository (Change Fail Rate %, merges/week)",
            description = "Weekly buckets over [from, to) or [startDate, endDate]; default the last 90 days.")
    @ApiResponse(responseCode = "200", description = "Metric series")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization")
    @GetMapping("/repositories/{repositoryId}/delivery-metrics")
    @PreAuthorize("@perm.onRepository(#repositoryId, 'REPOS_READ')")
    public ResponseEntity<List<DeliveryMetricPointOutput>> getDeliveryMetrics(
            @PathVariable String repositoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Instant start = from != null ? from
                : startDate != null ? startDate.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant end = to != null ? to
                : endDate != null ? endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        return ResponseEntity.ok(getRepositoryDeliveryMetricsUseCase.execute(repositoryId, start, end));
    }

    @Operation(summary = "Commit activity over the selected range, aggregated by period")
    @ApiResponse(responseCode = "200", description = "Activity series")
    @ApiResponse(responseCode = "404", description = "Repository not found in this organization")
    @GetMapping("/repositories/{repositoryId}/commit-activity")
    @PreAuthorize("@perm.onRepository(#repositoryId, 'REPOS_READ')")
    public ResponseEntity<List<CommitActivityPointOutput>> getCommitActivity(
            @PathVariable String repositoryId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(getRepositoryCommitActivityUseCase.execute(repositoryId, from, to));
    }

    @Operation(summary = "Commits of a single pull request, fetched from the provider on demand")
    @ApiResponse(responseCode = "200", description = "Commits listed")
    @ApiResponse(responseCode = "404", description = "Repository or pull request not found")
    @ApiResponse(responseCode = "502", description = "Provider unavailable (PR_COMMITS_UNAVAILABLE)")
    @GetMapping("/repositories/{repositoryId}/pull-requests/{pullRequestId}/commits")
    @PreAuthorize("@perm.onRepository(#repositoryId, 'REPOS_READ')")
    public ResponseEntity<List<PullRequestCommitOutput>> getPullRequestCommits(
            @PathVariable String repositoryId,
            @PathVariable String pullRequestId) {
        return ResponseEntity.ok(getPullRequestCommitsUseCase.execute(repositoryId, pullRequestId));
    }
}
