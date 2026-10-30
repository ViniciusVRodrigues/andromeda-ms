package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AiUsageDayModel;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageEditorTotal;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ActivityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Adoption;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.AiConnectorCard;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ConnectionCard;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ConnectionError;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.CostBucket;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Coverage;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.CurrentSprint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Delivery;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.EditorUse;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Engineering;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.FlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Integrations;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.OrgStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectSources;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.RepoRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.RepoSource;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectSprints;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.SprintHistory;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.SprintPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Subscription;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.SyncOutcomeDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ThroughputByProjectRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Tokens;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.TypeCount;
import andromeda.sei.ms.feature.dashboard.application.v2.ConcurrentLoads;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.DayRange;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData.Snapshot;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets.Bucket;
import andromeda.sei.ms.feature.dashboard.application.v2.PortfolioHealth;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.AiRatios;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems.SprintFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.dashboard.application.v2.SyncRunsSummary;
import andromeda.sei.ms.feature.integration.domain.SyncRunCount;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.CompletedWorkItem;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetricsCalculator;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static andromeda.sei.ms.feature.dashboard.application.v2.ConcurrentLoads.join;

/**
 * Organization overview v2 (dashboards-customizaveis D1, {@code buildOrganization}) over the
 * projects where the caller holds {@code DASHBOARD_STRATEGIC} — never the others: every figure is
 * computed from {@link OrganizationDashboardData#load}, which reads nothing project-scoped outside
 * {@code visibleProjectIds}. Each figure follows the rule that already exists for one project
 * ({@link GetProjectOverviewV2UseCase}): velocity by the effort resolver, durations by
 * {@link Percentiles}, time to first review by {@link ReviewMetricsPolicy}, stalled pull requests by
 * {@link StalePullRequestPolicy}, change failure rate by {@link DeliveryMetricsCalculator}.
 *
 * <p><b>Cost:</b> everything is indexed once ({@link Indexed}) — items by project, pull requests and
 * merged changes by repository, allocated cost by project through a {@code workItemId → projectId}
 * map — so the work is linear in the data (allocations × splits, items, pull requests), never a
 * product of two of them.
 */
@Service
public class GetOrganizationDashboardV2UseCase {

    static final String UNALLOCATED_NAME = "Não alocado a work items";
    private static final int MONEY_SCALE = 6;
    private static final int SUBSCRIPTION_SCALE = 2;
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final double PERCENT = 100.0;
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final String PROJECT_SEPARATOR = ", ";
    private static final String DISCONNECTED = "DISCONNECTED";
    private static final List<WorkItem.ResolvedType> THROUGHPUT_TYPES =
            List.of(WorkItem.ResolvedType.STORY, WorkItem.ResolvedType.BUG, WorkItem.ResolvedType.TASK);

    private final OrganizationDashboardData data;
    private final Clock clock;

    @Autowired
    public GetOrganizationDashboardV2UseCase(OrganizationDashboardData data) {
        this(data, Clock.systemUTC());
    }

    GetOrganizationDashboardV2UseCase(OrganizationDashboardData data, Clock clock) {
        this.data = data;
        this.clock = clock;
    }

