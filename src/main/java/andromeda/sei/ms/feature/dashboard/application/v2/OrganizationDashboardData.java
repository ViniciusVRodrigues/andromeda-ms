package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageDayModel;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageEditorTotal;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserDay;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserTotals;
import andromeda.sei.ms.feature.allocation.domain.AllocationCoverage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncRunCount;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static andromeda.sei.ms.feature.dashboard.application.v2.ConcurrentLoads.join;

/**
 * Everything the organization overview v2 reads, scoped by {@code organizationId}, run concurrently
 * on virtual threads ({@link ConcurrentLoads}) so the request waits for the slowest chain, not the
 * sum. No read grows with the period beyond its window, and only two grow with the number of
 * visible projects (one indexed query each per project, in parallel):
 *
 * <ol>
 *   <li>projects, users, sprints, version-control bindings, Azure bindings, connector tokens,
 *       the organization and its two connections: one query each;</li>
 *   <li>per visible project: its active work items with only the metric fields (raw projection,
 *       no document mapping) — the effort level needs each project's whole hierarchy;</li>
 *   <li>repositories of the visible bindings by id; their pull requests touched since the previous
 *       period started or still open (lean, one query per repository in parallel); their commits of
 *       both periods counted per
 *       day in the database (one aggregation per repository, in parallel); the bug lookup behind
 *       the merged changes;</li>
 *   <li>the people's AI usage of the current period summed in the database (per day and model, per
 *       editor, per person and day; three aggregations in parallel) and of the previous one summed per
 *       person; the validated allocations splitting to the visible work items of both periods, each
 *       with the cost and tokens of the records it cites summed in the database (no usage row and no
 *       allocation id list crosses to the JVM);</li>
 *   <li>the leaf sync runs of the last 14 days counted per project, day and status in the database.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class OrganizationDashboardData {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final EffortBearingLevelResolver effortResolver;
    private final VcsRepoBindingRepository bindingRepository;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsReadPort vcsReadPort;
    private final VcsCommitRepository commitRepository;
    private final DeliveryMetricsService deliveryMetricsService;
    private final OrganizationRepository organizationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final ConnectorTokenRepository connectorTokenRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final AzureProjectBindingRepository azureBindingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    /**
     * @param visibleProjectIds projects where the caller holds {@code DASHBOARD_STRATEGIC}; nothing
     *                          project-scoped outside them is read
     */
    public Snapshot load(String orgId, Set<String> visibleProjectIds, DashboardPeriod period, LocalDate today) {
        try (ConcurrentLoads loads = new ConcurrentLoads()) {
            CompletableFuture<List<Project>> activeF = loads.supply(() -> projectRepository.findAllActiveByOrganizationId(orgId));
            CompletableFuture<List<User>> usersF = loads.supply(() -> userRepository.findAllByOrganizationId(orgId));
            CompletableFuture<List<VcsRepoBinding>> bindingsF = loads.supply(() -> bindingRepository.findAllActiveByOrganizationId(orgId));
            CompletableFuture<Organization> organizationF = loads.supply(() -> organizationRepository.findById(orgId).orElse(null));
            CompletableFuture<List<ConnectorToken>> tokensF = loads.supply(() -> connectorTokenRepository.findAllByOrganizationId(orgId));
            CompletableFuture<Optional<AzureConnection>> azureF = loads.supply(() -> azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId));
            CompletableFuture<Optional<GithubConnection>> githubF = loads.supply(() -> githubConnectionRepository.findByOrganizationIdAndActiveTrue(orgId));
            CompletableFuture<List<SyncRunCount>> syncF = loads.supply(() -> syncLogRepository.countByProjectDayAndStatus(
                    orgId, SyncRunsSummary.LEAF_OPERATIONS, SyncRunsSummary.windowStart(today)));

            List<Project> active = join(activeF);
            List<Project> projects = active.stream().filter(p -> p.getId() != null && visibleProjectIds.contains(p.getId()))
                    .sorted(Comparator.comparing(Project::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            Set<String> projectIds = projects.stream().map(Project::getId).collect(Collectors.toCollection(LinkedHashSet::new));
            boolean seesAll = seesAll(active, projects);

            CompletableFuture<List<Sprint>> sprintsF = projects.isEmpty() ? CompletableFuture.completedFuture(List.of())
                    : loads.supply(() -> sprintRepository.findAllByOrganizationId(orgId));
            CompletableFuture<List<AzureProjectBinding>> azureBindingsF = projects.isEmpty()
                    ? CompletableFuture.completedFuture(List.of())
                    : loads.supply(() -> azureBindingRepository.findAllByOrganizationIdAndActiveTrue(orgId));
            Map<String, CompletableFuture<List<WorkItem>>> itemsF = new LinkedHashMap<>();
            for (String projectId : projectIds) {
                itemsF.put(projectId, loads.supply(() -> workItemRepository.findMetricFieldsByOrganizationIdAndProjectId(orgId, projectId)));
            }
            // kpis.aiCostUsd (and previous) is the allocated cost for every caller: both periods.
            DayRange allocationRange = new DayRange(period.previous().first(), period.current().last());
            // ... and back to the start of a sprint crossing the period that began before it (sprintHistory).
            CompletableFuture<List<AllocationCoverage>> allocationsF = loads.supply(
                    () -> allocations(orgId, itemsF, new DayRange(earliestStart(join(sprintsF), projectIds,
                            period.current(), allocationRange.first()), allocationRange.last())));
            List<User> users = join(usersF);
            Set<String> people = people(seesAll, projects, users);
            DayRange current = period.current();
            CompletableFuture<List<AiUsageDayModel>> dayModelF = loads.supply(() -> people.isEmpty() ? List.<AiUsageDayModel>of()
                    : aiUsageRepository.summarizeByDayAndModel(orgId, people, current.first(), current.last()));
            CompletableFuture<List<AiUsageEditorTotal>> editorF = loads.supply(() -> people.isEmpty() ? List.<AiUsageEditorTotal>of()
                    : aiUsageRepository.summarizeInteractionsByEditor(orgId, people, current.first(), current.last()));
            CompletableFuture<List<AiUsageUserDay>> userDayF = loads.supply(() -> people.isEmpty() ? List.<AiUsageUserDay>of()
                    : aiUsageRepository.summarizeByUserAndDay(orgId, people, current.first(), current.last()));
            CompletableFuture<List<AiUsageUserTotals>> previousF = loads.supply(() -> people.isEmpty()
                    ? List.<AiUsageUserTotals>of()
                    : aiUsageRepository.summarizeTotalsByUser(orgId, people, period.previous().first(),
                    period.previous().last()));
            CompletableFuture<Vcs> vcsF = loads.supply(() -> vcs(orgId, loads, join(bindingsF), projectIds, period));

            Map<String, List<Sprint>> sprints = join(sprintsF).stream().filter(s -> s.getProjectId() != null)
                    .collect(Collectors.groupingBy(Sprint::getProjectId));
            Map<String, ProjectPeriodItems> items = new LinkedHashMap<>();
            Map<String, String> itemProject = new HashMap<>();
            for (String projectId : projectIds) {
                List<WorkItem> projectItems = join(itemsF.get(projectId));
                items.put(projectId, ProjectPeriodItems.of(projectItems, sprints.getOrDefault(projectId, List.of()),
                        effortResolver));
                projectItems.forEach(i -> {
                    if (i.getId() != null) itemProject.putIfAbsent(i.getId(), projectId);
                });
            }
            return new Snapshot(projects, users, people, seesAll, items, itemProject, join(vcsF), join(organizationF),
                    new Ai(join(dayModelF), join(editorF), join(userDayF), join(previousF), join(allocationsF)), join(tokensF),
                    join(azureF),
                    join(githubF), join(azureBindingsF).stream().filter(b -> projectIds.contains(b.getProjectId())).toList(),
                    join(syncF).stream().filter(c -> c.projectId() == null || projectIds.contains(c.projectId()))
                            .toList());
        }
    }

    /** The earliest start among the visible projects' sprints crossing {@code range}, when before {@code floor}; else {@code floor}. */
    static LocalDate earliestStart(List<Sprint> sprints, Set<String> projectIds, DayRange range, LocalDate floor) {
        LocalDate earliest = floor;
        for (Sprint sprint : sprints) {
            if (!sprint.isActive() || sprint.getStartDate() == null || sprint.getEndDate() == null
                    || !projectIds.contains(sprint.getProjectId())) continue;
            SprintWindow window = SprintWindow.of(sprint);
            boolean crosses = !window.startDay().isAfter(range.last()) && !window.endDay().isBefore(range.first());
            if (crosses && window.startDay().isBefore(earliest)) earliest = window.startDay();
        }
        return earliest;
    }

    /** The caller sees every active project of the organization (and there is at least one). */
    static boolean seesAll(List<Project> active, List<Project> visible) {
        Set<String> activeIds = active.stream().map(Project::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        return !activeIds.isEmpty() && visible.size() == activeIds.size();
    }

    /**
     * The members of the visible projects; every active user when the caller sees all the active
     * projects of the organization (so people in no project still count, as in the preview).
     */
    static Set<String> people(boolean seesAll, List<Project> visible, List<User> users) {
        if (seesAll) {
            return users.stream().filter(User::isActive).map(User::getId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return visible.stream().filter(p -> p.getMemberIds() != null).flatMap(p -> p.getMemberIds().stream())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AllocationCoverage> allocations(String orgId, Map<String, CompletableFuture<List<WorkItem>>> itemsF,
                                                DayRange range) {
        Set<String> ids = new LinkedHashSet<>();
        itemsF.values().forEach(f -> join(f).forEach(i -> {
            if (i.getId() != null) ids.add(i.getId());
        }));
        if (ids.isEmpty()) return List.of();
        return allocationRepository.summarizeValidatedCoverage(orgId, ids,
                range.first(), range.last());
    }

    private Vcs vcs(String orgId, ConcurrentLoads loads, List<VcsRepoBinding> allBindings, Set<String> projectIds,
                    DashboardPeriod period) {
        List<VcsRepoBinding> bindings = allBindings.stream()
                .filter(b -> b.getRepositoryId() != null && projectIds.contains(b.getProjectId())).toList();
        Set<String> repositoryIds = bindings.stream().map(VcsRepoBinding::getRepositoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (repositoryIds.isEmpty()) return new Vcs(bindings, List.of(), List.of(), Map.of(), List.of());
        Instant from = period.previous().from();
        Instant to = period.current().to();
        CompletableFuture<List<VcsRepository>> repositoriesF = loads.supply(
                () -> repositoryRepository.findAllByOrganizationIdAndIdIn(orgId, repositoryIds));
        // One lean read per repository, in parallel (separate cursors use several server cores).
        List<CompletableFuture<List<VcsPullRequest>>> pullRequestsF = repositoryIds.stream()
                .map(id -> loads.supply(() -> vcsReadPort.pullRequestMetricFieldsTouchedSince(orgId, List.of(id), from)))
                .toList();
        // One covered aggregation per repository, in parallel.
        List<CompletableFuture<Map<LocalDate, Long>>> commitsF = repositoryIds.stream()
                .map(id -> loads.supply(() -> commitRepository.countByDay(orgId, List.of(id), from, to))).toList();
        List<VcsRepository> repositories = join(repositoriesF);
        List<VcsPullRequest> pullRequests = DeliveryMetricsService.union(pullRequestsF.stream().map(f -> join(f)).toList(),
                VcsPullRequest::getId).stream().filter(pr -> pr.getId() != null && pr.isActive()).toList();
        List<MergedChange> changes = deliveryMetricsService.mergedChangesFrom(orgId, repositories,
                pullRequests.stream().filter(pr -> pr.getMergedAtIfMerged() != null && !pr.getMergedAtIfMerged().isBefore(from)
                        && pr.getMergedAtIfMerged().isBefore(to)).toList());
        Map<LocalDate, Long> commits = new HashMap<>();
        commitsF.forEach(f -> join(f).forEach((day, n) -> commits.merge(day, n, Long::sum)));
        return new Vcs(bindings, repositories, pullRequests, commits, changes);
    }

    /**
     * @param people      ids of the people the AI and coverage blocks are about (see {@link #people})
     * @param seesAll     the caller sees every active project (the privacy rules of the AI cost differ)
     * @param items       work items and sprints of each visible project, by project id (name order)
     * @param itemProject the visible project of each work item id
     * @param syncRuns    leaf sync runs of the last 14 days per project (visible or organization level), day and status
     */
    public record Snapshot(List<Project> projects, List<User> users, Set<String> people, boolean seesAll,
                           Map<String, ProjectPeriodItems> items, Map<String, String> itemProject, Vcs vcs,
                           Organization organization, Ai ai, List<ConnectorToken> connectorTokens,
                           Optional<AzureConnection> azureConnection,
                           Optional<GithubConnection> githubConnection, List<AzureProjectBinding> azureBindings,
                           List<SyncRunCount> syncRuns) {}

    /**
     * Version control of the visible projects: their bindings, the repositories, the pull requests
     * touched since the previous period started plus the open ones, the commits of both periods per
     * UTC day and the merged changes of both periods (delivery metrics).
     */
    public record Vcs(List<VcsRepoBinding> bindings, List<VcsRepository> repositories,
                      List<VcsPullRequest> pullRequests, Map<LocalDate, Long> commitsByDay, List<MergedChange> changes) {

        /** Repository id to the branches chosen on its binding (a promotion pull request is not stalled). */
        public Map<String, Set<String>> chosenBranches() {
            return StalePullRequestPolicy.chosenBranchesByRepository(bindings, repositories);
        }

        /** Branches chosen on the bindings of one project: a repository bound to two projects has one set per link. */
        public Map<String, Set<String>> chosenBranches(String projectId) {
            return StalePullRequestPolicy.chosenBranchesByRepository(
                    bindings.stream().filter(b -> projectId.equals(b.getProjectId())).toList(), repositories);
        }

        /** Branches chosen on every binding of each repository (one set per link). */
        public Map<String, List<Set<String>>> chosenBranchesPerLink() {
            Map<String, String> defaults = new java.util.HashMap<>();
            repositories.stream().filter(r -> r != null && r.getId() != null)
                    .forEach(r -> defaults.putIfAbsent(r.getId(), r.getDefaultBranch()));
            Map<String, List<Set<String>>> byRepository = new java.util.HashMap<>();
            for (VcsRepoBinding binding : bindings) {
                if (binding.getRepositoryId() == null) continue;
                byRepository.computeIfAbsent(binding.getRepositoryId(), id -> new java.util.ArrayList<>())
                        .add(Set.copyOf(binding.effectiveBranches(defaults.get(binding.getRepositoryId()))));
            }
            return byRepository;
        }

        public Set<String> repositoryIdsOf(String projectId) {
            return bindings.stream().filter(b -> projectId.equals(b.getProjectId()))
                    .map(VcsRepoBinding::getRepositoryId).collect(Collectors.toSet());
        }

        /** Commits authored in the days of {@code range}. */
        public long commitsIn(DayRange range) {
            return commitsByDay.entrySet().stream().filter(e -> range.containsDay(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
        }
    }

    /**
     * {@code byDayModel}, {@code byEditor}, {@code byUserDay}: the people's usage of the current period
     * summed in the database; {@code previous}: the previous period summed per person;
     * {@code allocations}: validated allocations splitting to visible work items, of both periods, each
     * with the cost and tokens of the records it covers.
     */
    public record Ai(List<AiUsageDayModel> byDayModel, List<AiUsageEditorTotal> byEditor,
                     List<AiUsageUserDay> byUserDay, List<AiUsageUserTotals> previous,
                     List<AllocationCoverage> allocations) {}
}
