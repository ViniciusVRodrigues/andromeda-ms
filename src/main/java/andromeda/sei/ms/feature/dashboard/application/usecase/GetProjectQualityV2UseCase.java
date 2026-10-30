package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.Assignee;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.BugFlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.CommitKindRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.CommitKindTotal;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.OpenBug;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.ReworkRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectQualityV2Output.Totals;
import andromeda.sei.ms.feature.dashboard.application.v2.CommitKinds;
import andromeda.sei.ms.feature.dashboard.application.v2.ConcurrentLoads;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.application.v2.DayRange;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.dashboard.application.v2.WorkItemReopens;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Project quality v2 (dashboards-customizaveis D1, {@code buildQuality}) of one project and period:
 * open bugs, bugs created × closed, time to fix (lead time p50/p90 of the bugs closed), reopen rate
 * ({@link WorkItemReopens} over the recorded transitions), commit types by message
 * ({@link CommitKinds}) and remediation pull requests (the {@code RemediationClassifier} through
 * {@link DeliveryMetricsService}). Severity, closing reason and GitHub issues are not stored.
 */
@Service
public class GetProjectQualityV2UseCase {

    private static final int OLDEST_LIMIT = 8;
    private static final double HOURS_PER_DAY = 24.0;

    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTransitionRepository transitionRepository;
    private final ProjectVcsActivity vcsActivity;
    private final DeliveryMetricsService deliveryMetricsService;
    private final DashboardPeopleDirectory peopleDirectory;
    private final Clock clock;

