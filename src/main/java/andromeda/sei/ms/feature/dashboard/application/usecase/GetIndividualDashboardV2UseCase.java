package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummaryByModel;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.BurndownCalculator;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.HistoryPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.PendingAllocation;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.PendingSplit;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.UnallocatedDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.Person;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.ReviewsGiven;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.IndividualDashboardV2Output.SprintStats;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver.SprintSelection;
import andromeda.sei.ms.feature.dashboard.application.v2.ComplexityEffortFigures;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Individual dashboard v2 (dashboards-customizaveis D1, {@code buildIndividual}) of the caller in
 * one project and sprint. The person is always {@code userId} from the JWT.
 *
 * <p>Reuse, not re-derivation: the selected sprint's KPIs, items, cost per item, burndown and AI
 * series come from {@link GetDeveloperDashboardUseCase}; the previous sprint and the history use the
 * same single velocity definition ({@link EffortBearingLevelResolver#summarize}), percentile rule
 * and review rule ({@link ReviewMetricsPolicy}). Version-control figures count only repositories
 * bound to the project.
 */
@Service
public class GetIndividualDashboardV2UseCase {

    private static final double PERCENT = 100.0;
    private static final Map<WorkItem.ResolvedStatus, Integer> ITEM_ORDER = Map.of(
            WorkItem.ResolvedStatus.IN_PROGRESS, 0, WorkItem.ResolvedStatus.TODO, 1, WorkItem.ResolvedStatus.DONE, 2);

    private final ProjectRepository projectRepository;
    private final DashboardPeopleDirectory peopleDirectory;
    private final GetDeveloperDashboardUseCase developerDashboard;
    private final DashboardSprintResolver sprintResolver;
    private final ProjectVcsActivity vcsActivity;
    private final WorkItemRepository workItemRepository;
    private final EffortBearingLevelResolver effortResolver;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final Clock clock;

    @Autowired
    public GetIndividualDashboardV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                                           GetDeveloperDashboardUseCase developerDashboard,
                                           DashboardSprintResolver sprintResolver, ProjectVcsActivity vcsActivity,
                                           WorkItemRepository workItemRepository,
                                           EffortBearingLevelResolver effortResolver,
                                           AIUsageRepository aiUsageRepository,
                                           AiUsageAllocationRepository allocationRepository) {
        this(projectRepository, peopleDirectory, developerDashboard, sprintResolver,
                vcsActivity, workItemRepository, effortResolver, aiUsageRepository, allocationRepository,
                Clock.systemUTC());
    }

    GetIndividualDashboardV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                                    GetDeveloperDashboardUseCase developerDashboard,
                                    DashboardSprintResolver sprintResolver, ProjectVcsActivity vcsActivity,
                                    WorkItemRepository workItemRepository, EffortBearingLevelResolver effortResolver,
                                    AIUsageRepository aiUsageRepository,
                                    AiUsageAllocationRepository allocationRepository, Clock clock) {
        this.projectRepository = projectRepository;
        this.peopleDirectory = peopleDirectory;
        this.developerDashboard = developerDashboard;
        this.sprintResolver = sprintResolver;
        this.vcsActivity = vcsActivity;
        this.workItemRepository = workItemRepository;
        this.effortResolver = effortResolver;
        this.aiUsageRepository = aiUsageRepository;
        this.allocationRepository = allocationRepository;
        this.clock = clock;
    }

    public IndividualDashboardV2Output execute(String orgId, String userId, String projectId, String sprintId) {
        Objects.requireNonNull(orgId, "orgId");
        Objects.requireNonNull(userId, "userId");
        projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        Person person = person(orgId, userId);
        SprintSelection selection = sprintResolver.resolve(orgId, projectId, sprintId);
        if (selection.isEmpty()) return IndividualDashboardV2Output.withoutSprint(person, now);

        Context ctx = context(orgId, userId, projectId, selection, now);
        DeveloperDashboardOutput dev = developerDashboard.execute(orgId, userId, projectId,
                selection.selected().getId(), null, null, null);
        return new IndividualDashboardV2Output(
                DashboardV2Views.sprintMeta(selection.selected(), ctx.window(), ctx.today()), person,
                kpis(dev, ctx), selection.previous() == null ? null : stats(ctx, selection.previous()),
                burndown(dev, ctx), items(dev, ctx), pullRequests(dev, ctx), reviewsGiven(ctx), commitsByDay(ctx),
                ai(dev, ctx), history(ctx, selection), now);
    }

    private Context context(String orgId, String userId, String projectId, SprintSelection selection, Instant now) {
        SprintWindow window = SprintWindow.of(selection.selected());
        ProjectVcsActivity.Snapshot vcs = vcsActivity.load(orgId, projectId, window.from(), window.to());
        List<VcsPullRequest> myPrs = vcs.pullRequests().stream().filter(pr -> userId.equals(pr.getAuthorUserId())).toList();
        return new Context(orgId, userId, projectId, selection.selected(), window, now, SprintWindow.toDay(now), vcs,
                myPrs, peopleDirectory.people(orgId));
    }

    private Person person(String orgId, String userId) {
        PersonInfo info = peopleDirectory.person(orgId, userId);
        return new Person(userId, info.name(), info.githubLinked(), info.vcsLinked(),
                peopleDirectory.aiConnected(orgId, userId));
    }

    /** The developer dashboard's numbers for the selected sprint, plus this project's pull requests. */
    private Kpis kpis(DeveloperDashboardOutput dev, Context ctx) {
        boolean hasUsage = dev.aiSprintSummary() != null;
        PrStats prs = prStats(ctx, ctx.window());
        List<WorkItem> units = activeUnits(dev);
        ComplexityEffortFigures f = dev.figures();
        return new Kpis(dev.totalSpInSprint(),
                DashboardV2Views.deliveredOf(dev.totalSpInSprint(), dev.storyPointsDelivered()),
                (int) units.stream().filter(wi -> wi.getResolvedStatus() == WorkItem.ResolvedStatus.DONE).count(),
                units.size(), dev.cycleTime(), dev.leadTime(), prs.merged(), prs.review(),
                hasUsage ? dev.sprintCostUsd() : null, hasUsage ? dev.aiSprintSummary().totalTokens() : null,
                dev.avgTokensPerSp(),
                DashboardV2Views.isCurrent(ctx.sprint())
                        ? (int) ctx.myPrs().stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN).count()
                        : null,
                f.complexityPlanned(), f.complexityDelivered(), f.effortLogged(), f.avgTokensPerPoint(),
                f.avgTokensPerHour(), f.unitsWithoutComplexity());
    }

    /** Delivery of {@code sprint} for the person, with the rules the developer dashboard uses. */
    private SprintStats stats(Context ctx, Sprint sprint) {
        SprintWindow window = SprintWindow.of(sprint);
        List<WorkItem> items = workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
                ctx.orgId(), ctx.userId(), sprint.getId(), ctx.projectId()).stream().filter(WorkItem::isActive).toList();
        EffortBearingLevelResolver.EffortSummary summary = effortResolver.summarize(items);
        ComplexityEffortFigures f = ComplexityEffortFigures.of(summary);
        List<AIUsage> usage = aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(
                ctx.orgId(), ctx.userId(), window.startDay(), window.endDay());
        PrStats prs = prStats(ctx, window);
        return new SprintStats(summary.planned(), DashboardV2Views.deliveredOf(summary.planned(), summary.delivered()),
                summary.doneUnits().size(),
                summary.units().size(), Percentiles.of(cycleTimes(summary.doneUnits())),
                Percentiles.of(summary.doneUnits().stream().map(WorkItem::getLeadTimeHours).toList()), prs.merged(),
                prs.review(), usage.isEmpty() ? null : DashboardV2Views.sumOrNull(usage.stream()
                        .map(AIUsage::getCostUsd).toList()),
                usage.isEmpty() ? null : usage.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum(),
                DashboardV2Views.averageOrNull(summary.units().stream().map(WorkItem::getTokensPerSp).toList()),
                f.complexityPlanned(), f.complexityDelivered(), f.effortLogged(), f.avgTokensPerPoint(),
                f.avgTokensPerHour(), f.unitsWithoutComplexity());
    }

    /** Merged in the window, and the review rule over the person's pull requests created in it. */
    private PrStats prStats(Context ctx, SprintWindow window) {
        int merged = (int) ctx.myPrs().stream().filter(pr -> window.contains(pr.getMergedAt())).count();
        List<VcsPullRequest> created = ctx.myPrs().stream().filter(pr -> window.contains(pr.getCreatedAt())).toList();
        return new PrStats(merged, ReviewMetricsPolicy.aggregate(created, ctx.now()).timeToFirstReview());
    }

    private List<HistoryPoint> history(Context ctx, SprintSelection selection) {
        return selection.history().stream().map(sprint -> {
            SprintStats stats = stats(ctx, sprint);
            return new HistoryPoint(sprint.getId(), DashboardV2Views.historyLabel(sprint), sprint.getName(),
                    sprint.getId().equals(selection.selected().getId()), DashboardV2Views.isCurrent(sprint),
                    stats.spPlanned(), stats.spDelivered(), stats.cycleTimeHours(), stats.aiCostUsd(),
                    stats.aiTokens(), stats.complexityPlanned(), stats.complexityDelivered(), stats.effortLogged());
        }).toList();
    }

    /**
     * The developer dashboard's burndown (up to today) completed to the sprint end with the ideal
     * line only; {@code remaining} is {@code null} when no unit carries effort.
     */
    private List<BurndownDay> burndown(DeveloperDashboardOutput dev, Context ctx) {
        // The series is in complexity (complexity-effort D1): a sprint total of zero means no unit has it.
        boolean noEffort = dev.burndownSeries().isEmpty() || dev.burndownSeries().getFirst().idealSp() <= 0d;
        List<BurndownDay> days = new ArrayList<>(dev.burndownSeries().stream()
                .map(p -> burndownDay(p, noEffort)).toList());
        if (dev.burndownSeries().isEmpty()) return days;
        LocalDate last = dev.burndownSeries().getLast().date();
        double total = noEffort ? 0d : dev.burndownSeries().getFirst().idealSp();
        for (LocalDate day : DashboardV2Views.days(last.plusDays(1), ctx.window().endDay())) {
            double ideal = BurndownCalculator.ideal(total, ctx.window().startDay(), ctx.window().endDay(), day);
            days.add(new BurndownDay(day.toString(), DashboardV2Views.label(day), null, ideal, null, ideal));
        }
        return days;
    }

    private static BurndownDay burndownDay(BurndownPoint point, boolean noEffort) {
        return new BurndownDay(point.date().toString(), DashboardV2Views.label(point.date()),
                noEffort ? null : point.remainingSp(), point.idealSp(),
                noEffort ? null : point.remainingPoints(), point.idealPoints());
    }

    /** In progress first, then to do, then done; larger effort first. */
    private List<ItemView> items(DeveloperDashboardOutput dev, Context ctx) {
        Map<String, VcsPullRequest> prByItem = DashboardV2Views.latestPullRequestByWorkItem(ctx.vcs().pullRequests());
        Map<String, String> repoNames = ctx.vcs().repositoryNames();
        return effortResolver.listedUnits(activeUnits(dev)).stream()
                .sorted(Comparator.comparing((WorkItem wi) -> ITEM_ORDER.getOrDefault(wi.getResolvedStatus(), ITEM_ORDER.size()))
                        .thenComparing(WorkItem::getEffort, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(wi -> DashboardV2Views.itemView(wi, ctx.people(), dev.aiCostByWorkItemId().get(wi.getId()),
                        prByItem.get(wi.getExternalId()), repoNames, ctx.now(), dev.effortScope()))
                .toList();
    }

    /**
     * The person's pull requests touching the sprint: created or closed in it, or still open when
     * the sprint is the current one. Open first, then newest.
     */
    private List<PullRequestView> pullRequests(DeveloperDashboardOutput dev, Context ctx) {
        boolean current = DashboardV2Views.isCurrent(ctx.sprint());
        Map<String, String> repoNames = ctx.vcs().repositoryNames();
        Map<String, String> titles = DashboardV2Views.titlesByExternalId(activeUnits(dev));
        return ctx.myPrs().stream()
                .filter(pr -> ctx.window().contains(pr.getCreatedAt()) || ctx.window().contains(pr.getMergedAt())
                        || ctx.window().contains(pr.getClosedAt())
                        || (current && pr.getStatus() == PullRequestStatus.OPEN))
                .sorted(Comparator.comparing((VcsPullRequest pr) -> pr.getStatus() == PullRequestStatus.OPEN ? 0 : 1)
                        .thenComparing(VcsPullRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(pr -> DashboardV2Views.pullRequestView(pr, ctx.people(), repoNames, titles, ctx.now()))
                .toList();
    }

    private ReviewsGiven reviewsGiven(Context ctx) {
        List<ReviewKind> kinds = ctx.vcs().reviews().stream()
                .filter(e -> ctx.userId().equals(e.getActorUserId()))
                .map(VcsReviewEvent::getKind).filter(Objects::nonNull).toList();
        return new ReviewsGiven(kinds.size(), (int) kinds.stream().filter(ReviewKind::isApproval).count(),
                count(kinds, ReviewKind.CHANGES_REQUESTED), count(kinds, ReviewKind.COMMENTED),
                count(kinds, ReviewKind.REJECTED));
    }

    private static int count(List<ReviewKind> kinds, ReviewKind kind) {
        return (int) kinds.stream().filter(kind::equals).count();
    }

    private List<CommitDay> commitsByDay(Context ctx) {
        return DashboardV2Views.commitsByDay(ctx.vcs().commits(), c -> ctx.userId().equals(c.getAuthorUserId()),
                ctx.window().startDay(), ctx.window().lastElapsedDay(ctx.today()));
    }

    private Ai ai(DeveloperDashboardOutput dev, Context ctx) {
        List<AIUsage> usage = aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(
                ctx.orgId(), ctx.userId(), ctx.window().startDay(), ctx.window().endDay());
        Set<String> validated = allocationsInWindow(ctx, AiUsageAllocation.Status.VALIDATED).stream()
                .map(a -> DashboardV2Views.personDay(ctx.userId(), a.getDay())).collect(Collectors.toSet());
        List<String> editors = usage.stream().map(AIUsage::getEditor).filter(Objects::nonNull).distinct().toList();
        List<PendingAllocation> pendingList = pendingAllocations(dev, ctx, usage, validated);
        Set<String> pendingDays = pendingList.stream().map(p -> DashboardV2Views.personDay(ctx.userId(),
                LocalDate.parse(p.day()))).collect(Collectors.toSet());
        List<UnallocatedDay> unallocatedList = unallocatedDays(ctx, usage, validated, pendingDays);
        // The summary counts what the lists show, so the ui never sees a count and a list disagree.
        AllocationSummary summary = new AllocationSummary(
                DashboardV2Views.allocationSummary(usage, validated).validatedDays(),
                pendingList.size(), unallocatedList.size());
        return new Ai(aiByDay(dev, ctx), aiByModel(ctx), summary, pendingList, unallocatedList, editors);
    }

    private static List<AiDay> aiByDay(DeveloperDashboardOutput dev, Context ctx) {
        return dev.tokensByDay().stream()
                .filter(d -> d.day() != null && ctx.window().containsDay(d.day()) && !d.day().isAfter(ctx.today()))
                .map(GetIndividualDashboardV2UseCase::aiDay)
                .toList();
    }

    private static AiDay aiDay(DailyAiUsage d) {
        return new AiDay(d.day().toString(), DashboardV2Views.label(d.day()), d.inputTokens(), d.outputTokens(),
                d.interactions(), d.costUsd());
    }

    private List<AiModel> aiByModel(Context ctx) {
        return aiUsageRepository.findSummaryGroupedByModel(ctx.orgId(), ctx.userId(), ctx.window().startDay(),
                        ctx.window().endDay(), null).stream()
                .map(GetIndividualDashboardV2UseCase::aiModel)
                .sorted(Comparator.comparing(AiModel::costUsd, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static AiModel aiModel(AiUsageSummaryByModel m) {
        return new AiModel(m.model(), m.totalInputTokens() + m.totalOutputTokens(), m.totalCostUsd(), m.totalInteractions());
    }

    /** Days of the sprint whose automatic split awaits the person's validation, newest first. */
    private List<PendingAllocation> pendingAllocations(DeveloperDashboardOutput dev, Context ctx, List<AIUsage> usage,
                                                       Set<String> validatedPersonDays) {
        Map<String, AIUsage> usageById = usage.stream().filter(u -> u.getId() != null)
                .collect(Collectors.toMap(AIUsage::getId, Function.identity(), (a, b) -> a));
        List<AiUsageAllocation> pending = allocationsInWindow(ctx, AiUsageAllocation.Status.PENDING_VALIDATION).stream()
                .filter(a -> !validatedPersonDays.contains(DashboardV2Views.personDay(ctx.userId(), a.getDay())))
                .sorted(Comparator.comparing(AiUsageAllocation::getDay).reversed())
                .toList();
        Map<String, String> externalIdByItem = activeUnits(dev).stream().filter(wi -> wi.getExternalId() != null)
                .collect(Collectors.toMap(WorkItem::getId, WorkItem::getExternalId, (a, b) -> a));
        // A suggestion from Azure activity can point at an item outside this sprint's own list.
        pending.stream().flatMap(a -> a.getSplits() == null ? java.util.stream.Stream.<AllocationSplit>empty()
                        : a.getSplits().stream())
                .map(AllocationSplit::getWorkItemId).filter(id -> id != null && !externalIdByItem.containsKey(id))
                .distinct()
                .forEach(id -> workItemRepository.findById(id)
                        .filter(wi -> ctx.orgId().equals(wi.getOrganizationId()) && wi.getExternalId() != null)
                        .ifPresent(wi -> externalIdByItem.put(id, wi.getExternalId())));
        return pending.stream().map(a -> pendingAllocation(a, usageById, externalIdByItem)).toList();
    }

    private static PendingAllocation pendingAllocation(AiUsageAllocation allocation, Map<String, AIUsage> usageById,
                                                       Map<String, String> externalIdByItem) {
        List<AIUsage> records = allocation.getAiUsageIds() == null ? List.of()
                : allocation.getAiUsageIds().stream().map(usageById::get).filter(Objects::nonNull).toList();
        List<PendingSplit> splits = allocation.getSplits() == null ? List.of() : allocation.getSplits().stream()
                .map(s -> pendingSplit(s, externalIdByItem)).toList();
        BigDecimal cost = records.stream().map(AIUsage::getCostUsd).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String day = allocation.getDay().toString();
        return new PendingAllocation(allocation.getId(), day, day, DashboardV2Views.label(allocation.getDay()),
                records.stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum(),
                cost, cost, allocation.suggestionSourceOrDefault().name(), splits);
    }

    private static PendingSplit pendingSplit(AllocationSplit split, Map<String, String> externalIdByItem) {
        Double fraction = split.getPercentage() == null ? null : split.getPercentage().doubleValue();
        return new PendingSplit(split.getWorkItemId(), externalIdByItem.get(split.getWorkItemId()),
                split.getWorkItemTitle(), fraction == null ? null : fraction * PERCENT, fraction);
    }

    /**
     * Days of the window with usage not yet claimed by an allocation, that have neither a validated
     * allocation nor a pending suggestion, newest first.
     */
    private static List<UnallocatedDay> unallocatedDays(Context ctx, List<AIUsage> usage, Set<String> validatedPersonDays,
                                                        Set<String> pendingPersonDays) {
        Map<LocalDate, List<AIUsage>> unclaimedByDay = usage.stream()
                .filter(u -> u.getDay() != null && u.getAllocationId() == null)
                .filter(u -> !validatedPersonDays.contains(DashboardV2Views.personDay(ctx.userId(), u.getDay())))
                .filter(u -> !pendingPersonDays.contains(DashboardV2Views.personDay(ctx.userId(), u.getDay())))
                .collect(Collectors.groupingBy(AIUsage::getDay));
        return unclaimedByDay.entrySet().stream()
                .sorted(Map.Entry.<LocalDate, List<AIUsage>>comparingByKey().reversed())
                .map(e -> new UnallocatedDay(e.getKey().toString(),
                        e.getValue().stream().mapToLong(u -> u.getInputTokens() + u.getOutputTokens()).sum(),
                        e.getValue().stream().map(AIUsage::getCostUsd).filter(Objects::nonNull)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    private List<AiUsageAllocation> allocationsInWindow(Context ctx, AiUsageAllocation.Status status) {
        return allocationRepository.findByOrgUserAndStatus(ctx.orgId(), ctx.userId(), status).stream()
                .filter(AiUsageAllocation::isActive)
                .filter(a -> ctx.window().containsDay(a.getDay()))
                .toList();
    }

    private static List<WorkItem> activeUnits(DeveloperDashboardOutput dev) {
        return dev.currentSprintItems().stream().filter(WorkItem::isActive).toList();
    }

    private static List<Double> cycleTimes(List<WorkItem> items) {
        return items.stream().map(WorkItem::getCycleTimeHours).toList();
    }

    private record PrStats(int merged, DurationStat review) {}

    private record Context(String orgId, String userId, String projectId, Sprint sprint, SprintWindow window,
                           Instant now, LocalDate today, ProjectVcsActivity.Snapshot vcs, List<VcsPullRequest> myPrs,
                           Map<String, PersonInfo> people) {}
}
