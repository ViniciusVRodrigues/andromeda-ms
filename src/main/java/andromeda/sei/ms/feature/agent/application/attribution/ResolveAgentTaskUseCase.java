package andromeda.sei.ms.feature.agent.application.attribution;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;
import andromeda.sei.ms.feature.agent.domain.attribution.TaskRefParser;
import andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Server-side task resolution of an agent usage row (agente-endpoint-v2, design 5). The first step
 * that resolves wins: the row taskRef; the device active task; the branch matched against the
 * organization patterns; the single in-progress item of the user in the current sprint of the
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

        Optional<WorkItem> item = byTaskRef(q.organizationId(), q.userId(), q.taskRef());
        if (item.isPresent()) return outcome(item.get(), AttributionSource.TASK_REF, repositoryProjectId);

        if (activeTaskApplies(q)) {
            item = byTaskRef(q.organizationId(), q.userId(), q.activeTask().taskRef());
            if (item.isPresent()) return outcome(item.get(), AttributionSource.ACTIVE_TASK, repositoryProjectId);
        }

        item = byBranch(q);
        if (item.isPresent()) return outcome(item.get(), AttributionSource.BRANCH, repositoryProjectId);

        item = singleActive(q.organizationId(), q.userId(), repositoryProjectId);
        if (item.isPresent()) return outcome(item.get(), AttributionSource.SINGLE_ACTIVE, repositoryProjectId);

        return new AgentAttribution(null, repositoryProjectId, AttributionSource.NONE);
    }

    /** For the "andromeda task" command: resolves a typed reference with the same rules (source TASK_REF). */
    public Optional<ResolvedTask> resolveRef(String organizationId, String userId, String taskRef) {
        return byTaskRef(organizationId, userId, taskRef).map(i -> toResolved(i, AttributionSource.TASK_REF));
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
        ActiveTaskHint hint = q.activeTask();
        if (hint == null || hint.taskRef() == null || hint.since() == null || q.bucketStart() == null) return false;
        if (q.bucketStart().isBefore(hint.since())) return false;
        return hint.repository() == null || hint.repository().isBlank()
                || sameRepository(hint.repository(), q.repository());
    }

    private static boolean sameRepository(String a, String b) {
        if (b == null || b.isBlank()) return false;
        Optional<RemoteRepositoryRef> ra = RemoteRepositoryRef.parse(a);
        Optional<RemoteRepositoryRef> rb = RemoteRepositoryRef.parse(b);
        if (ra.isPresent() && rb.isPresent()) return ra.get().equals(rb.get());
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private Optional<WorkItem> byBranch(AgentTaskQuery q) {
        if (q.branch() == null || q.branch().isBlank()) return Optional.empty();
        List<String> patterns = organizationRepository.findById(q.organizationId())
                .map(Organization::getAgentSettings).map(s -> s.branchPatterns()).orElse(null);
        return BranchPatternMatcher.of(patterns).match(q.branch())
                .flatMap(group -> byTaskRef(q.organizationId(), q.userId(), group));
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
