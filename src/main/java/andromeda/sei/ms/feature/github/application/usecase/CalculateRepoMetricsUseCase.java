package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import andromeda.sei.ms.feature.github.infrastructure.client.GhIssueDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Computes period-level Change Failure Rate: corrective issues (labeled {@code bug} or
 * {@code hotfix}, excluding {@code not_planned}/{@code duplicate} and PR-flagged entries) opened
 * in the period, divided by commits made in the same period (spec: repo-level CFR; design.md D7 —
 * an aggregate ratio, never a per-commit causal join).
 *
 * <p>Period: a trailing 30-day window ending now, run once per sync cycle -- matches the
 * "period-scoped, pre-aggregated" goal in design.md without requiring a separate period-selection
 * API surface in this change (dashboard-side period selection is the companion change's concern).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalculateRepoMetricsUseCase {

    private static final int PERIOD_DAYS = 30;
    private static final String LABEL_BUG = "bug";
    private static final String LABEL_HOTFIX = "hotfix";

    private final GithubCommitRepository githubCommitRepository;
    private final RepoMetricsRepository repoMetricsRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;

    public void execute(GithubConnection connection, GithubRepo repo) {
        String orgId = connection.getOrganizationId();
        Instant periodEnd = Instant.now();
        Instant periodStart = periodEnd.minus(PERIOD_DAYS, ChronoUnit.DAYS);

        long commitCount = githubCommitRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(
                orgId, repo.getId(), periodStart, periodEnd);

        // spec: no commits in period -> do not compute or persist a value, leave prior value untouched.
        if (commitCount == 0) {
            log.info("Skipping CFR calculation for repo={}: zero commits in period", repo.getFullName());
            return;
        }

        long correctiveIssueCount = countCorrectiveIssues(connection, repo, periodStart, periodEnd);
        double cfr = correctiveIssueCount / (double) commitCount;

        RepoMetrics metrics = repoMetricsRepository
                .findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(orgId, repo.getId(), periodStart, periodEnd)
                .orElseGet(() -> new RepoMetrics(
                        null, orgId, repo.getId(), periodStart, periodEnd, null, false, null,
                        true, Instant.now(), Instant.now(), null));

        metrics.setCfr(cfr);
        metrics.setEstimated(false);
        metrics.setCalculatedAt(Instant.now());

        repoMetricsRepository.save(metrics);
    }

    /**
     * design.md D7: {@code labels} query param is AND-combined, so "bug OR hotfix" requires two
     * separate calls unioned and de-duplicated by issue id.
     */
    private long countCorrectiveIssues(GithubConnection connection, GithubRepo repo,
                                        Instant periodStart, Instant periodEnd) {
        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        // `since` filters updated_at, not created_at (D5) -- fetch with a buffer, filter client-side below.
        Instant sinceBuffer = periodStart.minus(1, ChronoUnit.DAYS);

        Map<Long, GhIssueDto> byId = new LinkedHashMap<>();
        for (String label : new String[] { LABEL_BUG, LABEL_HOTFIX }) {
            var issues = githubApiClient.getIssues(repo.getOwner(), repo.getName(), authHeader, label, sinceBuffer);
            for (GhIssueDto issue : issues) {
                byId.putIfAbsent(issue.id(), issue);
            }
        }

        return byId.values().stream()
                .filter(issue -> !issue.isPullRequest())
                .filter(issue -> !isExcludedStateReason(issue.stateReason()))
                .filter(issue -> isCreatedWithinPeriod(issue.createdAt(), periodStart, periodEnd))
                .count();
    }

    private boolean isExcludedStateReason(String stateReason) {
        return "not_planned".equalsIgnoreCase(stateReason) || "duplicate".equalsIgnoreCase(stateReason);
    }

    private boolean isCreatedWithinPeriod(String createdAtIso, Instant periodStart, Instant periodEnd) {
        if (createdAtIso == null) return false;
        try {
            Instant createdAt = Instant.parse(createdAtIso);
            return !createdAt.isBefore(periodStart) && !createdAt.isAfter(periodEnd);
        } catch (Exception e) {
            return false;
        }
    }
}