    @Autowired
    public GetProjectQualityV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                                      WorkItemTransitionRepository transitionRepository, ProjectVcsActivity vcsActivity,
                                      DeliveryMetricsService deliveryMetricsService,
                                      DashboardPeopleDirectory peopleDirectory) {
        this(projectRepository, workItemRepository, transitionRepository, vcsActivity, deliveryMetricsService,
                peopleDirectory, Clock.systemUTC());
    }

    GetProjectQualityV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                               WorkItemTransitionRepository transitionRepository, ProjectVcsActivity vcsActivity,
                               DeliveryMetricsService deliveryMetricsService, DashboardPeopleDirectory peopleDirectory,
                               Clock clock) {
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.transitionRepository = transitionRepository;
        this.vcsActivity = vcsActivity;
        this.deliveryMetricsService = deliveryMetricsService;
        this.peopleDirectory = peopleDirectory;
        this.clock = clock;
    }

    /** @param periodDays one of 30, 90, 180, 365 ({@code DashboardPeriodInvalidException} otherwise) */
    public ProjectQualityV2Output execute(String orgId, String projectId, int periodDays) {
        Objects.requireNonNull(orgId, "orgId");
        projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        DashboardPeriod period = DashboardPeriod.of(periodDays, SprintWindow.toDay(now));
        DayRange range = period.current();
        PeriodBuckets buckets = period.buckets();
        Context ctx = load(orgId, projectId, range, now);
        List<VcsCommit> commits = filter(ctx.vcs().commits(), c -> range.contains(c.getAuthoredAt()));
        return new ProjectQualityV2Output(
                new ProjectQualityV2Output.Period(periodDays, range.first().toString(), range.last().toString(),
                        buckets.granularity()),
                kpis(ctx, range, commits), bugFlow(ctx, buckets), oldestOpen(ctx, ctx.people()),
                rework(ctx, buckets), commitKinds(commits, buckets), commitKindTotals(commits),
                new Totals(commits.size()), now);
    }

    /** The work item chain (items, then their transitions) and the VCS chain run concurrently. */
    private Context load(String orgId, String projectId, DayRange range, Instant now) {
        try (ConcurrentLoads loads = new ConcurrentLoads()) {
            CompletableFuture<List<WorkItem>> items = loads.supply(() -> workItemRepository
                    .findAllByOrganizationIdAndProjectId(orgId, projectId).stream()
                    .filter(Objects::nonNull).filter(WorkItem::isActive).toList());
            CompletableFuture<List<WorkItem>> doneInPeriod = loads.then(items, all -> filter(all,
                    i -> isDone(i) && !ProjectPeriodItems.isContainer(i) && range.contains(i.getCompletedAt())));
            CompletableFuture<Map<String, Integer>> reopens = loads.then(doneInPeriod, done -> reopens(orgId, done));
            CompletableFuture<ProjectVcsActivity.Snapshot> vcs = loads.supply(
                    () -> vcsActivity.loadWithoutReviews(orgId, projectId, range.from(), range.to()));
            CompletableFuture<DeliveryMetrics> remediation = loads.then(vcs, v -> remediation(orgId, v, range));
            CompletableFuture<Map<String, PersonInfo>> people = loads.supply(() -> peopleDirectory.people(orgId));
            return new Context(now, ConcurrentLoads.join(items), ConcurrentLoads.join(doneInPeriod),
                    ConcurrentLoads.join(reopens), ConcurrentLoads.join(vcs), ConcurrentLoads.join(remediation),
                    ConcurrentLoads.join(people));
        }
    }

    private Map<String, Integer> reopens(String orgId, List<WorkItem> doneInPeriod) {
        Set<String> ids = doneInPeriod.stream().map(WorkItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of()
                : WorkItemReopens.byWorkItem(transitionRepository.findActiveByOrganizationIdAndWorkItemIdIn(orgId, ids));
    }

    private DeliveryMetrics remediation(String orgId, ProjectVcsActivity.Snapshot vcs, DayRange range) {
        List<VcsPullRequest> merged = filter(vcs.pullRequests(), pr -> range.contains(pr.getMergedAtIfMerged()));
        return deliveryMetricsService.calculate(orgId,
                deliveryMetricsService.mergedChangesFrom(orgId, vcs.repositories(), merged), List.of(),
                range.from(), range.to());
    }

    private static Kpis kpis(Context ctx, DayRange range, List<VcsCommit> commits) {
        List<WorkItem> bugs = bugs(ctx);
        List<WorkItem> closed = filter(bugs, b -> isDone(b) && range.contains(b.getCompletedAt()));
        long reopened = ctx.doneInPeriod().stream().filter(i -> isReopened(ctx, i)).count();
        DeliveryMetrics remediation = ctx.remediation();
        return new Kpis((int) bugs.stream().filter(b -> !isDone(b)).count(),
                (int) bugs.stream().filter(b -> range.contains(b.getCreatedAt())).count(), closed.size(),
                Percentiles.of(closed.stream().map(WorkItem::getLeadTimeHours).toList()),
                Ratios.percent(reopened, ctx.doneInPeriod().size()), (int) reopened,
                Ratios.percent(commits.stream().filter(c -> CommitKinds.FIX.equals(CommitKinds.kindOf(c.getMessage())))
                        .count(), commits.size()),
                remediation.mergedPrs(), remediation.remediationPrs(),
                Ratios.percent(remediation.remediationPrs(), remediation.mergedPrs()));
    }

    private static List<BugFlowRow> bugFlow(Context ctx, PeriodBuckets buckets) {
        List<WorkItem> bugs = bugs(ctx);
        return buckets.buckets().stream().map(b -> new BugFlowRow(b.start(), b.label(),
                (int) bugs.stream().filter(x -> b.range().contains(x.getCreatedAt())).count(),
                (int) bugs.stream().filter(x -> isDone(x) && b.range().contains(x.getCompletedAt())).count()))
                .toList();
    }

    private static List<OpenBug> oldestOpen(Context ctx, Map<String, PersonInfo> people) {
        return bugs(ctx).stream().filter(b -> !isDone(b))
                .sorted(Comparator.comparing(WorkItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(OLDEST_LIMIT)
                .map(b -> new OpenBug(b.getExternalId(), b.getTitle(), ProjectPeriodItems.priority(b),
                        b.getCreatedAt() == null ? null : Ratios.hours(b.getCreatedAt(), ctx.now()) / HOURS_PER_DAY,
                        b.getResolvedStatus() != null ? b.getResolvedStatus().name() : null, b.getRawStatus(),
                        assignee(b, people), b.getSprintId() != null))
                .toList();
    }

    private static Assignee assignee(WorkItem item, Map<String, PersonInfo> people) {
        if (item.getAssigneeUserId() == null) return null;
        PersonInfo person = people.get(item.getAssigneeUserId());
        return new Assignee(item.getAssigneeUserId(), person != null ? person.name() : item.getAssigneeUserId());
    }

    private static List<ReworkRow> rework(Context ctx, PeriodBuckets buckets) {
        return buckets.buckets().stream().map(b -> {
            List<WorkItem> done = filter(ctx.doneInPeriod(), i -> b.range().contains(i.getCompletedAt()));
            long reopened = done.stream().filter(i -> isReopened(ctx, i)).count();
            return new ReworkRow(b.start(), b.label(), (int) reopened, Ratios.percent(reopened, done.size()));
        }).toList();
    }

    private static List<CommitKindRow> commitKinds(List<VcsCommit> commits, PeriodBuckets buckets) {
        return buckets.buckets().stream().map(b -> {
            Map<String, Long> kinds = byKind(filter(commits, c -> b.range().contains(c.getAuthoredAt())));
            return new CommitKindRow(b.start(), b.label(), count(kinds, CommitKinds.FEAT), count(kinds, CommitKinds.FIX),
                    count(kinds, CommitKinds.REFACTOR), count(kinds, CommitKinds.TEST), count(kinds, CommitKinds.CHORE),
                    count(kinds, CommitKinds.DOCS), count(kinds, CommitKinds.OTHER));
        }).toList();
    }

    private static List<CommitKindTotal> commitKindTotals(List<VcsCommit> commits) {
        Map<String, Long> kinds = byKind(commits);
        return CommitKinds.ALL.stream().map(kind -> new CommitKindTotal(kind, count(kinds, kind))).toList();
    }

    private static Map<String, Long> byKind(List<VcsCommit> commits) {
        return commits.stream().collect(Collectors.groupingBy(c -> CommitKinds.kindOf(c.getMessage()), Collectors.counting()));
    }

    private static int count(Map<String, Long> kinds, String kind) {
        return kinds.getOrDefault(kind, 0L).intValue();
    }

    private static List<WorkItem> bugs(Context ctx) {
        return filter(ctx.items(), i -> i.getResolvedType() == WorkItem.ResolvedType.BUG);
    }

    private static boolean isReopened(Context ctx, WorkItem item) {
        return ctx.reopens().getOrDefault(item.getId(), 0) > 0;
    }

    private static boolean isDone(WorkItem item) {
        return item.getResolvedStatus() == WorkItem.ResolvedStatus.DONE;
    }


    private static <T> List<T> filter(Collection<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).toList();
    }

    private record Context(Instant now, List<WorkItem> items, List<WorkItem> doneInPeriod,
                           Map<String, Integer> reopens, ProjectVcsActivity.Snapshot vcs, DeliveryMetrics remediation,
                           Map<String, PersonInfo> people) {}
}
