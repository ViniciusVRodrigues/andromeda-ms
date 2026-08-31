package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.ProjectRepoResolver;
import andromeda.sei.ms.feature.github.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubRepoNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Commit counts over the range the screen is showing, for the activity trend.
 *
 * <p>Served entirely from synced commit data — no GitHub call. The trend answers the same
 * question as the rest of the screen, so it honours the same range: a chart anchored to its own
 * fixed window would describe a period the user did not ask about.
 */
@Component
@RequiredArgsConstructor
public class GetRepoCommitActivityUseCase {

    /** Below this many days the trend is bucketed daily, above it weekly, then monthly. */
    private static final long DAILY_MAX_DAYS = 31;
    private static final long WEEKLY_MAX_DAYS = 210;

    private static final int DEFAULT_RANGE_DAYS = 90;

    private final ProjectRepoResolver projectRepoResolver;
    private final GithubCommitRepository githubCommitRepository;

    public List<CommitActivityPointOutput> execute(String repoId, Instant from, Instant to) {
        String orgId = TenantContextHolder.get();
        projectRepoResolver.resolveOne(orgId, repoId)
                .orElseThrow(() -> new GithubRepoNotFoundException(repoId));

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        if (!start.isBefore(end)) return List.of();

        Duration bucket = bucketFor(start, end);

        List<CommitActivityPointOutput> series = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            Instant next = cursor.plus(bucket);
            if (next.isAfter(end)) next = end;

            long count = githubCommitRepository
                    .countByOrganizationIdAndRepoIdAndAuthoredAtBetween(orgId, repoId, cursor, next);
            series.add(new CommitActivityPointOutput(cursor, next, count));

            cursor = next;
        }
        return series;
    }

    /**
     * Bucket width follows the range so a short window is not collapsed into a single point nor
     * a long one exploded into hundreds.
     */
    private Duration bucketFor(Instant start, Instant end) {
        long days = Duration.between(start, end).toDays();
        if (days <= DAILY_MAX_DAYS) return Duration.ofDays(1);
        if (days <= WEEKLY_MAX_DAYS) return Duration.ofDays(7);
        return Duration.ofDays(30);
    }
}
