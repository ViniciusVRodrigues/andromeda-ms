package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IntegrationsDashboardV2Output.LastError;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ActivityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Adoption;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.AiConnectorCard;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ConnectionCard;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.CostBucket;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Coverage;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.CurrentSprint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Delivery;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.EditorUse;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Engineering;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.FlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.FluencyCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Integrations;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.OrgStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.PortfolioRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ProjectSources;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.RepoRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.RepoSource;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Subscription;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.SyncOutcomeDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.ThroughputByProjectRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.Tokens;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.OrganizationDashboardV2Output.TypeCount;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.DayRange;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData;
import andromeda.sei.ms.feature.dashboard.application.v2.OrganizationDashboardData.Snapshot;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets.Bucket;
import andromeda.sei.ms.feature.dashboard.application.v2.PortfolioHealth;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems.SprintFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.dashboard.application.v2.SyncRunsSummary;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
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
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
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

/**
 * Organization overview v2 (dashboards-customizaveis D1, {@code buildOrganization}) over the
 * projects where the caller holds {@code DASHBOARD_STRATEGIC} — never the others: every figure is
 * computed from {@link OrganizationDashboardData#load}, which reads nothing project-scoped outside
 * {@code visibleProjectIds}. Each figure follows the rule that already exists for one project
 * ({@link GetProjectOverviewV2UseCase}): velocity by the effort resolver, durations by
 * {@link Percentiles}, time to first review by {@link ReviewMetricsPolicy}, stalled pull requests by
 * {@link StalePullRequestPolicy}, change failure rate by {@link DeliveryMetricsCalculator}.
 */
@Service
public class GetOrganizationDashboardV2UseCase {

