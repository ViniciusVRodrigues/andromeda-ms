package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
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
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Everything the organization overview v2 reads, loaded once per request and scoped by
 * {@code organizationId}. The query count does not grow with the period, and only the work items
 * grow with the number of visible projects (one indexed query per project — the effort level needs
 * each project's whole hierarchy):
 *
 * <ol>
 *   <li>projects, users, sprints, version-control bindings, Azure bindings, connector tokens,
 *       fluency scores, the organization and its two connections: one query each;</li>
 *   <li>repositories of the visible bindings by id; pull requests of those repositories created or
 *       merged since the previous period started, plus the open ones (three windowed queries, never
 *       a repository's whole history); commits of both periods; the bug lookup behind the merged
 *       changes;</li>
 *   <li>the people's AI usage of both periods (by user id and day), the validated allocations that
 *       split to the visible projects' work items (by work item id, never the organization's whole
 *       allocation history), and the usage records those allocations cover that were not loaded yet
 *       (by id);</li>
 *   <li>the sync logs of the last 14 days, one query per integration.</li>
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

    /**
     * @param visibleProjectIds projects where the caller holds {@code DASHBOARD_STRATEGIC}; nothing
     *                          outside them is read beyond the organization-level states
     */
    public Snapshot load(String orgId, Set<String> visibleProjectIds, DashboardPeriod period, LocalDate today) {
        List<Project> active = projectRepository.findAllActiveByOrganizationId(orgId);
        List<Project> projects = active.stream().filter(p -> p.getId() != null && visibleProjectIds.contains(p.getId()))
                .sorted(Comparator.comparing(Project::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<User> users = userRepository.findAllByOrganizationId(orgId);
        Set<String> people = people(active, projects, users);
        Map<String, ProjectPeriodItems> items = items(orgId, projects);
        Vcs vcs = vcs(orgId, projects, period);
        Organization organization = organizationRepository.findById(orgId).orElse(null);
        Ai ai = ai(orgId, people, items, period);
        return new Snapshot(projects, users, people, items, vcs, organization, ai,
                connectorTokenRepository.findAllByOrganizationId(orgId),
                fluencyScoreRepository.findAllByOrganizationId(orgId),
                azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId),
                githubConnectionRepository.findByOrganizationIdAndActiveTrue(orgId),
                azureBindings(orgId, projects), syncLogs(orgId, projects, today));
    }

    /**
     * The members of the visible projects; every active user when the caller sees all the active
     * projects of the organization (so people in no project still count, as in the preview).
     */
    static Set<String> people(List<Project> active, List<Project> visible, List<User> users) {
        Set<String> activeIds = active.stream().map(Project::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> visibleIds = visible.stream().map(Project::getId).collect(Collectors.toSet());
        if (!activeIds.isEmpty() && visibleIds.equals(activeIds)) {
            return users.stream().filter(User::isActive).map(User::getId).filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        return visible.stream().filter(p -> p.getMemberIds() != null).flatMap(p -> p.getMemberIds().stream())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, ProjectPeriodItems> items(String orgId, List<Project> projects) {
        if (projects.isEmpty()) return Map.of();
        Map<String, List<Sprint>> sprints = sprintRepository.findAllByOrganizationId(orgId).stream()
                .filter(s -> s.getProjectId() != null).collect(Collectors.groupingBy(Sprint::getProjectId));
        Map<String, ProjectPeriodItems> byProject = new LinkedHashMap<>();
        for (Project project : projects) {
            byProject.put(project.getId(), ProjectPeriodItems.of(
                    workItemRepository.findAllByOrganizationIdAndProjectId(orgId, project.getId()),
                    sprints.getOrDefault(project.getId(), List.of()), effortResolver));
        }
        return byProject;
    }

    private Vcs vcs(String orgId, List<Project> projects, DashboardPeriod period) {
        Set<String> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        List<VcsRepoBinding> bindings = projectIds.isEmpty() ? List.of()
                : bindingRepository.findAllActiveByOrganizationId(orgId).stream()
                .filter(b -> b.getRepositoryId() != null && projectIds.contains(b.getProjectId())).toList();
        Set<String> repositoryIds = bindings.stream().map(VcsRepoBinding::getRepositoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (repositoryIds.isEmpty()) return new Vcs(bindings, List.of(), List.of(), List.of(), List.of());
        List<VcsRepository> repositories = repositoryRepository.findAllByOrganizationIdAndIdIn(orgId, repositoryIds);
        Instant from = period.previous().from();
        Instant to = period.current().to();
        List<VcsPullRequest> pullRequests = DeliveryMetricsService.union(List.of(
                vcsReadPort.pullRequestsCreatedIn(orgId, repositoryIds, from, to),
                vcsReadPort.mergedPullRequests(orgId, repositoryIds, from, to),
                vcsReadPort.openPullRequests(orgId, repositoryIds)), VcsPullRequest::getId).stream()
                .filter(pr -> pr.getId() != null && pr.isActive()).toList();
        List<VcsCommit> commits = commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(orgId,
                repositoryIds, from, to);
        List<MergedChange> changes = deliveryMetricsService.mergedChangesFrom(orgId, repositories,
                pullRequests.stream().filter(pr -> pr.getMergedAt() != null).toList());
        return new Vcs(bindings, repositories, pullRequests, commits, changes);
    }

    private Ai ai(String orgId, Set<String> people, Map<String, ProjectPeriodItems> items, DashboardPeriod period) {
        DayRange current = period.current();
        List<AIUsage> usage = people.isEmpty() ? List.of()
                : aiUsageRepository.findByOrganizationIdAndUserIdInAndDayBetween(orgId, people, period.previous().first(),
                current.last());
        Set<String> itemIds = items.values().stream().flatMap(i -> i.ids().stream()).collect(Collectors.toSet());
        List<AiUsageAllocation> allocations = itemIds.isEmpty() ? List.of()
                : allocationRepository.findValidatedByOrganizationIdAndWorkItemIdIn(orgId, itemIds).stream()
                .filter(a -> current.containsDay(a.getDay())).toList();
        Map<String, AIUsage> byId = usage.stream().filter(u -> u.getId() != null)
                .collect(Collectors.toMap(AIUsage::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Set<String> missing = allocations.stream().filter(a -> a.getAiUsageIds() != null)
                .flatMap(a -> a.getAiUsageIds().stream()).filter(id -> id != null && !byId.containsKey(id))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            aiUsageRepository.findByOrganizationIdAndIdIn(orgId, missing).stream().filter(u -> u.getId() != null)
                    .forEach(u -> byId.putIfAbsent(u.getId(), u));
        }
        return new Ai(usage, allocations, byId);
    }

    private List<AzureProjectBinding> azureBindings(String orgId, List<Project> projects) {
        if (projects.isEmpty()) return List.of();
        Set<String> ids = projects.stream().map(Project::getId).collect(Collectors.toSet());
        return azureBindingRepository.findAllByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(b -> ids.contains(b.getProjectId())).toList();
    }

    /** Logs of the visible projects and the organization-level ones ({@code projectId null}). */
    private List<IntegrationSyncLog> syncLogs(String orgId, List<Project> projects, LocalDate today) {
        Set<String> ids = projects.stream().map(Project::getId).collect(Collectors.toSet());
        Instant from = SyncRunsSummary.windowStart(today);
        return Stream.of(Integration.AZURE_DEVOPS, Integration.GITHUB)
                .flatMap(i -> syncLogRepository.findByOrganizationIdAndIntegrationAndOperationInAndStartedAtFrom(orgId, i,
                        SyncRunsSummary.LEAF_OPERATIONS, from).stream())
                .filter(l -> l.getProjectId() == null || ids.contains(l.getProjectId()))
                .toList();
    }

    /**
     * @param people ids of the people the AI and coverage blocks are about (see {@link #people})
     * @param items  work items and sprints of each visible project, by project id (name order)
     */
    public record Snapshot(List<Project> projects, List<User> users, Set<String> people,
                           Map<String, ProjectPeriodItems> items, Vcs vcs, Organization organization, Ai ai,
                           List<ConnectorToken> connectorTokens, List<FluencyScore> fluencyScores,
                           Optional<AzureConnection> azureConnection, Optional<GithubConnection> githubConnection,
                           List<AzureProjectBinding> azureBindings, List<IntegrationSyncLog> syncLogs) {}

    /**
     * Version control of the visible projects: their bindings, the repositories, the pull requests of
     * both periods plus the open ones, the commits of both periods, the merged changes (delivery
     * metrics).
     */
    public record Vcs(List<VcsRepoBinding> bindings, List<VcsRepository> repositories,
                      List<VcsPullRequest> pullRequests, List<VcsCommit> commits, List<MergedChange> changes) {

        public Set<String> repositoryIdsOf(String projectId) {
            return bindings.stream().filter(b -> projectId.equals(b.getProjectId()))
                    .map(VcsRepoBinding::getRepositoryId).collect(Collectors.toSet());
        }

        public static <T> List<T> ofRepositories(Collection<T> values, Function<T, String> repositoryOf,
                                                 Set<String> repositoryIds) {
            return values.stream().filter(v -> repositoryIds.contains(repositoryOf.apply(v))).toList();
        }
    }

    /**
     * {@code usage}: the people's records of both periods; {@code allocations}: validated
     * allocations of the current period splitting to visible work items; {@code usageById}: both,
     * plus the records those allocations cover.
     */
    public record Ai(List<AIUsage> usage, List<AiUsageAllocation> allocations, Map<String, AIUsage> usageById) {}
}
