package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageUserTotals;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.connector.application.port.FluencyScoreRepository;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.ConnectorTokenRepository;
import andromeda.sei.ms.feature.connector.domain.FluencyScore;
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

import java.math.BigDecimal;
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
 *       fluency scores, the organization and its two connections: one query each;</li>
 *   <li>per visible project: its active work items with only the metric fields (raw projection,
 *       no document mapping) — the effort level needs each project's whole hierarchy;</li>
 *   <li>repositories of the visible bindings by id; their pull requests touched since the previous
 *       period started or still open (lean, one query per repository in parallel); their commits of
 *       both periods counted per
 *       day in the database (one aggregation per repository, in parallel); the bug lookup behind
 *       the merged changes;</li>
 *   <li>the people's AI usage of the current period (lean, by user id and day, in up to
 *       {@value #USAGE_GROUPS} parallel groups) and of the previous one summed per person in the
 *       database; the validated allocations splitting to the visible work items (lean, by item id
 *       in parallel chunks) of both periods;
 *       the cost of the records they cover that the people's usage did not bring (lean, by id);</li>
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
    private final FluencyScoreRepository fluencyScoreRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final GithubConnectionRepository githubConnectionRepository;
    private final AzureProjectBindingRepository azureBindingRepository;
    private final IntegrationSyncLogRepository syncLogRepository;

    /** The people's usage is read in at most this many parallel queries. */
    static final int USAGE_GROUPS = 8;
    /** ... each over at most this many slices of days. */
    static final int USAGE_SLICES = 4;

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
            CompletableFuture<List<FluencyScore>> fluencyF = loads.supply(() -> fluencyScoreRepository.findAllByOrganizationId(orgId));
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
            CompletableFuture<List<AiUsageAllocation>> allocationsF = loads.supply(
                    () -> allocations(orgId, itemsF, allocationRange));
            List<User> users = join(usersF);
            Set<String> people = people(seesAll, projects, users);
            CompletableFuture<List<AIUsage>> usageF = loads.supply(() -> usage(orgId, loads, people, period.current()));
            CompletableFuture<List<AiUsageUserTotals>> previousF = loads.supply(() -> people.isEmpty()
                    ? List.<AiUsageUserTotals>of()
                    : aiUsageRepository.summarizeTotalsByUser(orgId, people, period.previous().first(),
                    period.previous().last()));
            CompletableFuture<Map<String, BigDecimal>> costsF = loads.supply(
                    () -> coveredCosts(orgId, join(allocationsF), join(usageF)));
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
                    new Ai(join(usageF), join(previousF), join(allocationsF), join(costsF)), join(tokensF), join(fluencyF),
                    join(azureF),
                    join(githubF), join(azureBindingsF).stream().filter(b -> projectIds.contains(b.getProjectId())).toList(),
                    join(syncF).stream().filter(c -> c.projectId() == null || projectIds.contains(c.projectId()))
                            .toList());
        }
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

    private List<AiUsageAllocation> allocations(String orgId, Map<String, CompletableFuture<List<WorkItem>>> itemsF,
                                                DayRange range) {
        Set<String> ids = new LinkedHashSet<>();
        itemsF.values().forEach(f -> join(f).forEach(i -> {
            if (i.getId() != null) ids.add(i.getId());
        }));
        if (ids.isEmpty()) return List.of();
        return allocationRepository.findValidatedByOrganizationIdAndWorkItemIdInAndDayBetween(orgId, ids,
                range.first(), range.last());
    }

    /**
     * The people's usage of the range (lean), read in parallel: up to {@value #USAGE_GROUPS} groups
     * of people, each over up to {@value #USAGE_SLICES} consecutive slices of days.
     */
    private List<AIUsage> usage(String orgId, ConcurrentLoads loads, Set<String> people, DayRange range) {
        if (people.isEmpty()) return List.of();
        List<String> all = List.copyOf(people);
        int size = Math.max(1, (all.size() + USAGE_GROUPS - 1) / USAGE_GROUPS);
        List<DayRange> slices = slices(range, USAGE_SLICES);
        List<CompletableFuture<List<AIUsage>>> parts = new java.util.ArrayList<>();
        for (int from = 0; from < all.size(); from += size) {
            List<String> group = all.subList(from, Math.min(all.size(), from + size));
            for (DayRange slice : slices) {
                parts.add(loads.supply(() -> aiUsageRepository.findDashboardFieldsByOrganizationIdAndUserIdInAndDayBetween(
                        orgId, group, slice.first(), slice.last())));
            }
        }
        return parts.stream().flatMap(f -> join(f).stream()).toList();
    }

    /** {@code range} cut into at most {@code count} consecutive, non-overlapping slices covering it. */
    static List<DayRange> slices(DayRange range, int count) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(range.first(), range.last()) + 1;
        long step = Math.max(1, (days + count - 1) / count);
        List<DayRange> slices = new java.util.ArrayList<>();
        for (LocalDate start = range.first(); !start.isAfter(range.last()); start = start.plusDays(step)) {
            LocalDate end = start.plusDays(step - 1);
            slices.add(new DayRange(start, end.isAfter(range.last()) ? range.last() : end));
        }
        return slices;
    }

    /** Cost of every record the allocations cover: from the people's usage, the rest read by id. */
    private Map<String, BigDecimal> coveredCosts(String orgId, List<AiUsageAllocation> allocations, List<AIUsage> usage) {
        Map<String, BigDecimal> known = new HashMap<>();
        usage.forEach(u -> {
            if (u.getId() != null && u.getCostUsd() != null) known.put(u.getId(), u.getCostUsd());
        });
        Map<String, BigDecimal> costs = new HashMap<>();
        Set<String> missing = new LinkedHashSet<>();
        allocations.stream().filter(a -> a.getAiUsageIds() != null).flatMap(a -> a.getAiUsageIds().stream())
                .filter(Objects::nonNull).forEach(id -> {
                    BigDecimal cost = known.get(id);
                    if (cost != null) costs.put(id, cost);
                    else missing.add(id);
                });
        if (!missing.isEmpty()) {
            aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(orgId, missing).stream()
                    .filter(u -> u.getId() != null && u.getCostUsd() != null)
                    .forEach(u -> costs.put(u.getId(), u.getCostUsd()));
        }
        return costs;
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
                pullRequests.stream().filter(pr -> pr.getMergedAt() != null && !pr.getMergedAt().isBefore(from)
                        && pr.getMergedAt().isBefore(to)).toList());
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
                           List<FluencyScore> fluencyScores, Optional<AzureConnection> azureConnection,
                           Optional<GithubConnection> githubConnection, List<AzureProjectBinding> azureBindings,
                           List<SyncRunCount> syncRuns) {}

    /**
     * Version control of the visible projects: their bindings, the repositories, the pull requests
     * touched since the previous period started plus the open ones, the commits of both periods per
     * UTC day and the merged changes of both periods (delivery metrics).
     */
    public record Vcs(List<VcsRepoBinding> bindings, List<VcsRepository> repositories,
                      List<VcsPullRequest> pullRequests, Map<LocalDate, Long> commitsByDay, List<MergedChange> changes) {

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
     * {@code usage}: the people's records of the current period (lean); {@code previous}: the people's
     * usage of the previous period summed per person; {@code allocations}: validated allocations
     * splitting to visible work items, of both periods; {@code coveredCosts}: the cost of every record those allocations cover, by record id.
     */
    public record Ai(List<AIUsage> usage, List<AiUsageUserTotals> previous, List<AiUsageAllocation> allocations,
                     Map<String, BigDecimal> coveredCosts) {}
}
