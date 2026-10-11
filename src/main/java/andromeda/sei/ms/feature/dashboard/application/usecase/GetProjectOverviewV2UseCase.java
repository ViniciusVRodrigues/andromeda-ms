package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator.AllocatedUsage;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Adoption;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ChangeFailureRate;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.CostBucket;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.DataQuality;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.DeployFrequency;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Dora;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.FlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.LeadTime;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.People;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Period;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.PeriodStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.PrFlowRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ProjectInfo;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.QualityRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.RepoRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.SprintRow;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.Subscription;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.ProjectOverviewV2Output.ThroughputRow;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PeopleCoverage;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeriod;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.DayRange;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets;
import andromeda.sei.ms.feature.dashboard.application.v2.PeriodBuckets.Bucket;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems.SprintFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.PerformanceClass;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Project overview v2 (dashboards-customizaveis D1, {@code buildProject}) of one project and period.
 *
 * <p>Everything is read once per request and sliced in memory — the work items of the project, its
 * sprints, the pull requests and commits of its repositories over both periods, the AI usage
 * allocated to it and the members' usage of the period — so a 365-day request costs the same
 * number of queries as a 30-day one. Each figure follows the rule that already exists elsewhere:
 * velocity by {@link EffortBearingLevelResolver#summarize}, durations by {@link Percentiles}, time to
 * first review by {@link ReviewMetricsPolicy}, stalled pull requests by
 * {@link StalePullRequestPolicy}, delivery metrics by {@link DeliveryMetricsService}, allocated AI
 * cost by {@link ProjectAiCostCalculator#allocatedUsage}.
 */
@Service
public class GetProjectOverviewV2UseCase {

    private static final int MONEY_SCALE = 6;
    private static final int SUBSCRIPTION_SCALE = 2;
    private static final BigDecimal DAYS_PER_MONTH = BigDecimal.valueOf(30);
    private static final Set<WorkItem.ResolvedType> THROUGHPUT_TYPES =
            Set.of(WorkItem.ResolvedType.STORY, WorkItem.ResolvedType.BUG, WorkItem.ResolvedType.TASK);

    private final ProjectRepository projectRepository;
    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final ProjectVcsActivity vcsActivity;
    private final VcsReadPort vcsReadPort;
    private final EffortBearingLevelResolver effortResolver;
    private final ProjectAiCostCalculator aiCostCalculator;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final DeliveryMetricsService deliveryMetricsService;
    private final DashboardPeopleDirectory peopleDirectory;
    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    @Autowired
    public GetProjectOverviewV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                                       SprintRepository sprintRepository, ProjectVcsActivity vcsActivity,
                                       VcsReadPort vcsReadPort, EffortBearingLevelResolver effortResolver,
                                       ProjectAiCostCalculator aiCostCalculator, AIUsageRepository aiUsageRepository,
                                       AiUsageAllocationRepository allocationRepository,
                                       DeliveryMetricsService deliveryMetricsService,
                                       DashboardPeopleDirectory peopleDirectory,
                                       OrganizationRepository organizationRepository) {
        this(projectRepository, workItemRepository, sprintRepository, vcsActivity, vcsReadPort, effortResolver,
                aiCostCalculator, aiUsageRepository, allocationRepository, deliveryMetricsService, peopleDirectory,
                organizationRepository, Clock.systemUTC());
    }

    GetProjectOverviewV2UseCase(ProjectRepository projectRepository, WorkItemRepository workItemRepository,
                                SprintRepository sprintRepository, ProjectVcsActivity vcsActivity,
                                VcsReadPort vcsReadPort, EffortBearingLevelResolver effortResolver,
                                ProjectAiCostCalculator aiCostCalculator, AIUsageRepository aiUsageRepository,
                                AiUsageAllocationRepository allocationRepository,
                                DeliveryMetricsService deliveryMetricsService, DashboardPeopleDirectory peopleDirectory,
                                OrganizationRepository organizationRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.workItemRepository = workItemRepository;
        this.sprintRepository = sprintRepository;
        this.vcsActivity = vcsActivity;
        this.vcsReadPort = vcsReadPort;
        this.effortResolver = effortResolver;
        this.aiCostCalculator = aiCostCalculator;
        this.aiUsageRepository = aiUsageRepository;
        this.allocationRepository = allocationRepository;
        this.deliveryMetricsService = deliveryMetricsService;
        this.peopleDirectory = peopleDirectory;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    /** @param periodDays one of 30, 90, 180, 365 ({@code DashboardPeriodInvalidException} otherwise) */
    public ProjectOverviewV2Output execute(String orgId, String projectId, int periodDays) {
        Objects.requireNonNull(orgId, "orgId");
        Project project = projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        DashboardPeriod period = DashboardPeriod.of(periodDays, SprintWindow.toDay(now));
        Context ctx = load(orgId, project, period, now);
        PeriodBuckets buckets = period.buckets();
        return new ProjectOverviewV2Output(projectInfo(ctx), periodInfo(period, buckets),
                stats(ctx, period.current(), ctx.currentDelivery(), true),
                stats(ctx, period.previous(), ctx.previousDelivery(), false),
                dora(ctx.currentDelivery()), throughput(ctx, buckets), sprintRows(ctx), flow(ctx, buckets),
                quality(ctx, buckets), prFlow(ctx, buckets), repos(ctx), ai(ctx, buckets), people(ctx),
                dataQuality(ctx), now);
    }

    private Context load(String orgId, Project project, DashboardPeriod period, Instant now) {
        ProjectPeriodItems items = ProjectPeriodItems.of(
                workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId()),
                sprintRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId()), effortResolver);
        DayRange current = period.current();
        ProjectVcsActivity.Snapshot vcs = vcsActivity.loadWithoutReviews(orgId, project.getId(),
                period.previous().from(), current.to());
        LocalDate aiFrom = items.sprintsEndingIn(current).stream().map(f -> f.window().startDay())
                .filter(day -> day.isBefore(period.previous().first())).min(Comparator.naturalOrder())
                .orElse(period.previous().first());
        List<AllocatedUsage> allocated = aiCostCalculator.allocatedUsage(orgId, items.ids(), aiFrom, current.last());
        List<AIUsage> memberUsage = aiUsageRepository.findByOrganizationIdAndUserIdInAndDayBetween(orgId,
                project.getMemberIds(), current.first(), current.last());
        return new Context(orgId, project, period, now, items, vcs, allocated, memberUsage,
                delivery(orgId, vcs, items, current), delivery(orgId, vcs, items, period.previous()),
                organizationRepository.findById(orgId).orElse(null));
    }

    private DeliveryMetrics delivery(String orgId, ProjectVcsActivity.Snapshot vcs, ProjectPeriodItems items,
                                     DayRange range) {
        List<VcsPullRequest> merged = filter(vcs.pullRequests(), pr -> range.contains(pr.getMergedAt()));
        List<WorkItem> completed = filter(items.all(), i -> range.contains(i.getCompletedAt()));
        return deliveryMetricsService.calculate(orgId,
                deliveryMetricsService.mergedChangesFrom(orgId, vcs.repositories(), merged),
                DeliveryMetricsService.completedItems(completed), range.from(), range.to());
    }

    private static ProjectInfo projectInfo(Context ctx) {
        String team = ctx.items().latestSprint().map(s -> s.getTeamName()).orElse(null);
        return new ProjectInfo(ctx.project().getId(), ctx.project().getName(), team,
                ctx.project().getMemberIds().size(), ctx.vcs().repositories().stream()
                .map(GetProjectOverviewV2UseCase::repoName).sorted(String.CASE_INSENSITIVE_ORDER).toList());
    }

    private static Period periodInfo(DashboardPeriod period, PeriodBuckets buckets) {
        return new Period(period.days(), period.current().first().toString(), period.current().last().toString(),
                period.previous().first().toString(), period.previous().last().toString(), buckets.granularity());
    }

    private static PeriodStats stats(Context ctx, DayRange range, DeliveryMetrics delivery, boolean current) {
        List<WorkItem> done = doneOfThroughputTypes(ctx, range);
        List<WorkItem> units = ctx.items().doneUnitsIn(range);
        Double sp = ctx.items().effort().scope().totalEffort(units);
        BigDecimal cost = allocatedCost(ctx.allocated(), range);
        List<VcsPullRequest> merged = filter(ctx.vcs().pullRequests(), pr -> range.contains(pr.getMergedAt()));
        return new PeriodStats(done.size(), delivery.deploymentsPerWeek(), sp, predictability(ctx, range),
                Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList()),
                delivery.changeFailRatePercent(), bugShare(done), cost, perSp(cost, sp), merged.size(),
                Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()),
                timeToFirstReview(ctx, range), current ? staleCount(ctx) : null,
                (int) ctx.vcs().commits().stream().filter(c -> range.contains(c.getAuthoredAt())).count());
    }

    private static Double predictability(Context ctx, DayRange range) {
        return DashboardV2Views.averageOrNull(ctx.items().sprintsEndingIn(range).stream()
                .map(SprintFigures::predictabilityPct).toList());
    }

    private static List<WorkItem> doneOfThroughputTypes(Context ctx, DayRange range) {
        return filter(ctx.items().all(), i -> i.getResolvedStatus() == WorkItem.ResolvedStatus.DONE
                && THROUGHPUT_TYPES.contains(i.getResolvedType()) && range.contains(i.getCompletedAt()));
    }

    private static Double bugShare(List<WorkItem> done) {
        return Ratios.percent(done.stream().filter(i -> i.getResolvedType() == WorkItem.ResolvedType.BUG).count(),
                done.size());
    }

    private static DurationStat timeToFirstReview(Context ctx, DayRange range) {
        return ReviewMetricsPolicy.aggregate(filter(ctx.vcs().pullRequests(), pr -> range.contains(pr.getCreatedAt())),
                ctx.now()).timeToFirstReview();
    }

    private static int staleCount(Context ctx) {
        return (int) ctx.vcs().pullRequests().stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN && StalePullRequestPolicy.isStale(pr, ctx.now()))
                .count();
    }

    private static Dora dora(DeliveryMetrics delivery) {
        return new Dora(delivery.method().name(),
                new LeadTime(delivery.changeLeadTime(), grade(delivery.changeLeadTimeClass())),
                new DeployFrequency(delivery.deploymentsPerWeek(), grade(delivery.deploymentFrequencyClass()), true),
                new ChangeFailureRate(delivery.changeFailRatePercent(), grade(delivery.changeFailRateClass()),
                        delivery.method() != DeliveryMethod.VCS_PROXY));
    }

    private static String grade(PerformanceClass performanceClass) {
        return performanceClass == null ? null : performanceClass.name();
    }

    private static List<ThroughputRow> throughput(Context ctx, PeriodBuckets buckets) {
        return buckets.buckets().stream().map(b -> {
            List<WorkItem> done = doneOfThroughputTypes(ctx, b.range());
            return new ThroughputRow(b.start(), b.label(), countType(done, WorkItem.ResolvedType.STORY),
                    countType(done, WorkItem.ResolvedType.BUG), countType(done, WorkItem.ResolvedType.TASK));
        }).toList();
    }

    private static int countType(List<WorkItem> items, WorkItem.ResolvedType type) {
        return (int) items.stream().filter(i -> i.getResolvedType() == type).count();
    }

    private static List<SprintRow> sprintRows(Context ctx) {
        return ctx.items().sprintsEndingIn(ctx.period().current()).stream().map(f -> {
            BigDecimal cost = allocatedCost(ctx.allocated(), new DayRange(f.window().startDay(), f.window().endDay()));
            return new SprintRow(f.sprint().getId(), DashboardV2Views.historyLabel(f.sprint()), f.sprint().getName(),
                    f.planned(), f.delivered(), f.predictabilityPct(), cost, perSp(cost, f.delivered()));
        }).toList();
    }

    private static List<FlowRow> flow(Context ctx, PeriodBuckets buckets) {
        return buckets.buckets().stream().map(b -> {
            List<WorkItem> units = ctx.items().doneUnitsIn(b.range());
            return new FlowRow(b.start(), b.label(), Percentiles.of(units.stream().map(WorkItem::getLeadTimeHours).toList()),
                    Percentiles.of(units.stream().map(WorkItem::getCycleTimeHours).toList()));
        }).toList();
    }

    private static List<QualityRow> quality(Context ctx, PeriodBuckets buckets) {
        return buckets.buckets().stream()
                .map(b -> new QualityRow(b.start(), b.label(), bugShare(doneOfThroughputTypes(ctx, b.range()))))
                .toList();
    }

    private static List<PrFlowRow> prFlow(Context ctx, PeriodBuckets buckets) {
        return buckets.buckets().stream().map(b -> new PrFlowRow(b.start(), b.label(),
                (int) ctx.vcs().pullRequests().stream().filter(pr -> b.range().contains(pr.getCreatedAt())).count(),
                (int) ctx.vcs().pullRequests().stream().filter(pr -> b.range().contains(pr.getMergedAt())).count(),
                timeToFirstReview(ctx, b.range()))).toList();
    }

    private List<RepoRow> repos(Context ctx) {
        Map<String, VcsRepoBinding> bindings = vcsReadPort.bindingsOfProject(ctx.orgId(), ctx.project().getId()).stream()
                .filter(b -> b.getRepositoryId() != null)
                .collect(Collectors.toMap(VcsRepoBinding::getRepositoryId, Function.identity(), (a, b) -> a));
        return ctx.vcs().repositories().stream().filter(r -> !r.isPendingFirstSync())
                .sorted(Comparator.comparing(GetProjectOverviewV2UseCase::repoName, String.CASE_INSENSITIVE_ORDER))
                .map(repo -> repoRow(ctx, repo, bindings.get(repo.getId())))
                .toList();
    }

    private RepoRow repoRow(Context ctx, VcsRepository repo, VcsRepoBinding binding) {
        DayRange range = ctx.period().current();
        List<VcsPullRequest> prs = filter(ctx.vcs().pullRequests(), pr -> repo.getId().equals(pr.getRepositoryId()));
        List<VcsPullRequest> merged = filter(prs, pr -> range.contains(pr.getMergedAt()));
        List<VcsPullRequest> open = filter(prs, pr -> pr.getStatus() == PullRequestStatus.OPEN);
        DeliveryMetrics delivery = deliveryMetricsService.calculate(ctx.orgId(),
                deliveryMetricsService.mergedChangesFrom(ctx.orgId(), List.of(repo), merged), List.of(),
                range.from(), range.to());
        return new RepoRow(repo.getId(), repoName(repo), repo.getProvider() != null ? repo.getProvider().name() : null,
                (int) ctx.vcs().commits().stream()
                        .filter(c -> repo.getId().equals(c.getRepositoryId()) && range.contains(c.getAuthoredAt())).count(),
                merged.size(), Percentiles.of(merged.stream().map(VcsPullRequest::getCycleTimeHours).toList()),
                ReviewMetricsPolicy.aggregate(filter(prs, pr -> range.contains(pr.getCreatedAt())), ctx.now())
                        .timeToFirstReview(),
                open.size(), (int) open.stream().filter(pr -> StalePullRequestPolicy.isStale(pr, ctx.now())).count(),
                delivery.changeFailRatePercent(), binding != null ? binding.isSyncEnabled() : null,
                binding != null ? binding.getLastSyncAt() : null);
    }

    private static String repoName(VcsRepository repo) {
        return repo.getDisplayName() != null ? repo.getDisplayName() : repo.getId();
    }

    private static Ai ai(Context ctx, PeriodBuckets buckets) {
        DayRange current = ctx.period().current();
        List<AllocatedUsage> inPeriod = filter(ctx.allocated(), a -> current.containsDay(a.usage().getDay()));
        List<String> models = inPeriod.stream().filter(a -> a.usage().getModel() != null)
                .collect(Collectors.groupingBy(a -> a.usage().getModel(),
                        Collectors.reducing(BigDecimal.ZERO, AllocatedUsage::allocatedCostUsd, BigDecimal::add)))
                .entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(Map.Entry::getKey).toList();
        List<CostBucket> costByBucket = buckets.buckets().stream().map(b -> costBucket(inPeriod, models, b)).toList();
        Adoption adoption = new Adoption((int) ctx.memberUsage().stream().map(AIUsage::getUserId)
                .filter(Objects::nonNull).distinct().count(), ctx.project().getMemberIds().size());
        Double tokensPerSp = DashboardV2Views.averageOrNull(ctx.items().sprintsEndingIn(current).stream()
                .flatMap(f -> f.summary().units().stream()).map(WorkItem::getTokensPerSp).toList());
        return new Ai(models, costByBucket, adoption, subscription(ctx), tokensPerSp);
    }

    private static CostBucket costBucket(List<AllocatedUsage> allocated, List<String> models, Bucket bucket) {
        Map<String, BigDecimal> byModel = new LinkedHashMap<>();
        models.forEach(model -> byModel.put(model, allocatedCost(filter(allocated,
                a -> model.equals(a.usage().getModel())), bucket.range())));
        return new CostBucket(bucket.start(), bucket.label(), byModel);
    }

    private static Subscription subscription(Context ctx) {
        BigDecimal consumption = ctx.memberUsage().stream().map(AIUsage::getCostUsd).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal perUser = ctx.organization() != null ? ctx.organization().getSubscriptionCostPerUserUsd() : null;
        BigDecimal seats = perUser == null ? null : perUser
                .multiply(BigDecimal.valueOf(ctx.project().getMemberIds().size()))
                .multiply(BigDecimal.valueOf(ctx.period().days()))
                .divide(DAYS_PER_MONTH, SUBSCRIPTION_SCALE, RoundingMode.HALF_UP);
        return new Subscription(consumption, seats, perUser);
    }

    private People people(Context ctx) {
        PeopleCoverage coverage = peopleDirectory.coverage(ctx.orgId(), ctx.project().getMemberIds());
        return new People(coverage.members(), coverage.azureLinked(), coverage.githubLinked(), coverage.aiConnected());
    }

    private DataQuality dataQuality(Context ctx) {
        DayRange current = ctx.period().current();
        List<SprintFigures> sprints = ctx.items().sprintsEndingIn(current);
        long units = sprints.stream().mapToLong(f -> f.summary().units().size()).sum();
        long estimated = sprints.stream().mapToLong(f -> f.summary().units().stream()
                .filter(u -> f.summary().scope().effectiveEffort(u) != null).count()).sum();
        List<VcsPullRequest> created = filter(ctx.vcs().pullRequests(), pr -> current.contains(pr.getCreatedAt()));
        long linked = created.stream().filter(pr -> pr.getLinkedWorkItemExternalId() != null).count();
        return new DataQuality(Ratios.percent(estimated, units), Ratios.percent(linked, created.size()),
                aiDaysAllocated(ctx, current));
    }

    private Double aiDaysAllocated(Context ctx, DayRange current) {
        Set<String> members = Set.copyOf(ctx.project().getMemberIds());
        Set<String> validated = allocationRepository
                .findValidatedByOrganizationIdAndDayBetween(ctx.orgId(), current.first(), current.last()).stream()
                .filter(a -> a.getUserId() != null && a.getDay() != null && members.contains(a.getUserId()))
                .map(a -> DashboardV2Views.personDay(a.getUserId(), a.getDay()))
                .collect(Collectors.toSet());
        AllocationSummary summary = DashboardV2Views.allocationSummary(ctx.memberUsage(), validated);
        long total = (long) summary.validatedDays() + summary.pendingDays() + summary.unallocatedDays();
        return Ratios.percent((long) summary.validatedDays() + summary.pendingDays(), total);
    }

    private static BigDecimal allocatedCost(List<AllocatedUsage> allocated, DayRange range) {
        return allocated.stream().filter(a -> range.containsDay(a.usage().getDay()))
                .map(AllocatedUsage::allocatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal perSp(BigDecimal cost, Double delivered) {
        if (cost == null || delivered == null || delivered <= 0) return null;
        return cost.divide(BigDecimal.valueOf(delivered), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static <T> List<T> filter(Collection<T> values, Predicate<T> predicate) {
        return values.stream().filter(predicate).toList();
    }

    private record Context(String orgId, Project project, DashboardPeriod period, Instant now,
                           ProjectPeriodItems items, ProjectVcsActivity.Snapshot vcs, List<AllocatedUsage> allocated,
                           List<AIUsage> memberUsage, DeliveryMetrics currentDelivery,
                           DeliveryMetrics previousDelivery, Organization organization) {}
}
