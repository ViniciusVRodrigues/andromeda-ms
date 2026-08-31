package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.RepoMetricPointOutput;
import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** A repository's stored Change Failure Rate measurements, ordered by period. */
@Component
@RequiredArgsConstructor
public class GetRepoMetricsSeriesUseCase {

    private final ProjectRepoResolver projectRepoResolver;
    private final RepoMetricsRepository repoMetricsRepository;

    public List<RepoMetricPointOutput> execute(String repoId, Instant from, Instant to) {
        String orgId = TenantContextHolder.get();
        projectRepoResolver.resolveOne(orgId, repoId)
                .orElseThrow(() -> new GithubRepoNotFoundException(repoId));

        Instant start = from != null ? from : Instant.EPOCH;
        Instant end = to != null ? to : Instant.now();

        return repoMetricsRepository
                .findAllByOrganizationIdAndRepoIdInAndPeriodOverlapping(orgId, List.of(repoId), start, end)
                .stream()
                .sorted(Comparator.comparing(RepoMetrics::getPeriodStart,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(m -> new RepoMetricPointOutput(m.getPeriodStart(), m.getPeriodEnd(),
                        m.getCfr(), m.isEstimated()))
                .toList();
    }
}
