package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.Sync;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncError;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncProblem;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncToday;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.SyncTriggers;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The sync runs of the last {@value #DAYS} days (UTC), as the preview's sync panels.
 *
 * <p><b>What counts as a run:</b> a finished log of a <em>leaf</em> operation — the work actually
 * done against the provider ({@code SYNC_SPRINTS}, {@code SYNC_WORK_ITEMS}, {@code SYNC_REPOS},
 * {@code SYNC_COMMITS}, {@code SYNC_PULL_REQUESTS}, {@code SYNC_REVIEWS}). The orchestrator's parent
 * logs ({@code PROJECT_REFRESH}, {@code ORGANIZATION_SYNC}) and its per-binding children
 * ({@code SYNC_BINDING}) wrap those same leaf runs, so counting them too would count one sync two or
 * three times; listings ({@code LIST_*}), wipes and {@code RUNNING} logs are not runs either.
 */
public final class SyncRunsSummary {

    public static final int DAYS = 14;
    static final int MAX_PROBLEMS = 12;
    private static final double MILLIS_PER_SECOND = 1000.0;
    /** The operations that are runs (see the class comment); also the filter of the sync-log query. */
    public static final Set<SyncOperation> LEAF_OPERATIONS = Collections.unmodifiableSet(EnumSet.of(
            SyncOperation.SYNC_SPRINTS, SyncOperation.SYNC_WORK_ITEMS, SyncOperation.SYNC_REPOS,
            SyncOperation.SYNC_COMMITS, SyncOperation.SYNC_PULL_REQUESTS, SyncOperation.SYNC_REVIEWS));
    private static final List<Integration> PROVIDERS = List.of(Integration.AZURE_DEVOPS, Integration.GITHUB);

    private SyncRunsSummary() {}

    /** First instant of the window: the start of {@code today - (DAYS - 1)}. */
    public static Instant windowStart(LocalDate today) {
        return firstDay(today).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** The {@value #DAYS} days of the window, oldest first. */
    public static List<LocalDate> days(LocalDate today) {
        return DashboardV2Views.days(firstDay(today), today);
    }

    /** A finished leaf-operation run (see the class comment). */
    public static boolean isRun(IntegrationSyncLog log) {
        return log != null && log.getStartedAt() != null && log.getStatus() != null
                && log.getStatus() != SyncStatus.RUNNING && LEAF_OPERATIONS.contains(log.getOperation());
    }

    public static LocalDate dayOf(IntegrationSyncLog log) {
        return log.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate();
    }

    public static Sync of(Collection<IntegrationSyncLog> logs, LocalDate today) {
        LocalDate first = firstDay(today);
        List<IntegrationSyncLog> runs = logs.stream().filter(SyncRunsSummary::isRun)
                .filter(l -> !dayOf(l).isBefore(first) && !dayOf(l).isAfter(today)).toList();
        return new Sync(days(today).stream().map(day -> syncDay(runs, day)).toList(),
                PROVIDERS.stream().map(i -> triggers(runs, i)).toList(), problems(runs),
                new SyncToday(count(runs, l -> dayOf(l).equals(today)),
                        count(runs, l -> dayOf(l).equals(today) && l.getStatus() == SyncStatus.FAILED)));
    }

    private static SyncDay syncDay(List<IntegrationSyncLog> runs, LocalDate day) {
        List<IntegrationSyncLog> ofDay = runs.stream().filter(l -> dayOf(l).equals(day)).toList();
        List<IntegrationSyncLog> azure = ofIntegration(ofDay, Integration.AZURE_DEVOPS);
        List<IntegrationSyncLog> github = ofIntegration(ofDay, Integration.GITHUB);
        return new SyncDay(day.toString(), DashboardV2Views.label(day), averageSeconds(azure), averageSeconds(github),
                maxSeconds(azure), maxSeconds(github), sum(ofDay, IntegrationSyncLog::getItemsProcessed),
                sum(ofDay, IntegrationSyncLog::getItemsFailed));
    }

    private static SyncTriggers triggers(List<IntegrationSyncLog> runs, Integration integration) {
        List<IntegrationSyncLog> of = ofIntegration(runs, integration);
        return new SyncTriggers(integration.name(), count(of, l -> l.getTriggeredBy() == SyncTrigger.SCHEDULED),
                count(of, l -> l.getTriggeredBy() == SyncTrigger.MANUAL),
                count(of, l -> l.getTriggeredBy() == SyncTrigger.WEBHOOK));
    }

    private static List<SyncProblem> problems(List<IntegrationSyncLog> runs) {
        return runs.stream().filter(l -> l.getStatus() != SyncStatus.SUCCESS)
                .sorted(Comparator.comparing(IntegrationSyncLog::getStartedAt).reversed())
                .limit(MAX_PROBLEMS)
                .map(l -> new SyncProblem(name(l.getIntegration()), name(l.getOperation()), name(l.getStatus()),
                        name(l.getTriggeredBy()), l.getStartedAt(), l.getDurationMs(), l.getItemsProcessed(),
                        l.getItemsFailed(), new SyncError(l.getErrorCode(), l.getHttpStatus())))
                .toList();
    }

    private static List<IntegrationSyncLog> ofIntegration(List<IntegrationSyncLog> logs, Integration integration) {
        return logs.stream().filter(l -> l.getIntegration() == integration).toList();
    }

    private static Double averageSeconds(List<IntegrationSyncLog> logs) {
        return logs.stream().map(IntegrationSyncLog::getDurationMs).filter(Objects::nonNull)
                .mapToLong(Long::longValue).average().stream().map(ms -> ms / MILLIS_PER_SECOND).boxed()
                .findFirst().orElse(null);
    }

    private static Double maxSeconds(List<IntegrationSyncLog> logs) {
        return logs.stream().map(IntegrationSyncLog::getDurationMs).filter(Objects::nonNull)
                .max(Long::compare).map(ms -> ms / MILLIS_PER_SECOND).orElse(null);
    }

    private static int sum(List<IntegrationSyncLog> logs, java.util.function.Function<IntegrationSyncLog, Integer> field) {
        ToIntFunction<IntegrationSyncLog> value = l -> field.apply(l) == null ? 0 : field.apply(l);
        return logs.stream().mapToInt(value).sum();
    }

    private static int count(List<IntegrationSyncLog> logs, Predicate<IntegrationSyncLog> predicate) {
        return (int) logs.stream().filter(predicate).count();
    }

    private static LocalDate firstDay(LocalDate today) {
        return today.minusDays(DAYS - 1L);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
