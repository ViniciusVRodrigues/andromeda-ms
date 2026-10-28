package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.BranchCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.FlowDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.LabelCount;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.ReviewQueue;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Rework;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.Scope;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StageTimes;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StageTimesPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.TeamFlowV2Output.StateTimes;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver;
import andromeda.sei.ms.feature.dashboard.application.v2.ProjectPeriodItems;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardSprintResolver.SprintSelection;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardV2Views;
import andromeda.sei.ms.feature.dashboard.application.v2.SprintWindow;
import andromeda.sei.ms.feature.dashboard.application.v2.WorkItemReopens;
import andromeda.sei.ms.feature.dashboard.application.v2.WorkItemStateTimeline;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver.EffortSummary;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItem.ResolvedStatus;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransition;
import andromeda.sei.ms.feature.workitem.domain.WorkItemTransitionRepository;
import andromeda.sei.ms.shared.domain.metrics.Percentiles;
import andromeda.sei.ms.shared.domain.metrics.Ratios;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Team flow v2 (dashboards-customizaveis D1/1.13, {@code buildTeamFlow}) of one project and sprint,
 * with the three state categories recorded today and without the blocks that need new collection.
 *
 * <p>Rules reused from the rest of the platform: the sprint and its 404s by
 * {@link DashboardSprintResolver}; the deliverable units by {@link EffortBearingLevelResolver#summarize}
 * (the same base as the team cycle time); durations by {@link Percentiles}; reopens by
 * {@link WorkItemReopens} (the definition of the project quality endpoint). Transitions are read in
 * one organization-scoped query.
 */
@Service
public class GetTeamFlowV2UseCase {

    static final int REOPENED_LIST_LIMIT = 50;

    /** Epics and features are containers: they are not counted in completions or reopens. */
    private static final Set<WorkItem.ResolvedType> CONTAINER_TYPES =
            EnumSet.of(WorkItem.ResolvedType.EPIC, WorkItem.ResolvedType.FEATURE);

    private final ProjectRepository projectRepository;
    private final DashboardPeopleDirectory peopleDirectory;
    private final DashboardSprintResolver sprintResolver;
    private final VcsReadPort vcsReadPort;
    private final WorkItemRepository workItemRepository;
    private final WorkItemTransitionRepository transitionRepository;
    private final EffortBearingLevelResolver effortResolver;
    private final Clock clock;

    @Autowired
    public GetTeamFlowV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                                DashboardSprintResolver sprintResolver, VcsReadPort vcsReadPort,
                                WorkItemRepository workItemRepository, WorkItemTransitionRepository transitionRepository,
                                EffortBearingLevelResolver effortResolver) {
        this(projectRepository, peopleDirectory, sprintResolver, vcsReadPort, workItemRepository, transitionRepository,
                effortResolver, Clock.systemUTC());
    }

    GetTeamFlowV2UseCase(ProjectRepository projectRepository, DashboardPeopleDirectory peopleDirectory,
                         DashboardSprintResolver sprintResolver, VcsReadPort vcsReadPort,
                         WorkItemRepository workItemRepository, WorkItemTransitionRepository transitionRepository,
                         EffortBearingLevelResolver effortResolver, Clock clock) {
        this.projectRepository = projectRepository;
        this.peopleDirectory = peopleDirectory;
        this.sprintResolver = sprintResolver;
        this.vcsReadPort = vcsReadPort;
        this.workItemRepository = workItemRepository;
        this.transitionRepository = transitionRepository;
        this.effortResolver = effortResolver;
        this.clock = clock;
    }

    public TeamFlowV2Output execute(String orgId, String projectId, String sprintId) {
        Objects.requireNonNull(orgId, "orgId");
        projectRepository.findActiveByIdAndOrganizationId(projectId, orgId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        Instant now = clock.instant();
        SprintSelection selection = sprintResolver.resolve(orgId, projectId, sprintId);
        if (selection.isEmpty()) return TeamFlowV2Output.withoutSprint(now);

        Context ctx = context(orgId, projectId, selection, now);
        EffortSummary current = effortResolver.summarize(ctx.sprintItems(selection.selected()));
        return new TeamFlowV2Output(DashboardV2Views.sprintMeta(ctx.sprint(), ctx.window(), ctx.today()), null,
                List.of(), cfd(ctx), stateTimes(ctx, selection, current), rework(ctx, effortResolver.scope(java.util.stream.Stream.concat(
                ctx.sprintItems(selection.selected()).stream(), ctx.doneInWindow().stream()).distinct().toList())),
                scope(ctx, current),
                reviewQueue(ctx), now);
    }

    private Context context(String orgId, String projectId, SprintSelection selection, Instant now) {
        Sprint sprint = selection.selected();
        SprintWindow window = SprintWindow.of(sprint);
        Map<String, List<WorkItem>> itemsBySprint = itemsBySprint(orgId, projectId, selection);
        List<WorkItem> doneInWindow = workItemRepository
                .findActiveByOrganizationIdAndProjectIdAndCompletedAtInRange(orgId, projectId, window.from(), window.to())
                .stream()
                .filter(wi -> !CONTAINER_TYPES.contains(wi.getResolvedType()))
                .filter(wi -> window.contains(wi.getCompletedAt()))
                .toList();
        List<VcsRepository> repositories = vcsReadPort.repositoriesOfProject(orgId, projectId);
        return new Context(orgId, sprint, window, now, SprintWindow.toDay(now), itemsBySprint, doneInWindow,
                transitionsOf(orgId, itemsBySprint.values(), doneInWindow), repositories,
                pullRequests(orgId, repositories, window, DashboardV2Views.isCurrent(sprint)),
                peopleDirectory.people(orgId),
                StalePullRequestPolicy.chosenBranchesByRepository(
                        vcsReadPort.bindingsOfProject(orgId, projectId), repositories));
    }

    /** Active items of the selected sprint and of its history, in one query, grouped by sprint. */
    private Map<String, List<WorkItem>> itemsBySprint(String orgId, String projectId, SprintSelection selection) {
        Set<String> sprintIds = new LinkedHashSet<>();
        selection.history().stream().map(Sprint::getId).forEach(sprintIds::add);
        sprintIds.add(selection.selected().getId());
        return workItemRepository.findActiveByOrganizationIdAndProjectIdAndSprintIdIn(orgId, projectId, sprintIds)
                .stream().filter(WorkItem::isActive).filter(wi -> wi.getSprintId() != null)
                .collect(Collectors.groupingBy(WorkItem::getSprintId));
    }

    /** Transitions of every item the payload reads, in one organization-scoped query. */
    private Map<String, List<WorkItemTransition>> transitionsOf(String orgId, Collection<List<WorkItem>> sprintItems,
                                                                List<WorkItem> doneInWindow) {
        Set<String> ids = Stream.concat(sprintItems.stream().flatMap(List::stream), doneInWindow.stream())
                .map(WorkItem::getId).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) return Map.of();
        return transitionRepository.findActiveByOrganizationIdAndWorkItemIdIn(orgId, ids).stream()
                .filter(t -> t.getWorkItemId() != null)
                .collect(Collectors.groupingBy(WorkItemTransition::getWorkItemId));
    }

    /**
     * Pull requests of the project's repositories created in the sprint, plus the open ones (current
     * state) for the current sprint — never the whole history.
     */
    private List<VcsPullRequest> pullRequests(String orgId, List<VcsRepository> repositories, SprintWindow window,
                                              boolean isCurrent) {
        List<String> repositoryIds = repositories.stream().map(VcsRepository::getId).filter(Objects::nonNull).toList();
        if (repositoryIds.isEmpty()) return List.of();
        Map<String, VcsPullRequest> byId = new LinkedHashMap<>();
        Stream.concat(vcsReadPort.pullRequestsCreatedIn(orgId, repositoryIds, window.from(), window.to()).stream(),
                        isCurrent ? vcsReadPort.openPullRequests(orgId, repositoryIds).stream() : Stream.empty())
                .forEach(pr -> byId.putIfAbsent(pr.getId() != null ? pr.getId() : "#" + byId.size(), pr));
        return List.copyOf(byId.values());
    }

    /** Items of the sprint per category at the end of each working day elapsed so far; empty without items. */
    private static List<FlowDay> cfd(Context ctx) {
        /* épicos e features não são trabalho da sprint: ficam fora do fluxo, como em reabertos e no escopo em risco */
        List<WorkItem> items = ctx.sprintItems(ctx.sprint()).stream()
                .filter(wi -> !ProjectPeriodItems.isContainer(wi)).toList();
        if (items.isEmpty()) return List.of();
        List<WorkItemStateTimeline> timelines = items.stream().map(ctx::timeline).toList();
        return DashboardV2Views.days(ctx.window().startDay(), ctx.window().lastElapsedDay(ctx.today())).stream()
                .filter(GetTeamFlowV2UseCase::isWorkingDay)
                .map(day -> flowDay(day, items, timelines))
                .toList();
    }

    private static FlowDay flowDay(LocalDate day, List<WorkItem> items, List<WorkItemStateTimeline> timelines) {
        Instant endOfDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
        Map<ResolvedStatus, Integer> counts = new EnumMap<>(ResolvedStatus.class);
        for (int i = 0; i < items.size(); i++) {
            Instant created = items.get(i).getCreatedAt();
            if (created != null && created.isAfter(endOfDay)) continue;
            counts.merge(timelines.get(i).categoryAt(endOfDay), 1, Integer::sum);
        }
        return new FlowDay(day.toString(), DashboardV2Views.label(day), counts.getOrDefault(ResolvedStatus.TODO, 0),
                counts.getOrDefault(ResolvedStatus.IN_PROGRESS, 0), counts.getOrDefault(ResolvedStatus.DONE, 0));
    }

    private StateTimes stateTimes(Context ctx, SprintSelection selection, EffortSummary current) {
        List<StageTimesPoint> history = selection.history().stream().map(sprint -> {
            StageTimes times = stageTimes(ctx, sprint, effortResolver.summarize(ctx.sprintItems(sprint)));
            return new StageTimesPoint(sprint.getId(), DashboardV2Views.historyLabel(sprint), sprint.getName(),
                    times.queueHours(), times.inProgressHours(), times.resolvedHours());
        }).toList();
        return new StateTimes(stageTimes(ctx, ctx.sprint(), current), history);
    }

    private static StageTimes stageTimes(Context ctx, Sprint sprint, EffortSummary summary) {
        Instant sprintStart = SprintWindow.of(sprint).from();
        List<WorkItem> done = summary.doneUnits();
        return new StageTimes(Percentiles.of(done.stream().map(wi -> queueHours(wi, sprintStart)).toList()),
                Percentiles.of(done.stream().map(wi -> ctx.timeline(wi).inProgressHours()).toList()), null);
    }

    /**
     * From entering the sprint ({@code max(createdAt, sprint start)} — the date an item was added to
     * the sprint is not recorded) to starting; {@code null} for an item started before entering.
     */
    private static Double queueHours(WorkItem item, Instant sprintStart) {
        if (item.getStartedAt() == null) return null;
        Instant entry = item.getCreatedAt() != null && item.getCreatedAt().isAfter(sprintStart)
                ? item.getCreatedAt() : sprintStart;
        return item.getStartedAt().isBefore(entry) ? null : Ratios.hours(entry, item.getStartedAt());
    }

    private static Rework rework(Context ctx, EffortBearingLevelResolver.EffortScope effortScope) {
        Map<String, Integer> reopens = WorkItemReopens.byWorkItem(ctx.doneInWindow().stream()
                .flatMap(wi -> ctx.transitionsOf(wi).stream()).toList());
        Map<String, VcsPullRequest> prByItem = DashboardV2Views.latestPullRequestByWorkItem(ctx.pullRequests());
        Map<String, String> repoNames = repositoryNames(ctx.repositories());
        List<WorkItem> reopenedItems = ctx.doneInWindow().stream()
                .filter(wi -> reopens.getOrDefault(wi.getId(), 0) > 0)
                .sorted(Comparator.comparing(WorkItem::getCompletedAt).reversed())
                .toList();
        /* a lista para a tela é limitada; a taxa usa o total */
        List<ItemView> reopened = reopenedItems.stream().limit(REOPENED_LIST_LIMIT)
                .map(wi -> DashboardV2Views.itemView(wi, ctx.people(), null, prByItem.get(wi.getExternalId()),
                        repoNames, ctx.now(), effortScope, ctx.chosenBranches()))
                .toList();
        int done = ctx.doneInWindow().size();
        return new Rework(reopened, done, done == 0 ? null : Ratios.percent(reopenedItems.size(), done));
    }

    /**
     * Complexity ({@code points}) of the units not done yet, for the current sprint; {@code null} when
     * nothing carries it.
     */
    private static Scope scope(Context ctx, EffortSummary current) {
        if (!DashboardV2Views.isCurrent(ctx.sprint())) return new Scope(null);
        Double points = null;
        if (current.plannedComplexity() != null) {
            List<WorkItem> notDoneStories = current.complexityUnits().stream()
                    .filter(wi -> wi.getResolvedStatus() != ResolvedStatus.DONE).toList();
            Double sum = current.scope().totalComplexity(notDoneStories);
            points = sum == null ? Double.valueOf(0d) : sum;
        }
        return new Scope(points);
    }

    private static ReviewQueue reviewQueue(Context ctx) {
        List<LabelCount> labels = counted(ctx.pullRequests().stream()
                .filter(pr -> ctx.window().contains(pr.getCreatedAt()))
                .flatMap(pr -> pr.getLabels() == null ? Stream.<String>empty() : pr.getLabels().stream()),
                LabelCount::new);
        if (!DashboardV2Views.isCurrent(ctx.sprint())) return new ReviewQueue(null, null, List.of(), labels);
        List<VcsPullRequest> open = ctx.pullRequests().stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN).toList();
        return new ReviewQueue(open.size(), (int) open.stream().filter(VcsPullRequest::isDraft).count(),
                counted(open.stream().map(VcsPullRequest::getBaseBranch), BranchCount::new), labels);
    }

    /** Occurrences of each non-blank value, most frequent first, then alphabetical. */
    private static <T> List<T> counted(Stream<String> values, CountFactory<T> factory) {
        return values.filter(v -> v != null && !v.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> factory.create(e.getKey(), e.getValue().intValue()))
                .toList();
    }

    private static Map<String, String> repositoryNames(List<VcsRepository> repositories) {
        return repositories.stream().filter(r -> r.getId() != null)
                .collect(Collectors.toMap(VcsRepository::getId,
                        r -> r.getDisplayName() != null ? r.getDisplayName() : r.getId(), (a, b) -> a));
    }

    private static boolean isWorkingDay(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    @FunctionalInterface
    private interface CountFactory<T> {
        T create(String value, int count);
    }

    private record Context(String orgId, Sprint sprint, SprintWindow window, Instant now, LocalDate today,
                           Map<String, List<WorkItem>> itemsBySprint, List<WorkItem> doneInWindow,
                           Map<String, List<WorkItemTransition>> transitions, List<VcsRepository> repositories,
                           List<VcsPullRequest> pullRequests, Map<String, PersonInfo> people,
                           Map<String, Set<String>> chosenBranches) {

        List<WorkItem> sprintItems(Sprint s) {
            return itemsBySprint.getOrDefault(s.getId(), List.of());
        }

        List<WorkItemTransition> transitionsOf(WorkItem item) {
            return transitions.getOrDefault(item.getId(), List.of());
        }

        WorkItemStateTimeline timeline(WorkItem item) {
            return WorkItemStateTimeline.of(item, transitionsOf(item));
        }
    }
}
