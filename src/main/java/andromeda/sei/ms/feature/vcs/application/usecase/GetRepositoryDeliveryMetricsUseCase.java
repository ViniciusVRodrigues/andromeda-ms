package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetricsCalculator;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.metrics.domain.RemediationClassifier;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.vcs.application.dto.DeliveryMetricPointOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A repository's delivery metrics as weekly buckets over the requested window (default: the last
 * 90 days), computed on read from its merged pull requests into the default branch.
 */
@Component
@RequiredArgsConstructor
public class GetRepositoryDeliveryMetricsUseCase {

    static final Duration DEFAULT_WINDOW = Duration.ofDays(90);
    static final Duration BUCKET = Duration.ofDays(7);
    private static final double ROUNDING_SCALE = 100.0;

    private final VcsReadPort vcsReadPort;
    private final DeliveryMetricsService deliveryMetricsService;

    public List<DeliveryMetricPointOutput> execute(String repositoryId, Instant from, Instant to) {
        String orgId = TenantContextHolder.get();
        VcsRepository repo = vcsReadPort.repository(orgId, repositoryId)
                .orElseThrow(() -> new RepoNotFoundException(repositoryId));

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_WINDOW);
        if (!start.isBefore(end)) return List.of();

        List<MergedChange> changes = deliveryMetricsService.mergedChanges(orgId, List.of(repo), start, end)
                .stream().filter(MergedChange::isIntoDefaultBranch).toList();
        RemediationRules rules = deliveryMetricsService.remediationRules(orgId);

        List<DeliveryMetricPointOutput> series = new ArrayList<>();
        for (Instant bucketStart = start; bucketStart.isBefore(end); bucketStart = bucketStart.plus(BUCKET)) {
            Instant bucketEnd = bucketStart.plus(BUCKET).isBefore(end) ? bucketStart.plus(BUCKET) : end;
            series.add(bucket(changes, rules, bucketStart, bucketEnd));
        }
        return series;
    }

    private DeliveryMetricPointOutput bucket(List<MergedChange> changes, RemediationRules rules,
                                             Instant bucketStart, Instant bucketEnd) {
        List<MergedChange> inBucket = changes.stream()
                .filter(c -> !c.mergedAt().isBefore(bucketStart) && c.mergedAt().isBefore(bucketEnd))
                .toList();
        int remediations = (int) inBucket.stream().filter(c -> RemediationClassifier.isRemediation(c, rules)).count();
        double perWeek = inBucket.size() / DeliveryMetricsCalculator.weeks(bucketStart, bucketEnd);
        return new DeliveryMetricPointOutput(bucketStart, bucketEnd,
                Ratios.percent(remediations, inBucket.size()),
                Math.round(perWeek * ROUNDING_SCALE) / ROUNDING_SCALE,
                inBucket.size(), remediations);
    }
}