    static final String UNALLOCATED_NAME = "Não alocado a work items";
    private static final int MONEY_SCALE = 6;
    private static final int SUBSCRIPTION_SCALE = 2;
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final double PERCENT = 100.0;
    private static final List<Integer> FLUENCY_STAGES = List.of(1, 2, 3, 4);
    private static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
    private static final String PROJECT_SEPARATOR = ", ";
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
                allocatedByProject(snapshot));
        return new OrganizationDashboardV2Output(periodInfo(ctx), stats(ctx, period.current(), true),
                stats(ctx, period.previous(), false), portfolio(ctx), delivery(ctx), engineering(ctx), ai(ctx),
                integrations(ctx), now);
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
        List<WorkItem> units = units(ctx, range);
        Double sp = spDelivered(ctx, range);
        List<AIUsage> usage = usageIn(ctx, range);
        BigDecimal cost = cost(usage);
        return new OrgStats(current ? s.projects().size() : null, current ? s.people().size() : null,
                s.items().values().stream().mapToInt(i -> doneOfThroughputTypes(i, range).size()).sum(), sp,
                Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList()),
                cfr(ctx, s.vcs().changes(), completedItems(s.items().values()), range),
                (int) s.vcs().pullRequests().stream().filter(pr -> range.contains(pr.getMergedAt())).count(),
                (int) s.vcs().commits().stream().filter(c -> range.contains(c.getAuthoredAt())).count(),
                timeToFirstReview(ctx, s.vcs().pullRequests(), range), cost,
                usage.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum(), perSp(cost, sp),
                (int) usage.stream().map(AIUsage::getUserId).filter(Objects::nonNull).distinct().count());
    }

    private static List<WorkItem> units(Context ctx, DayRange range) {
        return ctx.snapshot().items().values().stream().flatMap(i -> i.doneUnitsIn(range).stream()).toList();
    }

    /** Sum of each project's delivered effort (each by its own effort level); {@code null} when none carries effort. */
    private static Double spDelivered(Context ctx, DayRange range) {
        List<Double> perProject = ctx.snapshot().items().values().stream()
                .map(i -> i.effort().scope().totalEffort(i.doneUnitsIn(range))).filter(Objects::nonNull).toList();
        return perProject.isEmpty() ? null : perProject.stream().mapToDouble(Double::doubleValue).sum();
    }

    private static List<WorkItem> doneOfThroughputTypes(ProjectPeriodItems items, DayRange range) {
        return items.all().stream().filter(i -> i.getResolvedStatus() == WorkItem.ResolvedStatus.DONE
                && THROUGHPUT_TYPES.contains(i.getResolvedType()) && range.contains(i.getCompletedAt())).toList();
    }

    private static List<CompletedWorkItem> completedItems(Collection<ProjectPeriodItems> items) {
        return DeliveryMetricsService.completedItems(items.stream().flatMap(i -> i.all().stream()).toList());
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
        ProjectPeriodItems items = ctx.snapshot().items().get(project.getId());
        DayRange range = ctx.period().current();
        ProjectVcs vcs = projectVcs(ctx, project.getId());
        List<WorkItem> units = items.doneUnitsIn(range);
        Double sp = items.effort().scope().totalEffort(units);
        BigDecimal cost = ctx.allocatedByProject().getOrDefault(project.getId(), BigDecimal.ZERO);
        CurrentSprint current = currentSprint(items, ctx.today());
        Double gap = current == null || current.elapsedPct() == null || current.progressPct() == null ? null
                : current.elapsedPct() - current.progressPct();
        Double lastPredictability = lastPastSprint(items, ctx.today()).map(SprintFigures::predictabilityPct).orElse(null);
        Double cfr = cfr(ctx, vcs.changes(), DeliveryMetricsService.completedItems(items.all()), range);
        int stale = staleCount(ctx, vcs.pullRequests());
        List<VcsPullRequest> merged = filter(vcs.pullRequests(), pr -> range.contains(pr.getMergedAt()));
        return new PortfolioRow(project.getId(), project.getName(),
                items.latestSprint().map(Sprint::getTeamName).orElse(null),
                project.getMemberIds() == null ? 0 : project.getMemberIds().size(), current, gap, lastPredictability,
                DashboardV2Views.averageOrNull(items.sprintsEndingIn(range).stream()
                        .map(SprintFigures::predictabilityPct).toList()),
                doneOfThroughputTypes(items, range).size(), sp,
                Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()), cfr,
                Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()), stale, cost,
                perSp(cost, sp), ctx.buckets().buckets().stream()
                .map(b -> doneOfThroughputTypes(items, b.range()).size()).toList(),
                PortfolioHealth.of(lastPredictability, stale, cfr, gap));
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
                    return new CurrentSprint(f.sprint().getId(), f.sprint().getName(), f.delivered(), f.planned(),
                            progress, elapsed);
                })
                .orElse(null);
    }

    private static Optional<SprintFigures> lastPastSprint(ProjectPeriodItems items, LocalDate today) {
        List<SprintFigures> past = items.sprintsEndingIn(new DayRange(EPOCH, today));
        return past.isEmpty() ? Optional.empty() : Optional.of(past.getLast());
    }

    private static int staleCount(Context ctx, List<VcsPullRequest> prs) {
        return (int) prs.stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN
                && StalePullRequestPolicy.isStale(pr, ctx.now())).count();
    }

    private static ProjectVcs projectVcs(Context ctx, String projectId) {
        Set<String> repositoryIds = ctx.snapshot().vcs().repositoryIdsOf(projectId);
        List<VcsPullRequest> prs = filter(ctx.snapshot().vcs().pullRequests(),
                pr -> repositoryIds.contains(pr.getRepositoryId()));
        Set<String> prIds = prs.stream().map(VcsPullRequest::getId).collect(Collectors.toSet());
        return new ProjectVcs(prs, filter(ctx.snapshot().vcs().changes(), c -> prIds.contains(c.id())));
    }

    // ── delivery / engineering ──

    private static Delivery delivery(Context ctx) {
        Snapshot s = ctx.snapshot();
        List<ThroughputByProjectRow> byProject = ctx.buckets().buckets().stream().map(b -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            s.items().forEach((id, items) -> counts.put(id, doneOfThroughputTypes(items, b.range()).size()));
            return new ThroughputByProjectRow(b.start(), b.label(), counts);
        }).toList();
        List<FlowRow> flow = ctx.buckets().buckets().stream().map(b -> {
            List<WorkItem> units = units(ctx, b.range());
            return new FlowRow(b.start(), b.label(), Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                    Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList()));
        }).toList();
        List<WorkItem> done = s.items().values().stream()
                .flatMap(i -> doneOfThroughputTypes(i, ctx.period().current()).stream()).toList();
        List<TypeCount> typeMix = THROUGHPUT_TYPES.stream().map(t -> new TypeCount(t.name(),
                (int) done.stream().filter(i -> i.getResolvedType() == t).count())).toList();
        return new Delivery(byProject, flow, typeMix);
    }

    private static Engineering engineering(Context ctx) {
        List<VcsPullRequest> prs = ctx.snapshot().vcs().pullRequests();
        List<VcsCommit> commits = ctx.snapshot().vcs().commits();
        List<ActivityRow> activity = ctx.buckets().buckets().stream().map(b -> new ActivityRow(b.start(), b.label(),
                (int) commits.stream().filter(c -> b.range().contains(c.getAuthoredAt())).count(),
                (int) prs.stream().filter(pr -> b.range().contains(pr.getCreatedAt())).count(),
                (int) prs.stream().filter(pr -> b.range().contains(pr.getMergedAt())).count(),
                timeToFirstReview(ctx, prs, b.range()))).toList();
        List<RepoRow> repos = ctx.snapshot().vcs().repositories().stream().filter(r -> !r.isPendingFirstSync())
                .map(r -> repoRow(ctx, r))
                .sorted(Comparator.comparing(RepoRow::cfrPct, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RepoRow::repo, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return new Engineering(activity, repos);
    }

    private static RepoRow repoRow(Context ctx, VcsRepository repo) {
        DayRange range = ctx.period().current();
        List<VcsPullRequest> prs = filter(ctx.snapshot().vcs().pullRequests(), pr -> repo.getId().equals(pr.getRepositoryId()));
        Set<String> prIds = prs.stream().map(VcsPullRequest::getId).collect(Collectors.toSet());
        List<VcsPullRequest> open = filter(prs, pr -> pr.getStatus() == PullRequestStatus.OPEN);
        List<VcsPullRequest> merged = filter(prs, pr -> range.contains(pr.getMergedAt()));
        return new RepoRow(repo.getId(), repoName(repo), repo.getProvider() != null ? repo.getProvider().name() : null,
                projectNamesOf(ctx, repo.getId()),
                cfr(ctx, filter(ctx.snapshot().vcs().changes(), c -> prIds.contains(c.id())), List.of(), range),
                open.size(), staleCount(ctx, open),
                Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()));
    }

    private static String projectNamesOf(Context ctx, String repositoryId) {
        Set<String> projectIds = ctx.snapshot().vcs().bindings().stream()
                .filter(b -> repositoryId.equals(b.getRepositoryId())).map(VcsRepoBinding::getProjectId)
                .collect(Collectors.toSet());
        return ctx.snapshot().projects().stream().filter(p -> projectIds.contains(p.getId())).map(Project::getName)
                .collect(Collectors.joining(PROJECT_SEPARATOR));
    }

    private static String repoName(VcsRepository repo) {
        return repo.getDisplayName() != null ? repo.getDisplayName() : repo.getId();
    }

    // ── AI ──

    /**
     * Cost of the current period allocated to each visible project: for every validated allocation,
     * the cost of the records it covers × the sum of its split percentages pointing to the project's
     * work items (the basis of {@code ProjectAiCostCalculator}).
     */
    private static Map<String, BigDecimal> allocatedByProject(Snapshot s) {
        Map<String, BigDecimal> byProject = new HashMap<>();
        for (AiUsageAllocation allocation : s.ai().allocations()) {
            BigDecimal basis = allocation.getAiUsageIds() == null ? BigDecimal.ZERO : allocation.getAiUsageIds().stream()
                    .map(id -> s.ai().usageById().get(id)).filter(u -> u != null && u.getCostUsd() != null)
                    .map(AIUsage::getCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
            s.items().forEach((projectId, items) -> {
                BigDecimal share = share(allocation, items.ids());
                if (share.signum() > 0) byProject.merge(projectId, basis.multiply(share), BigDecimal::add);
            });
        }
        return byProject;
    }

    private static BigDecimal share(AiUsageAllocation allocation, Set<String> workItemIds) {
        if (allocation.getSplits() == null) return BigDecimal.ZERO;
        return allocation.getSplits().stream()
                .filter(split -> split.getPercentage() != null && workItemIds.contains(split.getWorkItemId()))
                .map(AllocationSplit::getPercentage).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Ai ai(Context ctx) {
        Snapshot s = ctx.snapshot();
        List<AIUsage> usage = usageIn(ctx, ctx.period().current());
        List<String> models = byKey(usage, AIUsage::getModel).entrySet().stream()
                .sorted(Map.Entry.<String, List<AIUsage>>comparingByValue(Comparator.comparing(GetOrganizationDashboardV2UseCase::cost)).reversed())
                .map(Map.Entry::getKey).toList();
        List<CostBucket> costByBucket = ctx.buckets().buckets().stream().map(b -> costBucket(usage, models, b)).toList();
        List<AiModel> byModel = models.stream().map(m -> {
            List<AIUsage> of = filter(usage, u -> m.equals(u.getModel()));
            return new AiModel(m, of.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum(), cost(of),
                    of.stream().mapToLong(AIUsage::getInteractions).sum());
        }).toList();
        List<EditorUse> byEditor = byKey(usage, AIUsage::getEditor).entrySet().stream()
                .map(e -> new EditorUse(e.getKey(), e.getValue().stream().mapToLong(AIUsage::getInteractions).sum()))
                .sorted(Comparator.comparingLong(EditorUse::interactions).reversed()).toList();
        Tokens tokens = new Tokens(usage.stream().mapToLong(AIUsage::getInputTokens).sum(),
                usage.stream().mapToLong(AIUsage::getOutputTokens).sum());
        Adoption adoption = new Adoption((int) usage.stream().map(AIUsage::getUserId).filter(Objects::nonNull).distinct()
                .count(), s.people().size(), connectedPeople(s));
        return new Ai(models, costByBucket, byModel, costByProject(ctx, usage), byEditor, tokens, adoption,
                fluency(s), subscription(ctx, usage));
    }

    private static CostBucket costBucket(List<AIUsage> usage, List<String> models, Bucket bucket) {
        Map<String, BigDecimal> byModel = new LinkedHashMap<>();
        models.forEach(m -> byModel.put(m, cost(filter(usage,
                u -> m.equals(u.getModel()) && bucket.range().containsDay(u.getDay())))));
        return new CostBucket(bucket.start(), bucket.label(), byModel);
    }

    /** Allocated to each visible project, then the people's cost no allocation claimed ({@code allocationId} absent). */
    private static List<ProjectCost> costByProject(Context ctx, List<AIUsage> usage) {
        List<ProjectCost> rows = new java.util.ArrayList<>(ctx.snapshot().projects().stream()
                .map(p -> new ProjectCost(p.getId(), p.getName(),
                        ctx.allocatedByProject().getOrDefault(p.getId(), BigDecimal.ZERO)))
                .toList());
        rows.add(new ProjectCost(null, UNALLOCATED_NAME, cost(filter(usage, u -> u.getAllocationId() == null))));
        return rows;
    }

    private static int connectedPeople(Snapshot s) {
        Set<String> withToken = s.connectorTokens().stream().filter(ConnectorToken::isActive)
                .map(ConnectorToken::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        return (int) s.people().stream().filter(withToken::contains).count();
    }

    private static List<FluencyCount> fluency(Snapshot s) {
        Map<Integer, Long> byStage = s.fluencyScores().stream()
                .filter(f -> f.getUserId() != null && s.people().contains(f.getUserId()))
                .collect(Collectors.groupingBy(f -> f.getOverallStage(), Collectors.counting()));
        return FLUENCY_STAGES.stream().map(stage -> new FluencyCount(stage, byStage.getOrDefault(stage, 0L).intValue()))
                .toList();
    }

    private static Subscription subscription(Context ctx, List<AIUsage> usage) {
        BigDecimal consumption = cost(usage);
        Organization organization = ctx.snapshot().organization();
        BigDecimal perUser = organization != null ? organization.getSubscriptionCostPerUserUsd() : null;
        if (perUser == null) return new Subscription(consumption, null, null, null);
        BigDecimal months = BigDecimal.valueOf(ctx.period().days()).divide(DAYS_PER_MONTH, MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal seats = perUser.multiply(BigDecimal.valueOf(ctx.snapshot().people().size()))
                .multiply(BigDecimal.valueOf(ctx.period().days())).divide(DAYS_PER_MONTH, SUBSCRIPTION_SCALE, RoundingMode.HALF_UP);
        int above = (int) byKey(usage, AIUsage::getUserId).values().stream()
                .filter(records -> cost(records).divide(months, MONEY_SCALE, RoundingMode.HALF_UP).compareTo(perUser) > 0)
                .count();
        return new Subscription(consumption, seats, perUser, above);
    }

    private static List<AIUsage> usageIn(Context ctx, DayRange range) {
        return filter(ctx.snapshot().ai().usage(), u -> range.containsDay(u.getDay()));
    }

    // ── integrations ──

    private static Integrations integrations(Context ctx) {
        Snapshot s = ctx.snapshot();
        ConnectionCard azure = s.azureConnection().map(c -> new ConnectionCard(
                c.getStatus() != null ? c.getStatus().name() : null, c.getLastSuccessAt(), c.getConsecutiveFailures(),
                lastError(c.getLastErrorCode(), c.getLastErrorMessage()))).orElse(disconnected());
        ConnectionCard github = s.githubConnection().map(c -> new ConnectionCard(
                c.getStatus() != null ? c.getStatus().name() : null, c.getLastSuccessAt(), c.getConsecutiveFailures(),
                lastError(c.getLastErrorCode(), c.getLastErrorMessage()))).orElse(disconnected());
        AiConnectorCard aiConnector = new AiConnectorCard(
                (int) s.connectorTokens().stream().filter(ConnectorToken::isActive).count(),
                s.ai().usage().stream().map(AIUsage::getUploadedAt).filter(Objects::nonNull)
                        .max(Comparator.naturalOrder()).orElse(null));
        return new Integrations(azure, github, aiConnector, syncByDay(s.syncLogs(), ctx.today()), coverage(ctx),
                sources(s));
    }

    private static ConnectionCard disconnected() {
        return new ConnectionCard("DISCONNECTED", null, null, null);
    }

    private static LastError lastError(String code, String message) {
        return code == null ? null : new LastError(code, message, null);
    }

    private static List<SyncOutcomeDay> syncByDay(List<IntegrationSyncLog> logs, LocalDate today) {
        List<IntegrationSyncLog> runs = filter(logs, SyncRunsSummary::isRun);
        return SyncRunsSummary.days(today).stream().map(day -> {
            List<IntegrationSyncLog> ofDay = filter(runs, l -> SyncRunsSummary.dayOf(l).equals(day));
            return new SyncOutcomeDay(day.toString(), DashboardV2Views.label(day), countStatus(ofDay, SyncStatus.SUCCESS),
                    countStatus(ofDay, SyncStatus.PARTIAL), countStatus(ofDay, SyncStatus.FAILED));
        }).toList();
    }

    private static int countStatus(List<IntegrationSyncLog> logs, SyncStatus status) {
        return (int) logs.stream().filter(l -> l.getStatus() == status).count();
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
        Set<String> validated = s.ai().allocations().stream()
                .filter(a -> a.getUserId() != null && a.getDay() != null && s.people().contains(a.getUserId()))
                .map(a -> DashboardV2Views.personDay(a.getUserId(), a.getDay())).collect(Collectors.toSet());
        AllocationSummary summary = DashboardV2Views.allocationSummary(usageIn(ctx, ctx.period().current()), validated);
        long total = (long) summary.validatedDays() + summary.pendingDays() + summary.unallocatedDays();
        return Ratios.percent((long) summary.validatedDays() + summary.pendingDays(), total);
    }

    private static List<ProjectSources> sources(Snapshot s) {
        Map<String, VcsRepository> repositories = s.vcs().repositories().stream().filter(r -> r.getId() != null)
                .collect(Collectors.toMap(VcsRepository::getId, Function.identity(), (a, b) -> a));
        return s.projects().stream().map(p -> {
            var azure = s.azureBindings().stream().filter(b -> p.getId().equals(b.getProjectId())).findFirst();
            List<RepoSource> repos = s.vcs().bindings().stream().filter(b -> p.getId().equals(b.getProjectId()))
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

    private static BigDecimal cost(Collection<AIUsage> usage) {
        return usage.stream().map(AIUsage::getCostUsd).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal perSp(BigDecimal cost, Double delivered) {
        if (cost == null || delivered == null || delivered <= 0) return null;
        return cost.divide(BigDecimal.valueOf(delivered), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Records grouped by a key, nulls left out, in first-seen order. */
    private static Map<String, List<AIUsage>> byKey(List<AIUsage> usage, Function<AIUsage, String> key) {
        return usage.stream().filter(u -> key.apply(u) != null)
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> List<T> filter(Collection<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).toList();
    }

    private record ProjectVcs(List<VcsPullRequest> pullRequests, List<MergedChange> changes) {}

    private record Context(Snapshot snapshot, DashboardPeriod period, PeriodBuckets buckets, Instant now,
                           LocalDate today, RemediationRules rules, Map<String, BigDecimal> allocatedByProject) {}
}
