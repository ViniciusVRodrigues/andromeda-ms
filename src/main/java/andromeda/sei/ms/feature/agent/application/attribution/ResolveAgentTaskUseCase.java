package andromeda.sei.ms.feature.agent.application.attribution;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;
import andromeda.sei.ms.feature.agent.domain.attribution.TaskRefParser;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-side task resolution of an agent usage row (agente-endpoint-v2, design 5; the branch first since
 * atribuicao-a-prova-de-esquecimento, design 2.1). The first step that resolves wins: the branch matched
 * against the organization work item patterns (only an item of the user's sprint); the row taskRef; the
 * device active task (only on the day, repository and branch it was marked); the single in-progress item of the user in the current sprint of the
 * project bound to the row repository. A step that does not resolve (unknown or ambiguous
 * reference, project the user cannot access, no eligible item) falls through to the next.
 *
 * <p>A resolved item must be active, in a project the user can access, and eligible for AI
 * allocation; an ineligible one is replaced by its first eligible ancestor, up to
 * {@value #MAX_ANCESTOR_LEVELS} levels up the parentExternalId chain.
 */
@Component
@RequiredArgsConstructor
public class ResolveAgentTaskUseCase {

    static final int MAX_ANCESTOR_LEVELS = 3;

    private final WorkItemRepository workItemRepository;
    private final SprintRepository sprintRepository;
    private final OrganizationRepository organizationRepository;
    private final AllocationTypeEligibility typeEligibility;
    private final EffectivePermissionService permissionService;
    private final RemoteRepositoryResolver remoteRepositoryResolver;

    public AgentAttribution execute(AgentTaskQuery q) {
        String repositoryProjectId = remoteRepositoryResolver
                .resolveProjectId(q.organizationId(), q.repository()).orElse(null);

        Optional<WorkItem> item = byBranch(q);
        if (item.isPresent()) {
            AgentAttribution branch = outcome(item.get(), AttributionSource.BRANCH, repositoryProjectId);
            String overridden = overriddenBy(q, item.get());
            return overridden == null ? branch : new AgentAttribution(
                    branch.resolvedTask().withOverridden(overridden), repositoryProjectId, AttributionSource.BRANCH);
        }

        item = byTaskRef(q.organizationId(), q.userId(), q.taskRef());
        if (item.isPresent()) return outcome(item.get(), AttributionSource.TASK_REF, repositoryProjectId);

        if (activeTaskApplies(q)) {
            item = byTaskRef(q.organizationId(), q.userId(), q.activeTask().taskRef());
            if (item.isPresent()) return outcome(item.get(), AttributionSource.ACTIVE_TASK, repositoryProjectId);
        }

        item = singleActive(q.organizationId(), q.userId(), repositoryProjectId);
        if (item.isPresent()) return outcome(item.get(), AttributionSource.SINGLE_ACTIVE, repositoryProjectId);

        return new AgentAttribution(null, repositoryProjectId, AttributionSource.NONE);
    }

    /** For the "andromeda task" command: resolves a typed reference with the same rules (source TASK_REF). */
    public Optional<ResolvedTask> resolveRef(String organizationId, String userId, String taskRef) {
        return byTaskRef(organizationId, userId, taskRef).map(i -> toResolved(i, AttributionSource.TASK_REF));
    }

    /**
     * For moving agent usage by hand: the chosen item itself, when it is active, in a project the user
     * can open and eligible for AI allocation. No ancestor is substituted: the person picked this item.
     */
    public Optional<ResolvedTask> resolveItem(String organizationId, String userId, String workItemId) {
        if (workItemId == null || workItemId.isBlank()) return Optional.empty();
        return workItemRepository.findByIdAndOrganizationIdAndActiveTrue(workItemId, organizationId)
                .filter(i -> canAccess(organizationId, userId, i.getProjectId()))
                .filter(i -> typeEligibility.isEligible(organizationId, i))
                .map(i -> toResolved(i, AttributionSource.TASK_REF));
    }

    /** The ref the branch beat: the row taskRef, else the valid active task, when they point to another item. */
    private String overriddenBy(AgentTaskQuery q, WorkItem winner) {
        Optional<WorkItem> other = byTaskRef(q.organizationId(), q.userId(), q.taskRef());
        if (other.isEmpty() && activeTaskApplies(q)) {
            other = byTaskRef(q.organizationId(), q.userId(), q.activeTask().taskRef());
        }
        return other.filter(o -> !Objects.equals(o.getId(), winner.getId())).map(ResolveAgentTaskUseCase::refOf).orElse(null);
    }

    private AgentAttribution outcome(WorkItem item, AttributionSource source, String repositoryProjectId) {
        return new AgentAttribution(toResolved(item, source), repositoryProjectId, source);
    }

    private ResolvedTask toResolved(WorkItem item, AttributionSource source) {
        return new ResolvedTask(item.getId(), item.getProjectId(), item.getTitle(), refOf(item), source);
    }

    static String refOf(WorkItem item) {
        return item.getSource() == WorkItem.Source.JIRA ? item.getExternalId() : "AB#" + item.getExternalId();
    }

    private boolean activeTaskApplies(AgentTaskQuery q) {
        return activeTaskApplies(q.activeTask(), q.bucketStart(), q.repository(), q.branch(), q.day());
    }

    /**
     * Whether the active task of the device stands for a row of this hour and repository. It is the only
     * step of {@link #execute} that depends on the hour, so the ingest memoizes the rest per batch (agt9).
     */
    public static boolean activeTaskApplies(ActiveTaskHint hint, Instant bucketStart, String repository,
                                            String branch, LocalDate day) {
        if (hint == null || hint.taskRef() == null || hint.since() == null || bucketStart == null) return false;
        if (bucketStart.isBefore(hint.since())) return false;
        if (hint.day() != null && day != null && !hint.day().equals(day)) return false;
        if (hint.branch() != null && !hint.branch().isBlank() && branch != null && !branch.isBlank()
                && !hint.branch().trim().equals(branch.trim())) return false;
        return hint.repository() == null || hint.repository().isBlank()
                || sameRepository(hint.repository(), repository);
    }

    private static boolean sameRepository(String a, String b) {
        if (b == null || b.isBlank()) return false;
        Optional<RemoteRepositoryRef> ra = RemoteRepositoryRef.parse(a);
        Optional<RemoteRepositoryRef> rb = RemoteRepositoryRef.parse(b);
        if (ra.isPresent() && rb.isPresent()) return ra.get().equals(rb.get());
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Branch step (design 5.3). Off when the organization disabled the patterns. A number taken
     * from a branch is not explicit, so the item must be the user's own: in the current sprint of
     * its project or assigned to the user ({@code 2026-ajustes} must not grab an old item 2026).
     */
    private Optional<WorkItem> byBranch(AgentTaskQuery q) {
        if (q.branch() == null || q.branch().isBlank()) return Optional.empty();
        WorkItemPatterns patterns = organizationRepository.findById(q.organizationId())
                .map(Organization::getWorkItemPatterns).orElseGet(WorkItemPatterns::defaults);
        if (!patterns.enabled()) return Optional.empty();
        return BranchPatternMatcher.of(patterns.regexes()).match(q.branch())
                .flatMap(group -> TaskRefParser.parse(group))
                .flatMap(externalId -> {
                    List<WorkItem> found = workItemRepository
                            .findAllActiveByOrganizationIdAndExternalId(q.organizationId(), externalId);
                    if (found.size() != 1) return Optional.<WorkItem>empty();
                    WorkItem item = found.get(0);
                    if (!item.isActive() || !belongsToUserSprint(q.organizationId(), q.userId(), item)) {
                        return Optional.<WorkItem>empty();
                    }
                    return usable(q.organizationId(), q.userId(), item);
                });
    }

    private boolean belongsToUserSprint(String organizationId, String userId, WorkItem item) {
        if (userId != null && userId.equals(item.getAssigneeUserId())) return true;
        if (item.getSprintId() == null || item.getProjectId() == null) return false;
        return sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(organizationId, item.getProjectId())
                .map(sprint -> item.getSprintId().equals(sprint.getId()))
                .orElse(false);
    }

    private Optional<WorkItem> byTaskRef(String organizationId, String userId, String taskRef) {
        Optional<String> externalId = TaskRefParser.parse(taskRef);
        if (externalId.isEmpty()) return Optional.empty();
        List<WorkItem> found = workItemRepository.findAllActiveByOrganizationIdAndExternalId(organizationId, externalId.get());
        if (found.size() != 1) return Optional.empty();
        return usable(organizationId, userId, found.get(0));
    }

    private Optional<WorkItem> singleActive(String organizationId, String userId, String projectId) {
        if (projectId == null) return Optional.empty();
        return sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(organizationId, projectId)
                .flatMap(sprint -> {
                    List<WorkItem> inProgress = workItemRepository
                            .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                                    organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS)
                            .stream()
                            .filter(WorkItem::isActive)
                            .filter(i -> projectId.equals(i.getProjectId()))
                            .toList();
                    return inProgress.size() == 1
                            ? usable(organizationId, userId, inProgress.get(0))
                            : Optional.<WorkItem>empty();
                });
    }

    /** The item itself or its first eligible ancestor (up to 3 levels), all in projects the user can open. */
    private Optional<WorkItem> usable(String organizationId, String userId, WorkItem start) {
        WorkItem current = start;
        for (int level = 0; level <= MAX_ANCESTOR_LEVELS; level++) {
            if (!current.isActive() || !canAccess(organizationId, userId, current.getProjectId())) return Optional.empty();
            if (typeEligibility.isEligible(organizationId, current)) return Optional.of(current);
            String parent = current.getParentExternalId();
            if (parent == null || parent.isBlank()) return Optional.empty();
            Optional<WorkItem> next = workItemRepository
                    .findByOrganizationIdAndExternalIdAndSource(organizationId, parent, current.getSource());
            if (next.isEmpty()) return Optional.empty();
            current = next.get();
        }
        return Optional.empty();
    }

    private boolean canAccess(String organizationId, String userId, String projectId) {
        return permissionService.snapshot(organizationId, userId)
                .map(s -> projectId == null
                        ? s.resolver().hasProjectAccessAll()
                        : s.resolver().isProjectAccessible(projectId))
                .orElse(false);
    }
}
