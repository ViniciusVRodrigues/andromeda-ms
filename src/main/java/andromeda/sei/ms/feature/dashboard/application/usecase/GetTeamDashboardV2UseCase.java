package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.dashboard.application.BurndownCalculator;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator.AllocatedUsage;
import andromeda.sei.ms.feature.dashboard.application.ProjectAiCostCalculator.ProjectAiCost;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AiModel;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.BurndownDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Ai;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.HistoryPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Kpis;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.Member;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.RepoHealth;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.SprintStats;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.StatusByType;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamDashboardV2Output.TeamInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver.SprintSelection;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectVcsActivity;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetricsPolicy;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver.EffortSummary;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Team dashboard v2 (dashboards-customizaveis D1, {@code buildTeam}) of one project and sprint.
 *
 * <p>Every figure follows a rule that already exists, so the v2 agrees with the rest of the
 * platform: velocity and planned effort by {@link EffortBearingLevelResolver#summarize}; durations
 * by {@link Percentiles}; AI cost allocated to the project by {@link ProjectAiCostCalculator} (the
 * cost not allocated yet is reported apart and never added to the team cost); stalled pull requests
 * by {@link StalePullRequestPolicy}; time to first review by {@link ReviewMetricsPolicy}; repository
 * health by {@link ListProjectRepositoriesUseCase} (its delivery calculator included); burndown by
 * {@link BurndownCalculator}.
 */
@Service
public class GetTeamDashboardV2UseCase {

    /** The v2 preview's aging list length. */
    private static final int AGING_ITEMS_LIMIT = 8;
    private static final int AGING_PERCENTILE = 85;
    private static final double PERCENT = 100.0;
    private static final int MONEY_SCALE = 6;
    /** Status × type always lists these three, in this order; other types follow when present. */
    private static final List<WorkItem.ResolvedType> MAIN_TYPES =
            List.of(WorkItem.ResolvedType.STORY, WorkItem.ResolvedType.BUG, WorkItem.ResolvedType.TASK);

    private final ProjectRepository projectRepository;
    private final DashboardPeopleDirectory peopleDirectory;
    private final DashboardSprintResolver sprintResolver;
    private final ProjectVcsActivity vcsActivity;
    private final WorkItemRepository workItemRepository;
    private final EffortBearingLevelResolver effortResolver;
    private final ProjectAiCostCalculator aiCostCalculator;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final ListProjectRepositoriesUseCase listProjectRepositories;
    private final Clock clock;

    @Autowired
    public GetTeamDashboardV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                                     DashboardSprintResolver sprintResolver, ProjectVcsActivity vcsActivity,
                                     WorkItemRepository workItemRepository, EffortBearingLevelResolver effortResolver,
                                     ProjectAiCostCalculator aiCostCalculator, AIUsageRepository aiUsageRepository,
                                     AiUsageAllocationRepository allocationRepository,
                                     ListProjectRepositoriesUseCase listProjectRepositories) {
        this(projectRepository, peopleDirectory, sprintResolver, vcsActivity, workItemRepository, effortResolver,
                aiCostCalculator, aiUsageRepository, allocationRepository, listProjectRepositories, Clock.systemUTC());
    }

    GetTeamDashboardV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                              DashboardSprintResolver sprintResolver, ProjectVcsActivity vcsActivity,
                              WorkItemRepository workItemRepository, EffortBearingLevelResolver effortResolver,
                              ProjectAiCostCalculator aiCostCalculator, AIUsageRepository aiUsageRepository,
                              AiUsageAllocationRepository allocationRepository,
                              ListProjectRepositoriesUseCase listProjectRepositories, Clock clock) {
        this.projectRepository = projectRepository;
        this.peopleDirectory = peopleDirectory;
        this.sprintResolver = sprintResolver;
        this.vcsActivity = vcsActivity;
        this.workItemRepository = workItemRepository;
        this.effortResolver = effortResolver;
        this.aiCostCalculator = aiCostCalculator;
        this.aiUsageRepository = aiUsageRepository;
        this.allocationRepository = allocationRepository;
        this.listProjectRepositories = listProjectRepositories;
        this.clock = clock;
    }

    public TeamDashboardV2Output execute(String orgId, String userId, String projectId, String sprintId) {
        Objects.requireNonNull(orgId, "orgId");
        Project project = projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        SprintSelection selection = sprintResolver.resolve(orgId, projectId, sprintId);
        Sprint sprint = selection.selected();
        TeamInfo team = new TeamInfo(sprint != null && sprint.getTeamName() != null ? sprint.getTeamName()
                : project.getName(), project.getMemberIds().size());
        if (selection.isEmpty()) return TeamDashboardV2Output.withoutSprint(team, now);

        Context ctx = context(orgId, userId, project, sprint, now);
        TeamSprint current = teamSprint(ctx, sprint);
        return new TeamDashboardV2Output(DashboardV2Views.sprintMeta(sprint, ctx.window(), ctx.today()), team,
                kpis(ctx, current), selection.previous() == null ? null : teamSprint(ctx, selection.previous()).stats(),
                burndown(ctx, current), statusByType(current.items()), agingItems(ctx, current),
                p85CycleHours(ctx, selection), members(ctx, current), stalePrs(ctx), repos(ctx),
                DashboardV2Views.commitsByDay(ctx.vcs().commits(), c -> true, ctx.window().startDay(),
                        ctx.window().lastElapsedDay(ctx.today())),
                ai(ctx, current), history(ctx, selection), now);
    }

    private Context context(String orgId, String userId, Project project, Sprint sprint, Instant now) {
        SprintWindow window = SprintWindow.of(sprint);
        Context base = new Context(orgId, userId, project.getId(), sprint, window, now, SprintWindow.toDay(now),
                vcsActivity.load(orgId, project.getId(), window.from(), window.to()), peopleDirectory.people(orgId),
                projectWorkItemIds(orgId, project.getId()), new HashMap<>(), new LinkedHashSet<>(project.getMemberIds()));
        effortResolver.summarize(items(base, sprint)).units().stream()
                .map(WorkItem::getAssigneeUserId).filter(Objects::nonNull).forEach(base.memberIds()::add);
        return base;
    }

    private Set<String> projectWorkItemIds(String orgId, String projectId) {
        return workItemRepository.findAllByOrganizationIdAndProjectId(orgId, projectId).stream()
                .filter(WorkItem::isActive).map(WorkItem::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** Active items of a sprint of the project, read once per request. */
    private List<WorkItem> items(Context ctx, Sprint sprint) {
        return ctx.itemsBySprint().computeIfAbsent(sprint.getId(), id -> workItemRepository
                .findAllByOrganizationIdAndProjectIdAndSprintId(ctx.orgId(), ctx.projectId(), id).stream()
                .filter(WorkItem::isActive).toList());
    }

    private TeamSprint teamSprint(Context ctx, Sprint sprint) {
        SprintWindow window = SprintWindow.of(sprint);
        List<WorkItem> items = items(ctx, sprint);
        EffortSummary summary = effortResolver.summarize(items);
        ProjectAiCost aiCost = aiCostCalculator.calculate(ctx.orgId(), ctx.projectWorkItemIds(), ctx.memberIds(),
                window.startDay(), window.endDay());
        List<VcsPullRequest> created = ctx.vcs().pullRequests().stream().filter(pr -> window.contains(pr.getCreatedAt())).toList();
        boolean hasUsage = hasAiUsage(ctx, window, aiCost);
        BigDecimal cost = hasUsage ? aiCost.totalAllocated() : null;
        Double delivered = DashboardV2Views.deliveredOf(summary.planned(), summary.delivered());
        SprintStats stats = new SprintStats(summary.planned(), delivered, summary.doneUnits().size(),
                summary.units().size(),
                (int) summary.units().stream().filter(u -> summary.scope().effectiveEffort(u) != null).count(),
                (int) summary.units().stream().filter(u -> u.getAssigneeUserId() == null).count(),
                Percentiles.of(summary.doneUnits().stream().map(WorkItem::getCycleTimeHours).toList()),
                Percentiles.of(summary.doneUnits().stream().map(WorkItem::getLeadTimeHours).toList()),
                cost, perSp(cost, delivered),
                ReviewMetricsPolicy.aggregate(created, ctx.now()).timeToFirstReview(),
                (int) ctx.vcs().pullRequests().stream().filter(pr -> window.contains(pr.getMergedAt())).count());
        return new TeamSprint(stats, aiCost, hasUsage, summary, items);
    }

    /**
     * Any AI usage behind the window: a member's usage record, or cost allocated to the project. Without
     * it the AI cost is "no data" ({@code null}); with usage but nothing allocated, 0 is a real value.
     */
    private boolean hasAiUsage(Context ctx, SprintWindow window, ProjectAiCost aiCost) {
        return aiCost.totalAllocated().signum() > 0 || !aiUsageRepository.summarizeByUserAndDay(
                ctx.orgId(), ctx.memberIds(), window.startDay(), window.endDay()).isEmpty();
    }

    private static BigDecimal perSp(BigDecimal cost, Double delivered) {
        if (cost == null || delivered == null || delivered <= 0) return null;
        return cost.divide(BigDecimal.valueOf(delivered), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** The selected sprint's stats; open/stalled pull requests (current state) only for the current sprint. */
    private Kpis kpis(Context ctx, TeamSprint current) {
        SprintStats s = current.stats();
        boolean isCurrent = DashboardV2Views.isCurrent(ctx.sprint());
        List<VcsPullRequest> open = openPrs(ctx);
        return new Kpis(s.spPlanned(), s.spDelivered(), s.itemsDone(), s.itemsTotal(), s.itemsWithPoints(),
                s.unassigned(), s.cycleTimeHours(), s.leadTimeHours(), s.aiCostUsd(), s.aiCostPerSp(),
                s.timeToFirstReviewHours(), s.prsMerged(), isCurrent ? open.size() : null,
                isCurrent ? (int) open.stream().filter(pr -> StalePullRequestPolicy.isStale(pr, ctx.now())).count() : null,
                current.hasAiUsage() ? current.aiCost().totalUnallocated() : null);
    }

    private List<BurndownDay> burndown(Context ctx, TeamSprint current) {
        EffortSummary summary = current.summary();
        boolean noEffort = summary.plannedComplexity() == null;
        List<BurndownDay> days = new ArrayList<>(BurndownCalculator.compute(summary.complexityUnits(), summary.scope(),
                        ctx.window().startDay(), ctx.window().endDay(), ctx.today()).stream()
                .map(p -> new BurndownDay(p.date().toString(), DashboardV2Views.label(p.date()),
                        noEffort ? null : p.remainingSp(), p.idealSp()))
                .toList());
        if (days.isEmpty()) return days;
        double total = noEffort ? 0d : summary.plannedComplexity();
        LocalDate next = LocalDate.parse(days.getLast().date()).plusDays(1);
        for (LocalDate day : DashboardV2Views.days(next, ctx.window().endDay())) {
            days.add(new BurndownDay(day.toString(), DashboardV2Views.label(day), null,
                    BurndownCalculator.ideal(total, ctx.window().startDay(), ctx.window().endDay(), day)));
        }
        return days;
    }

    private static List<StatusByType> statusByType(List<WorkItem> items) {
        Set<WorkItem.ResolvedType> types = new LinkedHashSet<>(MAIN_TYPES);
        EnumSet.allOf(WorkItem.ResolvedType.class).stream()
                .filter(t -> items.stream().anyMatch(i -> i.getResolvedType() == t)).forEach(types::add);
        return types.stream().map(type -> new StatusByType(type.name(),
                count(items, type, WorkItem.ResolvedStatus.TODO), count(items, type, WorkItem.ResolvedStatus.IN_PROGRESS),
                count(items, type, WorkItem.ResolvedStatus.DONE))).toList();
    }

    private static int count(List<WorkItem> items, WorkItem.ResolvedType type, WorkItem.ResolvedStatus status) {
        return (int) items.stream().filter(i -> i.getResolvedType() == type && i.getResolvedStatus() == status).count();
    }

    /** Deliverable units in progress, longest-running first. */
    private List<ItemView> agingItems(Context ctx, TeamSprint current) {
        Map<String, VcsPullRequest> prByItem = DashboardV2Views.latestPullRequestByWorkItem(ctx.vcs().pullRequests());
        Map<String, String> repoNames = ctx.vcs().repositoryNames();
        return current.summary().units().stream()
                .filter(wi -> DashboardV2Views.daysInProgress(wi, ctx.now()) != null)
                .sorted(Comparator.comparing((WorkItem wi) -> DashboardV2Views.daysInProgress(wi, ctx.now())).reversed())
                .limit(AGING_ITEMS_LIMIT)
                .map(wi -> DashboardV2Views.itemView(wi, ctx.people(), null, prByItem.get(wi.getExternalId()),
                        repoNames, ctx.now()))
                .toList();
    }

    /** p85 of the cycle time of the units completed in the sprints before the selected one. */
    private Double p85CycleHours(Context ctx, SprintSelection selection) {
        List<Double> cycleTimes = selection.agingReference().stream()
                .flatMap(s -> effortResolver.summarize(items(ctx, s)).doneUnits().stream())
                .map(WorkItem::getCycleTimeHours)
                .toList();
        return Percentiles.percentile(cycleTimes, AGING_PERCENTILE);
    }

    private List<Member> members(Context ctx, TeamSprint current) {
        Map<String, List<WorkItem>> unitsByAssignee = current.summary().units().stream()
                .filter(wi -> wi.getAssigneeUserId() != null)
                .collect(Collectors.groupingBy(WorkItem::getAssigneeUserId));
        return ctx.memberIds().stream()
                .map(id -> member(ctx, current, id, unitsByAssignee.getOrDefault(id, List.of())))
                .sorted(Comparator.comparing(Member::spDelivered, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Member::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private Member member(Context ctx, TeamSprint current, String userId, List<WorkItem> units) {
        PersonInfo person = ctx.people().getOrDefault(userId, new PersonInfo(userId, userId, false, false));
        List<WorkItem> done = units.stream().filter(wi -> wi.getResolvedStatus() == WorkItem.ResolvedStatus.DONE).toList();
        EffortBearingLevelResolver.EffortScope scope = current.summary().scope();
        SprintWindow window = ctx.window();
        List<VcsPullRequest> mine = ctx.vcs().pullRequests().stream()
                .filter(pr -> userId.equals(pr.getAuthorUserId())).toList();
        return new Member(userId, person.name(), userId.equals(ctx.userId()), person.githubLinked(), person.vcsLinked(),
                done.size(), units.size(), scope.totalEffort(units),
                DashboardV2Views.deliveredOf(scope.totalEffort(units), scope.totalEffort(done)),
                (int) units.stream().filter(wi -> wi.getResolvedStatus() == WorkItem.ResolvedStatus.IN_PROGRESS).count(),
                Percentiles.of(done.stream().map(WorkItem::getCycleTimeHours).toList()),
                attributed(person, mine.stream().filter(pr -> window.contains(pr.getCreatedAt())).count()),
                attributed(person, mine.stream().filter(pr -> window.contains(pr.getMergedAt())).count()),
                attributed(person, ctx.vcs().reviews().stream().filter(e -> userId.equals(e.getActorUserId())).count()),
                peopleDirectory.aiConnected(ctx.orgId(), userId) ? current.aiCost().allocatedOf(userId) : null,
                DashboardV2Views.averageOrNull(units.stream().map(WorkItem::getTokensPerSp).toList()));
    }

    /** A provider count is "no data" for someone with no linked identity and nothing attributed. */
    private static Integer attributed(PersonInfo person, long count) {
        return !person.vcsLinked() && count == 0 ? null : (int) count;
    }

    private List<VcsPullRequest> openPrs(Context ctx) {
        return ctx.vcs().pullRequests().stream().filter(pr -> pr.getStatus() == PullRequestStatus.OPEN).toList();
    }

    /** Stalled pull requests (current state), oldest first. */
    private List<PullRequestView> stalePrs(Context ctx) {
        Map<String, String> repoNames = ctx.vcs().repositoryNames();
        Map<String, String> titles = DashboardV2Views.titlesByExternalId(items(ctx, ctx.sprint()));
        return openPrs(ctx).stream()
                .filter(pr -> StalePullRequestPolicy.isStale(pr, ctx.now()))
                .sorted(Comparator.comparing(VcsPullRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(pr -> DashboardV2Views.pullRequestView(pr, ctx.people(), repoNames, titles, ctx.now()))
                .toList();
    }

    private List<RepoHealth> repos(Context ctx) {
        Map<String, Long> commitsByRepo = ctx.vcs().commits().stream().filter(c -> c.getRepositoryId() != null)
                .collect(Collectors.groupingBy(VcsCommit::getRepositoryId, Collectors.counting()));
        return listProjectRepositories.execute(ctx.projectId(), ctx.window().startDay(),
                        ctx.window().lastElapsedDay(ctx.today())).stream()
                .map(repo -> repoHealth(ctx, repo, commitsByRepo.getOrDefault(repo.repositoryId(), 0L).intValue()))
                .toList();
    }

    private RepoHealth repoHealth(Context ctx, ProjectRepositoryOutput repo, int commits) {
        int merged = (int) ctx.vcs().pullRequests().stream()
                .filter(pr -> repo.repositoryId().equals(pr.getRepositoryId()) && ctx.window().contains(pr.getMergedAt()))
                .count();
        return new RepoHealth(repo.repositoryId(), repo.displayName(),
                repo.provider() != null ? repo.provider().name() : null, repo.openPrCount(), repo.stalePrCount(), merged,
                repo.prCycleTime(), repo.review() != null ? repo.review().timeToFirstReview() : null,
                repo.review() != null ? repo.review().reviewedWithinSlaPercent() : null, repo.delivery(), commits);
    }

    private Ai ai(Context ctx, TeamSprint current) {
        SprintWindow window = ctx.window();
        List<AllocatedUsage> allocated = aiCostCalculator.allocatedUsage(ctx.orgId(), ctx.projectWorkItemIds(),
                window.startDay(), window.endDay());
        List<AiUsageUserDay> memberUsage = aiUsageRepository.summarizeByUserAndDay(
                ctx.orgId(), ctx.memberIds(), window.startDay(), window.endDay());
        Set<String> validated = allocationRepository
                .findValidatedUserDays(ctx.orgId(), ctx.memberIds(), window.startDay(), window.endDay()).stream()
                .filter(d -> d.userId() != null && d.day() != null && ctx.memberIds().contains(d.userId()))
                .map(d -> DashboardV2Views.personDay(d.userId(), d.day()))
                .collect(Collectors.toSet());
        return new Ai(aiByDay(ctx, allocated), aiByModel(allocated),
                DashboardV2Views.allocationSummaryOfDays(memberUsage, validated),
                (int) memberUsage.stream().map(AiUsageUserDay::userId).filter(Objects::nonNull).distinct().count(),
                current.hasAiUsage() ? current.aiCost().totalUnallocated() : null);
    }

    /** Allocated cost and the allocated share of tokens/interactions per day, up to today. */
    private static List<AiDay> aiByDay(Context ctx, List<AllocatedUsage> allocated) {
        Map<LocalDate, List<AllocatedUsage>> byDay = allocated.stream().filter(a -> a.usage().getDay() != null)
                .collect(Collectors.groupingBy(a -> a.usage().getDay()));
        return DashboardV2Views.days(ctx.window().startDay(), ctx.window().lastElapsedDay(ctx.today())).stream()
                .map(day -> {
                    List<AllocatedUsage> records = byDay.getOrDefault(day, List.of());
                    return new AiDay(day.toString(), DashboardV2Views.label(day),
                            share(records, a -> a.usage().getInputTokens()), share(records, a -> a.usage().getOutputTokens()),
                            share(records, a -> a.usage().getInteractions()), cost(records));
                })
                .toList();
    }

    private static List<AiModel> aiByModel(List<AllocatedUsage> allocated) {
        return allocated.stream().filter(a -> a.usage().getModel() != null)
                .collect(Collectors.groupingBy(a -> a.usage().getModel())).entrySet().stream()
                .map(e -> new AiModel(e.getKey(),
                        share(e.getValue(), a -> a.usage().getInputTokens() + a.usage().getOutputTokens()),
                        cost(e.getValue()), share(e.getValue(), a -> a.usage().getInteractions())))
                .sorted(Comparator.comparing(AiModel::costUsd).reversed())
                .toList();
    }

    private static BigDecimal cost(List<AllocatedUsage> records) {
        return records.stream().map(AllocatedUsage::allocatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long share(List<AllocatedUsage> records, java.util.function.ToLongFunction<AllocatedUsage> amount) {
        return records.stream()
                .map(a -> a.share().multiply(BigDecimal.valueOf(amount.applyAsLong(a))))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private List<HistoryPoint> history(Context ctx, SprintSelection selection) {
        return selection.history().stream().map(sprint -> {
            TeamSprint ts = teamSprint(ctx, sprint);
            SprintStats s = ts.stats();
            Long tokens = ts.hasAiUsage() ? ts.aiCost().allocatedTokens() : null;
            return new HistoryPoint(sprint.getId(), DashboardV2Views.historyLabel(sprint), sprint.getName(),
                    sprint.getId().equals(selection.selected().getId()), DashboardV2Views.isCurrent(sprint),
                    s.spPlanned(), s.spDelivered(), predictability(s), s.cycleTimeHours(), s.aiCostUsd(), s.aiCostPerSp(), tokens);
        }).toList();
    }

    private static Double predictability(SprintStats s) {
        if (s.spPlanned() == null || s.spPlanned() <= 0 || s.spDelivered() == null) return null;
        return s.spDelivered() / s.spPlanned() * PERCENT;
    }

    private record TeamSprint(SprintStats stats, ProjectAiCost aiCost, boolean hasAiUsage, EffortSummary summary,
                              List<WorkItem> items) {}

    private record Context(String orgId, String userId, String projectId, Sprint sprint, SprintWindow window,
                           Instant now, LocalDate today, ProjectVcsActivity.Snapshot vcs,
                           Map<String, PersonInfo> people, Set<String> projectWorkItemIds,
                           Map<String, List<WorkItem>> itemsBySprint, Set<String> memberIds) {}
}