    /**
     * @param visibleProjectIds projects where the caller effectively holds {@code DASHBOARD_STRATEGIC}
     * @param periodDays        one of 30, 90, 180, 365 ({@code DashboardPeriodInvalidException} otherwise)
     */
    public OrganizationDashboardV2Output execute(String orgId, Set<String> visibleProjectIds, int periodDays) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(visibleProjectIds, "visibleProjectIds");
        Instant now = clock.instant();
        LocalDate today = SprintWindow.toDay(now);
        DashboardPeriod period = DashboardPeriod.of(periodDays, today);
        Snapshot snapshot = data.load(orgId, Set.copyOf(visibleProjectIds), period, today);
        Context ctx = new Context(snapshot, period, period.buckets(), now, today, rules(snapshot.organization()),
                Indexed.of(snapshot, period));
        // The sections are pure functions of the (immutable) snapshot: computed concurrently.
        try (ConcurrentLoads sections = new ConcurrentLoads()) {
            var kpis = sections.supply(() -> stats(ctx, period.current(), true));
            var previous = sections.supply(() -> stats(ctx, period.previous(), false));
            var portfolio = sections.supply(() -> portfolio(ctx));
            var delivery = sections.supply(() -> delivery(ctx));
            var engineering = sections.supply(() -> engineering(ctx));
            var ai = sections.supply(() -> ai(ctx));
            var integrations = sections.supply(() -> integrations(ctx));
            var sprintHistory = sections.supply(() -> sprintHistory(ctx));
            return new OrganizationDashboardV2Output(periodInfo(ctx), join(kpis), join(previous), join(portfolio),
                    join(delivery), join(engineering), join(ai), join(integrations), join(sprintHistory), now);
        }
    }

    private static RemediationRules rules(Organization organization) {
        return Optional.ofNullable(organization).map(Organization::getMetricSettings)
                .map(MetricSettings::remediationRules).orElseGet(RemediationRules::defaults);
    }

    private static Period periodInfo(Context ctx) {
        DashboardPeriod p = ctx.period();
        return new Period(p.days(), p.current().first().toString(), p.current().last().toString(),
                p.previous().first().toString(), p.previous().last().toString(), ctx.buckets().granularity());
    }

    // ── kpis / previous ──

    private static OrgStats stats(Context ctx, DayRange range, boolean current) {
        Snapshot s = ctx.snapshot();
        Indexed ix = ctx.indexed();
        List<WorkItem> units = ix.unitsIn(range);
        Double points = ix.projects().values().stream().map(p -> p.items().complexityDeliveredIn(range))
                .filter(Objects::nonNull).reduce(Double::sum).orElse(null);
        Double hours = ix.projects().values().stream().map(p -> p.items().effortLoggedIn(range))
                .filter(Objects::nonNull).reduce(Double::sum).orElse(null);
        AiFigures ai = current ? AiFigures.of(s.ai().byDayModel(), s.ai().byUserDay()) : AiFigures.ofTotals(s.ai().previous());
        // the cost allocated (validated) to the visible projects, for every caller (coordinator's decision)
        BigDecimal cost = sum(ix.allocatedIn(current).values());
        return new OrgStats(current ? s.projects().size() : null, current ? s.people().size() : null,
                ix.projects().values().stream().mapToInt(p -> count(p.doneThroughput(), range)).sum(),
                Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList()),
                cfr(ctx, s.vcs().changes(), ix.completed(), range),
                (int) s.vcs().pullRequests().stream().filter(pr -> range.contains(pr.getMergedAtIfMerged())).count(),
                (int) s.vcs().commitsIn(range), timeToFirstReview(ctx, s.vcs().pullRequests(), range), cost,
                ai.tokens(), ai.activeUsers(), points, AiRatios.per(cost, points),
                AiRatios.per(cost, hours));
    }

    /** The people's AI cost, tokens and active users of one period. */
    private record AiFigures(BigDecimal cost, long tokens, int activeUsers) {

        static AiFigures of(List<AiUsageDayModel> dayModels, List<AiUsageUserDay> userDays) {
            return new AiFigures(costOfDayModels(dayModels),
                    dayModels.stream().mapToLong(u -> u.inputTokens() + u.outputTokens()).sum(),
                    (int) userDays.stream().map(AiUsageUserDay::userId).filter(Objects::nonNull).distinct().count());
        }

        static AiFigures ofTotals(List<andromeda.sei.ms.feature.aiusage.domain.AiUsageUserTotals> totals) {
            return new AiFigures(sum(totals.stream().map(t -> t.costUsd()).toList()),
                    totals.stream().mapToLong(t -> t.inputTokens() + t.outputTokens()).sum(),
                    (int) totals.stream().map(t -> t.userId()).filter(Objects::nonNull).distinct().count());
        }
    }

    private static Double cfr(Context ctx, List<MergedChange> changes, List<CompletedWorkItem> completed, DayRange range) {
        return DeliveryMetricsCalculator.calculate(changes, completed, ctx.rules(), range.from(), range.to())
                .changeFailRatePercent();
    }

    private static DurationStat timeToFirstReview(Context ctx, List<VcsPullRequest> prs, DayRange range) {
        return ReviewMetricsPolicy.aggregate(filter(prs, pr -> range.contains(pr.getCreatedAt())), ctx.now())
                .timeToFirstReview();
    }

    // ── portfolio ──

    private static List<PortfolioRow> portfolio(Context ctx) {
        return ctx.snapshot().projects().stream().map(p -> portfolioRow(ctx, p)).toList();
    }

    private static PortfolioRow portfolioRow(Context ctx, Project project) {
        ProjectView view = ctx.indexed().projects().get(project.getId());
        ProjectPeriodItems items = view.items();
        DayRange range = ctx.period().current();
        List<WorkItem> units = within(view.units(), range);
        Double points = items.complexityDeliveredIn(range);
        Double hours = items.effortLoggedIn(range);
        Double plannedPoints = items.sprintsEndingIn(range).stream().map(SprintFigures::complexityPlanned)
                .filter(Objects::nonNull).reduce(Double::sum).orElse(null);
        BigDecimal cost = ctx.indexed().allocatedIn(true).getOrDefault(project.getId(), BigDecimal.ZERO);
        CurrentSprint current = currentSprint(items, ctx.today());
        Double gap = current == null || current.elapsedPct() == null || current.progressPct() == null ? null
                : current.elapsedPct() - current.progressPct();
        Double lastPredictability = lastPastSprint(items, ctx.today()).map(SprintFigures::predictabilityPct).orElse(null);
        Double cfr = cfr(ctx, view.changes(), view.completed(), range);
        int stale = staleCount(ctx, project.getId(), view.pullRequests());
        List<VcsPullRequest> merged = filter(view.pullRequests(), pr -> range.contains(pr.getMergedAtIfMerged()));
        return new PortfolioRow(project.getId(), project.getName(),
                items.latestSprint().map(Sprint::getTeamName).orElse(null),
                project.getMemberIds() == null ? 0 : project.getMemberIds().size(), current, gap, lastPredictability,
                DashboardV2Views.averageOrNull(items.sprintsEndingIn(range).stream()
                        .map(SprintFigures::predictabilityPct).toList()),
                count(view.doneThroughput(), range),
                Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()), cfr,
                Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()), stale, cost,
                bucketCounts(ctx, view.doneThroughput()),
                PortfolioHealth.of(lastPredictability, stale, cfr, gap), points, plannedPoints,
                AiRatios.per(cost, points), AiRatios.per(cost, hours));
    }

    /** The current sprint (latest start among those covering today); {@code null} without one. */
    private static CurrentSprint currentSprint(ProjectPeriodItems items, LocalDate today) {
        return items.sprintsOverlapping(new DayRange(today, today)).stream()
                .filter(f -> f.sprint().getTimeFrame() == Sprint.TimeFrame.CURRENT)
                .reduce((first, second) -> second)
                .map(f -> {
                    var meta = DashboardV2Views.sprintMeta(f.sprint(), f.window(), today);
                    Double progress = f.planned() == null || f.planned() <= 0 || f.delivered() == null ? null
                            : f.delivered() / f.planned() * PERCENT;
                    Double elapsed = meta.dayCount() == 0 ? null : (double) meta.dayIndex() / meta.dayCount() * PERCENT;
                    return new CurrentSprint(f.sprint().getId(), f.sprint().getName(),
                            progress, elapsed, f.complexityDelivered(), f.complexityPlanned());
                })
                .orElse(null);
    }

    private static Optional<SprintFigures> lastPastSprint(ProjectPeriodItems items, LocalDate today) {
        List<SprintFigures> past = items.sprintsEndingIn(new DayRange(EPOCH, today));
        return past.isEmpty() ? Optional.empty() : Optional.of(past.getLast());
    }

    /** Stale pull requests of one project, judged by the branches of that project's own bindings. */
    private static int staleCount(Context ctx, String projectId, List<VcsPullRequest> prs) {
        Map<String, Set<String>> chosen = ctx.snapshot().vcs().chosenBranches(projectId);
        return (int) prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN
                && StalePullRequestPolicy.isStale(pr, ctx.now(), chosen)).count();
    }

    /** Stale pull requests of a repository bound to several projects: stalled under at least one of its links. */
    private static int staleCount(Context ctx, List<VcsPullRequest> prs) {
        Map<String, List<Set<String>>> perLink = ctx.snapshot().vcs().chosenBranchesPerLink();
        return (int) prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN)
                .filter(pr -> {
                    List<Set<String>> links = perLink.get(pr.getRepositoryId());
                    if (links == null || links.isEmpty()) return StalePullRequestPolicy.isStale(pr, ctx.now(), Set.<String>of());
                    return links.stream().anyMatch(chosen -> StalePullRequestPolicy.isStale(pr, ctx.now(), chosen));
                }).count();
    }

    /** Items completed per bucket, in one pass over the items (buckets are contiguous and ordered). */
    private static List<Integer> bucketCounts(Context ctx, List<WorkItem> done) {
        List<Bucket> buckets = ctx.buckets().buckets();
        int[] counts = new int[buckets.size()];
        for (WorkItem item : done) {
            int index = bucketOf(buckets, item.getCompletedAt());
            if (index >= 0) counts[index]++;
        }
        List<Integer> result = new ArrayList<>(counts.length);
        for (int c : counts) result.add(c);
        return result;
    }

    private static int bucketOf(List<Bucket> buckets, Instant at) {
        return at == null ? -1 : bucketOfDay(buckets, SprintWindow.toDay(at));
    }

    /** The bucket holding {@code day} (buckets are contiguous and ordered): binary search, -1 outside. */
    private static int bucketOfDay(List<Bucket> buckets, LocalDate day) {
        if (day == null || buckets.isEmpty() || day.isBefore(buckets.getFirst().range().first())
                || day.isAfter(buckets.getLast().range().last())) return -1;
        int low = 0;
        int high = buckets.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            DayRange range = buckets.get(mid).range();
            if (day.isBefore(range.first())) high = mid - 1;
            else if (day.isAfter(range.last())) low = mid + 1;
            else return mid;
        }
        return -1;
    }

    /** Values grouped by bucket in one pass ({@code at} gives each value's instant). */
    private static <T> List<List<T>> byBucket(Context ctx, Collection<T> values, Function<T, Instant> at) {
        List<Bucket> buckets = ctx.buckets().buckets();
        List<List<T>> groups = new ArrayList<>(buckets.size());
        for (int i = 0; i < buckets.size(); i++) groups.add(new ArrayList<>());
        for (T value : values) {
            int index = bucketOf(buckets, at.apply(value));
            if (index >= 0) groups.get(index).add(value);
        }
        return groups;
    }

    // ── sprint history ──

    /**
     * Per visible project, the sprints crossing the current period (oldest first, one per shared iteration —
     * the selection of {@link ProjectPeriodItems#sprintsOverlapping}). Cost and tokens are the VALIDATED
     * allocation to the project over the sprint's days, the base of the project overview's {@code SprintRow};
     * complexity, hours, predictability and the items done come from the sprint's {@link SprintFigures}.
     */
    private static SprintHistory sprintHistory(Context ctx) {
        DayRange period = ctx.period().current();
        List<ProjectSprints> projects = ctx.snapshot().projects().stream().map(project -> {
            ProjectView view = ctx.indexed().projects().get(project.getId());
            Map<LocalDate, DayAllocation> days = ctx.indexed().allocatedByDay().getOrDefault(project.getId(), Map.of());
            List<SprintPoint> sprints = view == null ? List.<SprintPoint>of()
                    : view.items().sprintsOverlapping(period).stream().map(f -> sprintPoint(f, days)).toList();
            return new ProjectSprints(project.getId(), project.getName(), sprints);
        }).toList();
        return new SprintHistory(projects);
    }

    private static SprintPoint sprintPoint(SprintFigures f, Map<LocalDate, DayAllocation> days) {
        DayRange window = new DayRange(f.window().startDay(), f.window().endDay());
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal tokens = null;
        for (Map.Entry<LocalDate, DayAllocation> day : days.entrySet()) {
            if (!window.containsDay(day.getKey())) continue;
            cost = cost.add(day.getValue().cost());
            tokens = tokens == null ? day.getValue().tokens() : tokens.add(day.getValue().tokens());
        }
        Double hours = f.summary().effortLogged();
        List<WorkItem> done = f.summary().doneUnits().stream().filter(u -> !ProjectPeriodItems.isContainer(u)).toList();
        return new SprintPoint(f.sprint().getId(), f.sprint().getName(), f.window().startDay().toString(),
                f.window().endDay().toString(), DashboardV2Views.isCurrent(f.sprint()), cost,
                tokens == null ? null : tokens.setScale(0, RoundingMode.HALF_UP).longValue(), f.complexityPlanned(),
                f.complexityDelivered(), hours, AiRatios.per(cost, f.complexityDelivered()), AiRatios.per(cost, hours),
                AiRatios.hoursPer(hours, f.complexityDelivered()), f.predictabilityPct(),
                Percentiles.of(done.stream().map(WorkItem::getLeadTimeHours).toList()), done.size());
    }

    // ── delivery / engineering ──

    private static Delivery delivery(Context ctx) {
        Indexed ix = ctx.indexed();
        Map<String, List<Integer>> perProject = new LinkedHashMap<>();
        ix.projects().forEach((id, view) -> perProject.put(id, bucketCounts(ctx, view.doneThroughput())));
        List<Bucket> buckets = ctx.buckets().buckets();
        List<ThroughputByProjectRow> byProject = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            int index = i;
            perProject.forEach((id, list) -> counts.put(id, list.get(index)));
            byProject.add(new ThroughputByProjectRow(buckets.get(i).start(), buckets.get(i).label(), counts));
        }
        List<List<WorkItem>> unitsByBucket = byBucket(ctx,
                ix.projects().values().stream().flatMap(p -> p.units().stream()).toList(), WorkItem::getCompletedAt);
        List<FlowRow> flow = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            List<WorkItem> units = unitsByBucket.get(i);
            flow.add(new FlowRow(buckets.get(i).start(), buckets.get(i).label(),
                    Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                    Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList())));
        }
        DayRange current = ctx.period().current();
        List<WorkItem> done = ix.projects().values().stream()
                .flatMap(p -> within(p.doneThroughput(), current).stream()).toList();
        List<TypeCount> typeMix = THROUGHPUT_TYPES.stream().map(t -> new TypeCount(t.name(),
                (int) done.stream().filter(i -> i.getResolvedType() == t).count())).toList();
        return new Delivery(byProject, flow, typeMix);
    }

    private static Engineering engineering(Context ctx) {
        List<VcsPullRequest> prs = ctx.snapshot().vcs().pullRequests();
        List<Bucket> buckets = ctx.buckets().buckets();
        List<List<VcsPullRequest>> created = byBucket(ctx, prs, VcsPullRequest::getCreatedAt);
        List<List<VcsPullRequest>> merged = byBucket(ctx, prs, VcsPullRequest::getMergedAtIfMerged);
        List<ActivityRow> activity = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            Bucket b = buckets.get(i);
            activity.add(new ActivityRow(b.start(), b.label(), (int) ctx.snapshot().vcs().commitsIn(b.range()),
                    created.get(i).size(), merged.get(i).size(),
                    ReviewMetricsPolicy.aggregate(created.get(i), ctx.now()).timeToFirstReview()));
        }
        List<RepoRow> repos = ctx.snapshot().vcs().repositories().stream().filter(r -> !r.isPendingFirstSync())
                .map(r -> repoRow(ctx, r))
                .sorted(Comparator.comparing(RepoRow::cfrPct, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RepoRow::repo, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Engineering(activity, repos);
    }

    private static RepoRow repoRow(Context ctx, VcsRepository repo) {
        DayRange range = ctx.period().current();
        List<VcsPullRequest> prs = ctx.indexed().prsByRepo().getOrDefault(repo.getId(), List.of());
        List<VcsPullRequest> open = filter(prs, pr -> pr.getStatus() == PullRequestStatus.OPEN);
        List<VcsPullRequest> merged = filter(prs, pr -> range.contains(pr.getMergedAtIfMerged()));
        return new RepoRow(repo.getId(), repoName(repo), repo.getProvider() != null ? repo.getProvider().name() : null,
                ctx.indexed().projectNamesByRepo().getOrDefault(repo.getId(), ""),
                cfr(ctx, ctx.indexed().changesByRepo().getOrDefault(repo.getId(), List.of()), List.of(), range),
                open.size(), staleCount(ctx, open),
                Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()));
    }

    private static String repoName(VcsRepository repo) {
        return repo.getDisplayName() != null ? repo.getDisplayName() : repo.getId();
    }

    // ── AI ──

    private static Ai ai(Context ctx) {
        Snapshot s = ctx.snapshot();
        List<AiUsageDayModel> dayModels = s.ai().byDayModel();
        Map<String, List<AiUsageDayModel>> byModelRows = dayModels.stream().filter(u -> u.model() != null)
                .collect(Collectors.groupingBy(AiUsageDayModel::model, LinkedHashMap::new, Collectors.toList()));
        // highest cost first; a tie is broken by the model name (ascending)
        List<String> models = byModelRows.entrySet().stream()
                .sorted(Map.Entry.<String, List<AiUsageDayModel>>comparingByValue(
                        Comparator.comparing(GetOrganizationDashboardV2UseCase::costOfDayModels)).reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).toList();
        List<AiModel> byModel = models.stream().map(m -> {
            List<AiUsageDayModel> of = byModelRows.get(m);
            return new AiModel(m, of.stream().mapToLong(u -> u.inputTokens() + u.outputTokens()).sum(),
                    costOfDayModels(of), of.stream().mapToLong(AiUsageDayModel::interactions).sum());
        }).toList();
        List<EditorUse> byEditor = s.ai().byEditor().stream().filter(e -> e.editor() != null)
                .collect(Collectors.toMap(AiUsageEditorTotal::editor, AiUsageEditorTotal::interactions, Long::sum,
                        LinkedHashMap::new))
                .entrySet().stream().map(e -> new EditorUse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(EditorUse::interactions).reversed()
                        .thenComparing(EditorUse::editor)).toList();
        Tokens tokens = new Tokens(dayModels.stream().mapToLong(AiUsageDayModel::inputTokens).sum(),
                dayModels.stream().mapToLong(AiUsageDayModel::outputTokens).sum());
        Adoption adoption = new Adoption((int) s.ai().byUserDay().stream().map(AiUsageUserDay::userId)
                .filter(Objects::nonNull).distinct().count(), s.people().size(), connectedPeople(s));
        return new Ai(models, costByBucket(ctx, dayModels, models), byModel, costByProject(ctx), byEditor, tokens,
                adoption, subscription(ctx));
    }

    /** Cost per model and bucket, in one pass over the day-model sums. */
    private static List<CostBucket> costByBucket(Context ctx, List<AiUsageDayModel> dayModels, List<String> models) {
        List<Bucket> buckets = ctx.buckets().buckets();
        List<Map<String, BigDecimal>> rows = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            Map<String, BigDecimal> row = new LinkedHashMap<>();
            models.forEach(m -> row.put(m, BigDecimal.ZERO));
            rows.add(row);
        }
        for (AiUsageDayModel u : dayModels) {
            if (u.model() == null || u.costUsd() == null) continue;
            int index = bucketOfDay(buckets, u.day());
            if (index >= 0) rows.get(index).merge(u.model(), u.costUsd(), BigDecimal::add);
        }
        List<CostBucket> result = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            result.add(new CostBucket(buckets.get(i).start(), buckets.get(i).label(), rows.get(i)));
        }
        return result;
    }

    /**
     * Allocated to each visible project, then the people's cost no allocation claimed
     * ({@code allocationId} absent) — {@code null} for a partial caller, so that no subtraction
     * isolates what the people spent on projects the caller does not see.
     */
    private static List<ProjectCost> costByProject(Context ctx) {
        Map<String, BigDecimal> allocated = ctx.indexed().allocatedIn(true);
        List<ProjectCost> rows = new ArrayList<>(ctx.snapshot().projects().stream()
                .map(p -> new ProjectCost(p.getId(), p.getName(), allocated.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList());
        rows.add(new ProjectCost(null, UNALLOCATED_NAME, ctx.snapshot().seesAll()
                ? sum(ctx.snapshot().ai().byUserDay().stream().map(AiUsageUserDay::unallocatedCostUsd).toList()) : null));
        return rows;
    }

    private static int connectedPeople(Snapshot s) {
        Set<String> withToken = s.connectorTokens().stream().filter(ConnectorToken::isActive)
                .map(ConnectorToken::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return (int) s.people().stream().filter(withToken::contains).count();
    }

    private static Subscription subscription(Context ctx) {
        List<AiUsageUserDay> userDays = ctx.snapshot().ai().byUserDay();
        BigDecimal consumption = sum(userDays.stream().map(AiUsageUserDay::costUsd).toList());
        Organization organization = ctx.snapshot().organization();
        BigDecimal perUser = organization != null ? organization.getSubscriptionCostPerUserUsd() : null;
        if (perUser == null) return new Subscription(consumption, null, null, null);
        BigDecimal months = BigDecimal.valueOf(ctx.period().days()).divide(DAYS_PER_MONTH, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal seats = perUser.multiply(BigDecimal.valueOf(ctx.snapshot().people().size()))
                .multiply(BigDecimal.valueOf(ctx.period().days())).divide(DAYS_PER_MONTH, SUBSCRIPTION_SCALE, RoundingMode.HALF_UP);
        int above = (int) userDays.stream().filter(d -> d.userId() != null)
                .collect(Collectors.groupingBy(AiUsageUserDay::userId, Collectors.mapping(AiUsageUserDay::costUsd, Collectors.toList())))
                .values().stream()
                .filter(costs -> sum(costs).divide(months, MONEY_SCALE, RoundingMode.HALF_UP).compareTo(perUser) > 0)
                .count();
        return new Subscription(consumption, seats, perUser, above);
    }

    // ── integrations ──

    private static Integrations integrations(Context ctx) {
        Snapshot s = ctx.snapshot();
        ConnectionCard azure = s.azureConnection().map(c -> new ConnectionCard(
                c.getStatus() != null ? c.getStatus().name() : null, c.getLastSuccessAt(), c.getConsecutiveFailures(),
                error(c.getLastErrorCode()))).orElse(disconnected());
        ConnectionCard github = s.githubConnection().map(c -> new ConnectionCard(
                c.getStatus() != null ? c.getStatus().name() : null, c.getLastSuccessAt(), c.getConsecutiveFailures(),
                error(c.getLastErrorCode()))).orElse(disconnected());
        AiConnectorCard aiConnector = new AiConnectorCard(activeTokens(s),
                s.ai().byDayModel().stream().map(AiUsageDayModel::lastUploadedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null));
        return new Integrations(azure, github, aiConnector, syncByDay(s.syncRuns(), ctx.today()), coverage(ctx),
                sources(ctx));
    }

    /** Active tokens of the organization for a caller who sees everything; of the people otherwise. */
    private static int activeTokens(Snapshot s) {
        return (int) s.connectorTokens().stream().filter(ConnectorToken::isActive)
                .filter(t -> s.seesAll() || (t.getUserId() != null && s.people().contains(t.getUserId())))
                .count();
    }

    private static ConnectionCard disconnected() {
        return new ConnectionCard(DISCONNECTED, null, null, null);
    }

    private static ConnectionError error(String code) {
        return code == null ? null : new ConnectionError(code, null);
    }

    /** Leaf runs per day and result (the read already kept leaf operations only; RUNNING is no result). */
    private static List<SyncOutcomeDay> syncByDay(List<SyncRunCount> runs, LocalDate today) {
        Map<LocalDate, List<SyncRunCount>> byDay = runs.stream().filter(r -> r.day() != null)
                .collect(Collectors.groupingBy(SyncRunCount::day));
        return SyncRunsSummary.days(today).stream().map(day -> {
            List<SyncRunCount> ofDay = byDay.getOrDefault(day, List.of());
            return new SyncOutcomeDay(day.toString(), DashboardV2Views.label(day), countStatus(ofDay, SyncStatus.SUCCESS),
                    countStatus(ofDay, SyncStatus.PARTIAL), countStatus(ofDay, SyncStatus.FAILED));
        }).toList();
    }

    private static int countStatus(List<SyncRunCount> runs, SyncStatus status) {
        return (int) runs.stream().filter(r -> r.status() == status).mapToLong(SyncRunCount::count).sum();
    }

    private static Coverage coverage(Context ctx) {
        Snapshot s = ctx.snapshot();
        Map<String, User> users = s.users().stream().filter(u -> u.getId() != null)
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
        List<User> people = s.people().stream().map(users::get).filter(Objects::nonNull).toList();
        int total = s.people().size();
        DayRange current = ctx.period().current();
        List<SprintFigures> sprints = s.items().values().stream().flatMap(i -> i.sprintsEndingIn(current).stream()).toList();
        long units = sprints.stream().mapToLong(f -> f.summary().units().size()).sum();
        long estimated = sprints.stream().mapToLong(f -> f.summary().units().stream()
                .filter(u -> f.summary().scope().effectiveEffort(u) != null).count()).sum();
        List<VcsPullRequest> created = filter(s.vcs().pullRequests(), pr -> current.contains(pr.getCreatedAt()));
        return new Coverage(Ratios.percent(people.stream().filter(u -> present(u.getAzureId())).count(), total),
                Ratios.percent(people.stream().filter(u -> present(u.getGithubId())).count(), total),
                Ratios.percent(connectedPeople(s), total), Ratios.percent(estimated, units),
                Ratios.percent(created.stream().filter(pr -> pr.getLinkedWorkItemExternalId() != null).count(),
                        created.size()),
                aiDaysAllocated(ctx));
    }

    /**
     * Person-days of the people's usage claimed by an allocation (validated or pending) over all
     * person-days with usage. Claimed is read from the records ({@code allocationId}); the validated
     * allocations loaded only split the claimed days into validated and pending, which this ratio sums.
     */
    private static Double aiDaysAllocated(Context ctx) {
        Snapshot s = ctx.snapshot();
        DayRange current = ctx.period().current();
        Set<String> validated = s.ai().allocations().stream()
                .filter(a -> a.userId() != null && current.containsDay(a.day()) && s.people().contains(a.userId()))
                .map(a -> DashboardV2Views.personDay(a.userId(), a.day())).collect(Collectors.toSet());
        AllocationSummary summary = DashboardV2Views.allocationSummaryOfDays(s.ai().byUserDay(), validated);
        long total = (long) summary.validatedDays() + summary.pendingDays() + summary.unallocatedDays();
        return Ratios.percent((long) summary.validatedDays() + summary.pendingDays(), total);
    }

    private static List<ProjectSources> sources(Context ctx) {
        Snapshot s = ctx.snapshot();
        Map<String, VcsRepository> repositories = s.vcs().repositories().stream().filter(r -> r.getId() != null)
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
        Map<String, List<VcsRepoBinding>> bindings = s.vcs().bindings().stream()
                .collect(Collectors.groupingBy(VcsRepoBinding::getProjectId));
        Map<String, andromeda.sei.ms.feature.azure.domain.AzureProjectBinding> azureByProject = s.azureBindings().stream()
                .collect(Collectors.toMap(b -> b.getProjectId(), Function.identity(), (a, b) -> a));
        return s.projects().stream().map(p -> {
            var azure = Optional.ofNullable(azureByProject.get(p.getId()));
            List<RepoSource> repos = bindings.getOrDefault(p.getId(), List.of()).stream()
                    .map(b -> {
                        VcsRepository repo = repositories.get(b.getRepositoryId());
                        return new RepoSource(b.getRepositoryId(), repo != null ? repoName(repo) : b.getRepositoryId(),
                                b.getProvider() != null ? b.getProvider().name() : null, b.isSyncEnabled(),
                                b.getLastSyncAt());
                    })
                    .sorted(Comparator.comparing(RepoSource::repo, String.CASE_INSENSITIVE_ORDER)).toList();
            return new ProjectSources(p.getId(), p.getName(), azure.map(b -> b.getAzureProjectName()).orElse(null),
                    azure.map(b -> b.isSyncEnabled()).orElse(null), azure.map(b -> b.getLastSyncAt()).orElse(null), repos);
        }).toList();
    }

    // ── shared ──

    private static BigDecimal costOfDayModels(Collection<AiUsageDayModel> rows) {
        return sum(rows.stream().map(AiUsageDayModel::costUsd).toList());
    }

    private static BigDecimal sum(Collection<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static int count(List<WorkItem> items, DayRange range) {
        return (int) items.stream().filter(i -> range.contains(i.getCompletedAt())).count();
    }

    private static List<WorkItem> within(List<WorkItem> items, DayRange range) {
        return filter(items, i -> range.contains(i.getCompletedAt()));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> filter(Collection<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).toList();
    }

    /**
     * One visible project, sliced once: its throughput-type items done (with a completion date), its
     * deliverable units done (epics and features out), its completed items for the delivery metrics,
     * and the pull requests and merged changes of its repositories.
     */
    record ProjectView(ProjectPeriodItems items, List<WorkItem> doneThroughput, List<WorkItem> units,
                       List<CompletedWorkItem> completed, List<VcsPullRequest> pullRequests, List<MergedChange> changes) {}

    /**
     * Everything the figures look up, built once in linear time: the project views, the pull requests
     * and merged changes by repository, the names of the visible projects bound to each repository,
     * all completed items, and the cost allocated to each project in each period.
     */
    record Indexed(Map<String, ProjectView> projects, Map<String, List<VcsPullRequest>> prsByRepo,
                   Map<String, List<MergedChange>> changesByRepo, Map<String, String> projectNamesByRepo,
                   List<CompletedWorkItem> completed, Map<String, BigDecimal> allocatedCurrent,
                   Map<String, BigDecimal> allocatedPrevious,
                   Map<String, Map<LocalDate, DayAllocation>> allocatedByDay) {

        static Indexed of(Snapshot s, DashboardPeriod period) {
            Map<String, List<VcsPullRequest>> prsByRepo = s.vcs().pullRequests().stream()
                    .filter(pr -> pr.getRepositoryId() != null)
                    .collect(Collectors.groupingBy(VcsPullRequest::getRepositoryId));
            Map<String, String> repoOfPr = new HashMap<>();
            s.vcs().pullRequests().forEach(pr -> repoOfPr.put(pr.getId(), pr.getRepositoryId()));
            Map<String, List<MergedChange>> changesByRepo = s.vcs().changes().stream()
                    .filter(c -> repoOfPr.get(c.id()) != null)
                    .collect(Collectors.groupingBy(c -> repoOfPr.get(c.id())));
            Map<String, ProjectView> projects = new LinkedHashMap<>();
            s.items().forEach((projectId, items) -> projects.put(projectId,
                    view(items, s.vcs().repositoryIdsOf(projectId), prsByRepo, changesByRepo)));
            Map<String, String> names = s.projects().stream().collect(Collectors.toMap(Project::getId,
                    p -> p.getName() == null ? "" : p.getName(), (a, b) -> a));
            Map<String, String> projectNamesByRepo = s.vcs().bindings().stream()
                    .collect(Collectors.groupingBy(VcsRepoBinding::getRepositoryId, Collectors.mapping(
                            b -> names.getOrDefault(b.getProjectId(), ""),
                            Collectors.collectingAndThen(Collectors.toCollection(java.util.TreeSet::new),
                                    set -> String.join(PROJECT_SEPARATOR, set)))));
            List<CompletedWorkItem> completed = projects.values().stream().flatMap(p -> p.completed().stream()).toList();
            return new Indexed(projects, prsByRepo, changesByRepo, projectNamesByRepo, completed,
                    allocated(s, period.current()), allocated(s, period.previous()), allocatedByDay(s));
        }

        /**
         * Cost and tokens (input + output) allocated (validated) to each visible project on each day, over
         * every allocation loaded: the same basis as {@link #allocated}, weighted by the split percentage.
         * One pass over allocations × splits; a sprint sums its days from here.
         */
        static Map<String, Map<LocalDate, DayAllocation>> allocatedByDay(Snapshot s) {
            Map<String, Map<LocalDate, DayAllocation>> byProject = new HashMap<>();
            for (AllocationCoverage allocation : s.ai().allocations()) {
                if (allocation.day() == null || allocation.splits() == null) continue;
                BigDecimal cost = allocation.costUsd() == null ? BigDecimal.ZERO : allocation.costUsd();
                long tokens = allocation.tokens();
                if (cost.signum() == 0 && tokens == 0) continue;
                for (AllocationSplit split : allocation.splits()) {
                    String projectId = split.getWorkItemId() == null ? null : s.itemProject().get(split.getWorkItemId());
                    if (projectId == null || split.getPercentage() == null) continue;
                    DayAllocation share = new DayAllocation(cost.multiply(split.getPercentage()),
                            split.getPercentage().multiply(BigDecimal.valueOf(tokens)));
                    byProject.computeIfAbsent(projectId, p -> new HashMap<>())
                            .merge(allocation.day(), share, DayAllocation::plus);
                }
            }
            return byProject;
        }

        private static ProjectView view(ProjectPeriodItems items, Set<String> repositoryIds,
                                        Map<String, List<VcsPullRequest>> prsByRepo,
                                        Map<String, List<MergedChange>> changesByRepo) {
            List<WorkItem> doneThroughput = items.all().stream().filter(i -> i.getResolvedStatus() == WorkItem.ResolvedStatus.DONE
                    && THROUGHPUT_TYPES.contains(i.getResolvedType()) && i.getCompletedAt() != null).toList();
            List<WorkItem> units = items.effort().doneUnits().stream()
                    .filter(u -> !ProjectPeriodItems.isContainer(u) && u.getCompletedAt() != null).toList();
            List<VcsPullRequest> prs = repositoryIds.stream().flatMap(r -> prsByRepo.getOrDefault(r, List.of()).stream())
                    .toList();
            List<MergedChange> changes = repositoryIds.stream()
                    .flatMap(r -> changesByRepo.getOrDefault(r, List.of()).stream()).toList();
            return new ProjectView(items, doneThroughput, units, DeliveryMetricsService.completedItems(items.all()), prs,
                    changes);
        }

        /**
         * Cost of the range allocated to each visible project: for every validated allocation of the
         * range, the cost of the records it covers × each split percentage, added to the project that
         * owns the split's work item ({@code workItemId → projectId}, built once). Linear in
         * allocations × splits.
         */
        static Map<String, BigDecimal> allocated(Snapshot s, DayRange range) {
            Map<String, BigDecimal> byProject = new HashMap<>();
            for (AllocationCoverage allocation : s.ai().allocations()) {
                if (!range.containsDay(allocation.day()) || allocation.splits() == null) continue;
                BigDecimal basis = allocation.costUsd() == null ? BigDecimal.ZERO : allocation.costUsd();
                if (basis.signum() == 0) continue;
                for (AllocationSplit split : allocation.splits()) {
                    String projectId = split.getWorkItemId() == null ? null : s.itemProject().get(split.getWorkItemId());
                    if (projectId != null && split.getPercentage() != null) {
                        byProject.merge(projectId, basis.multiply(split.getPercentage()), BigDecimal::add);
                    }
                }
            }
            return byProject;
        }

        Map<String, BigDecimal> allocatedIn(boolean current) {
            return current ? allocatedCurrent : allocatedPrevious;
        }

        List<WorkItem> unitsIn(DayRange range) {
            return projects.values().stream().flatMap(p -> within(p.units(), range).stream()).toList();
        }
    }

    /** Cost and (share-weighted) tokens allocated to one project on one day. */
    record DayAllocation(BigDecimal cost, BigDecimal tokens) {
        DayAllocation plus(DayAllocation other) {
            return new DayAllocation(cost.add(other.cost), tokens.add(other.tokens));
        }
    }

    private record Context(Snapshot snapshot, DashboardPeriod period, PeriodBuckets buckets, Instant now,
                           LocalDate today, RemediationRules rules, Indexed indexed) {}
}
