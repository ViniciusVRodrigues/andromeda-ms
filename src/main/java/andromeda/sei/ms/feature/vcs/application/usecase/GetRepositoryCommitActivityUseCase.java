package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Commit counts over the range the screen is showing, served from synced commits only. The bucket
 * width follows the range: daily up to a month, weekly up to about seven months, monthly above.
 */
@Component
@RequiredArgsConstructor
public class GetRepositoryCommitActivityUseCase {

    private static final long DAILY_MAX_DAYS = 31;
    private static final long WEEKLY_MAX_DAYS = 210;
    private static final int DEFAULT_RANGE_DAYS = 90;
    private static final int WEEK_DAYS = 7;
    private static final int MONTH_DAYS = 30;

    private final VcsReadPort vcsReadPort;
    private final VcsCommitRepository commitRepository;

    public List<CommitActivityPointOutput> execute(String repositoryId, Instant from, Instant to) {
        String orgId = TenantContextHolder.get();
        vcsReadPort.repository(orgId, repositoryId).orElseThrow(() -> new RepoNotFoundException(repositoryId));

        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        if (!start.isBefore(end)) return List.of();

        Duration bucket = bucketFor(start, end);
        List<CommitActivityPointOutput> series = new ArrayList<>();
        Instant cursor = start;
        while (cursor.isBefore(end)) {
            Instant next = cursor.plus(bucket).isAfter(end) ? end : cursor.plus(bucket);
            long count = commitRepository.countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(
                    orgId, repositoryId, cursor, next);
            series.add(new CommitActivityPointOutput(cursor, next, count));
            cursor = next;
        }
        return series;
    }

    private static Duration bucketFor(Instant start, Instant end) {
        long days = Duration.between(start, end).toDays();
        if (days <= DAILY_MAX_DAYS) return Duration.ofDays(1);
        if (days <= WEEKLY_MAX_DAYS) return Duration.ofDays(WEEK_DAYS);
        return Duration.ofDays(MONTH_DAYS);
    }
}
