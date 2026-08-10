package andromeda.sei.ms.feature.github.domain;

import java.time.Instant;
import java.util.Optional;

public interface RepoMetricsRepository {

    Optional<RepoMetrics> findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(
            String organizationId, String repoId, Instant periodStart, Instant periodEnd);

    RepoMetrics save(RepoMetrics metrics);
}
