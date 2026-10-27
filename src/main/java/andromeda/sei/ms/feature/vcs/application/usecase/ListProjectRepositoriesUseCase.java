package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Lists a project's bound repositories (any provider), each with the health figures the
 * Repositorios screen shows over the selected window (default: the last 90 days): open and stalled
 * pull requests (current state), pull request cycle time p50/p90 over merges in the window, review
 * metrics over pull requests created in the window, and delivery metrics, all through the same
 * definitions the dashboards use.
 *
 * <p>A repository still pending its first sync (no external id yet) is not listed: it has no data,
 * exactly as an unsynced binding was absent before the neutral model.
 */
@Component
@RequiredArgsConstructor
public class ListProjectRepositoriesUseCase {

    static final int DEFAULT_WINDOW_DAYS = 90;

    private final VcsReadPort vcsReadPort;
    private final DeliveryMetricsService deliveryMetricsService;

    public List<ProjectRepositoryOutput> execute(String projectId, LocalDate startDate, LocalDate endDate) {
        String orgId = TenantContextHolder.get();
        Instant now = Instant.now();
        LocalDate lastDay = endDate != null ? endDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate firstDay = startDate != null ? startDate : lastDay.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        Window window = new Window(firstDay.atStartOfDay(ZoneOffset.UTC).toInstant(),
                lastDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());

        Map<String, VcsRepoBinding> bindingByRepository = new HashMap<>();
        vcsReadPort.bindingsOfProject(orgId, projectId)
                .forEach(binding -> bindingByRepository.putIfAbsent(binding.getRepositoryId(), binding));
        return vcsReadPort.repositoriesOfProject(orgId, projectId).stream()
                .filter(repo -> !repo.isPendingFirstSync())
                .map(repo -> summarize(orgId, repo, bindingByRepository.get(repo.getId()), window, now))
                .sorted(Comparator.comparing(ProjectRepositoryOutput::displayName,
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private ProjectRepositoryOutput summarize(String orgId, VcsRepository repo, VcsRepoBinding binding, Window window,
                                              Instant now) {
        List<VcsPullRequest> all = vcsReadPort.pullRequestsOfRepository(orgId, repo.getId());
        List<VcsPullRequest> open = all.stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN).toList();
        long staleCount = open.stream().filter(pr -> StalePullRequestPolicy.isStale(pr, now)).count();

        List<VcsPullRequest> mergedInWindow = all.stream().filter(pr -> window.contains(pr.getMergedAt())).toList();
        List<VcsPullRequest> createdInWindow = all.stream().filter(pr -> window.contains(pr.getCreatedAt())).toList();

        DurationStat prCycleTime = Percentiles.of(mergedInWindow.stream().map(VcsPullRequest::getCycleTimeHours).toList());
        ReviewMetrics review = ReviewMetricsPolicy.aggregate(createdInWindow, now);
        DeliveryMetrics delivery = deliveryMetricsService.calculate(orgId,
                deliveryMetricsService.mergedChangesFrom(orgId, List.of(repo), mergedInWindow),
                List.of(), window.from(), window.to());
        Double cfr = delivery.changeFailRatePercent();

        return new ProjectRepositoryOutput(
                repo.getId(), repo.getDisplayName(), open.size(), staleCount,
                average(mergedInWindow, VcsPullRequest::getCycleTimeHours),
                average(createdInWindow, VcsPullRequest::getTimeToFirstReviewHours),
                cfr, cfr != null ? Boolean.TRUE : null, cfr != null ? window.to() : null,
                prCycleTime, review, delivery,
                repo.getId(), repo.getProvider(), repo.getDisplayName(), repo.getDefaultBranch(),
                binding != null ? binding.effectiveBranches(repo.getDefaultBranch()) : defaultBranches(repo),
                binding != null ? binding.effectiveProductionBranch(repo.getDefaultBranch()) : repo.getDefaultBranch(),
                binding != null && binding.isFilterPullRequestsByBranch(),
                binding != null ? binding.getMissingBranches() : List.of(),
                binding != null ? binding.effectiveSyncState(now) : null,
                binding != null ? binding.getLastSyncAt() : null);
    }

    private static List<String> defaultBranches(VcsRepository repo) {
        return repo.getDefaultBranch() == null ? List.of() : List.of(repo.getDefaultBranch());
    }

    /** Deprecated average, kept for one release next to the percentiles. */
    private static Double average(List<VcsPullRequest> prs, Function<VcsPullRequest, Double> metric) {
        OptionalDouble average = prs.stream().map(metric).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    /** Half-open {@code [from, to)}. */
    private record Window(Instant from, Instant to) {
        boolean contains(Instant at) {
            return at != null && !at.isBefore(from) && at.isBefore(to);
        }
    }
}
