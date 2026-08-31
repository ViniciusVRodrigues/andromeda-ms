package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.ProjectRepoSummaryOutput;
import andromeda.sei.ms.feature.github.domain.GithubPullRequest;
import andromeda.sei.ms.feature.github.domain.GithubPullRequestRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.PullRequestStatus;
import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import andromeda.sei.ms.feature.github.domain.StalePullRequestPolicy;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Lists a project's bound repositories, each with the health figures the Repositórios screen
 * shows: open and stalled pull requests, average cycle time and time to first review, and the
 * latest Change Failure Rate.
 *
 * <p>Averages are null when nothing was measured. Reporting zero instead would present the
 * absence of data as a measurement.
 */
@Component
@RequiredArgsConstructor
public class ListProjectReposUseCase {

    private final ProjectRepoResolver projectRepoResolver;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final RepoMetricsRepository repoMetricsRepository;

    public List<ProjectRepoSummaryOutput> execute(String projectId) {
        String orgId = TenantContextHolder.get();
        Instant now = Instant.now();

        return projectRepoResolver.resolve(orgId, projectId).stream()
                .map(repo -> summarize(orgId, repo, now))
                .sorted(Comparator.comparing(ProjectRepoSummaryOutput::fullName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private ProjectRepoSummaryOutput summarize(String orgId, GithubRepo repo, Instant now) {
        List<GithubPullRequest> all = githubPullRequestRepository
                .findAllByOrganizationIdAndRepoId(orgId, repo.getId());

        long openCount = all.stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN)
                .count();

        long staleCount = all.stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN)
                .filter(pr -> StalePullRequestPolicy.isStale(pr, now))
                .count();

        Double avgCycle = average(all.stream()
                .map(GithubPullRequest::getCycleTimeHours)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue));

        Double avgFirstReview = average(all.stream()
                .map(GithubPullRequest::getTimeToFirstReviewHours)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(Double::doubleValue));

        RepoMetrics latest = repoMetricsRepository
                .findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(
                        orgId, List.of(repo.getId()), Instant.EPOCH, now)
                .stream()
                .max(Comparator.comparing(RepoMetrics::getPeriodEnd,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        return new ProjectRepoSummaryOutput(
                repo.getId(), repo.getFullName(), openCount, staleCount,
                avgCycle, avgFirstReview,
                latest != null ? latest.getCfr() : null,
                latest != null ? latest.isEstimated() : null,
                latest != null ? latest.getPeriodEnd() : null);
    }

    private Double average(java.util.stream.DoubleStream values) {
        OptionalDouble average = values.average();
        return average.isPresent() ? average.getAsDouble() : null;
    }
}
