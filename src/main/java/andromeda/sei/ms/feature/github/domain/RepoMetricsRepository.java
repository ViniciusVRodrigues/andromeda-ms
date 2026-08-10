package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RepoMetricsRepository {

    Optional<RepoMetrics> findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd);

    /**
     * Rows whose [periodStart, periodEnd] window overlaps the given [from, to] window,
     * scoped to any of the given repoIds (D1: aggregate across a project's bound repos).
     */
    List<RepoMetrics> findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(
            String organizationId, List<String> repoIds, Instant from, Instant to);

    RepoMetrics save(RepoMetrics metrics);
}
