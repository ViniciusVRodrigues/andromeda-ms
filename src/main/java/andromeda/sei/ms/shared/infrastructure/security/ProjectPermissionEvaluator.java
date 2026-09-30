package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.feature.workitem.domain.exception.SprintNotFoundException;
import andromeda.sei.ms.feature.workitem.domain.exception.WorkItemNotFoundException;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The {@code perm} bean of {@code @PreAuthorize} expressions (project-scoped-permissions D6):
 * {@code @PreAuthorize("@perm.onProject(#projectId, 'DASHBOARD_TEAM')")}.
 *
 * <p>Each method resolves the project of the resource <b>within the caller's organization</b> and
 * delegates to the caller's {@link andromeda.sei.ms.shared.domain.security.PermissionResolver}.
 * A resource of another organization (or missing) throws the resource's {@code *NotFoundException}:
 * SpEL rethrows runtime exceptions of a method call unwrapped, so it leaves the method-security
 * interceptor as-is and {@code GlobalExceptionHandler} answers {@code 404} — existence is not
 * leaked. A {@code false} answer becomes {@code 403}.
 *
 * <p>An ORG_ONLY code "on a project" means holding it at organization level with the project
 * accessible (member, {@code PROJECT_ACCESS_ALL} or a project-level grant).
 */
@Component("perm")
@RequiredArgsConstructor
public class ProjectPermissionEvaluator {

    private final EffectivePermissionService effectivePermissionService;
    private final CurrentUserPermissions currentUserPermissions;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final VcsRepositoryRepository vcsRepositoryRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final WorkItemRepository workItemRepository;

    /** {@code code} on the project; {@code 404} when the project is not active in the caller's org. */
    public boolean onProject(String projectId, String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || projectId == null) return false;
        projectRepository.findActiveByIdAndOrganizationId(projectId, caller.get().organizationId())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return caller.get().resolver().hasOnProject(code, projectId);
    }

    /**
     * The project is accessible to the caller (member, {@code PROJECT_ACCESS_ALL} or a project-level
     * grant), whatever the code: the {@code GET /v1/projects} criterion, for project metadata such
     * as the sprint selector. {@code 404} when the project is not active in the caller's org.
     */
    public boolean canAccessProject(String projectId) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || projectId == null) return false;
        projectRepository.findActiveByIdAndOrganizationId(projectId, caller.get().organizationId())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        return caller.get().resolver().isProjectAccessible(projectId);
    }

    /**
     * The work item's project is accessible to the caller, or the caller is its assignee;
     * {@code 404} for a work item of another org.
     */
    public boolean canAccessWorkItem(String workItemId) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || workItemId == null) return false;
        WorkItem item = workItemRepository.findByIdAndOrganizationIdAndActiveTrue(workItemId, caller.get().organizationId())
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        if (caller.get().userId().equals(item.getAssigneeUserId())) return true;
        return item.getProjectId() == null
                ? caller.get().resolver().hasProjectAccessAll()
                : caller.get().resolver().isProjectAccessible(item.getProjectId());
    }

    /**
     * {@code code} at organization level or on at least one active project: the gate of listings
     * that span projects, which then filter by {@code EffectivePermissionService.projectsWith}.
     */
    public boolean onAnyProject(String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty()) return false;
        return caller.get().resolver().hasOrg(code)
                || !effectivePermissionService.projectsWith(caller.get(), code).isEmpty();
    }

    /** {@code code} on the project of the sprint; {@code 404} for a sprint of another org. */
    public boolean onSprint(String sprintId, String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || sprintId == null) return false;
        Sprint sprint = sprintRepository.findByIdAndOrganizationIdAndActiveTrue(sprintId, caller.get().organizationId())
                .orElseThrow(() -> new SprintNotFoundException(sprintId));
        return onProjectOf(caller.get(), sprint.getProjectId(), code);
    }

    /**
     * {@code code} on the project the repository is bound to; {@code 404} for a repository of
     * another org. An unbound repository belongs to no project: only an organization-wide holder
     * ({@code PROJECT_ACCESS_ALL} plus the code) reads it.
     */
    public boolean onRepository(String repositoryId, String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || repositoryId == null) return false;
        String orgId = caller.get().organizationId();
        vcsRepositoryRepository.findByIdAndOrganizationId(repositoryId, orgId)
                .orElseThrow(() -> new RepoNotFoundException(repositoryId));
        String projectId = vcsRepoBindingRepository.findActiveByOrganizationIdAndRepositoryId(orgId, repositoryId)
                .map(VcsRepoBinding::getProjectId)
                .orElse(null);
        return onProjectOf(caller.get(), projectId, code);
    }

    /** {@code code} on the project of a repository or Azure binding; {@code 404} outside the org. */
    public boolean onBinding(String bindingId, String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || bindingId == null) return false;
        String orgId = caller.get().organizationId();
        String projectId = vcsRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .map(VcsRepoBinding::getProjectId)
                .or(() -> azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                        .map(AzureProjectBinding::getProjectId))
                .orElseThrow(() -> new RepoBindingNotFoundException(bindingId));
        return onProjectOf(caller.get(), projectId, code);
    }

    /**
     * {@code code} on the project of the work item; {@code 404} for a work item of another org. No
     * assignee shortcut: the code itself is required (it guards edits and team views).
     */
    public boolean onWorkItem(String workItemId, String code) {
        Optional<UserPermissionSnapshot> caller = caller();
        if (caller.isEmpty() || workItemId == null) return false;
        WorkItem item = workItemRepository.findByIdAndOrganizationIdAndActiveTrue(workItemId, caller.get().organizationId())
                .orElseThrow(() -> new WorkItemNotFoundException(workItemId));
        return onProjectOf(caller.get(), item.getProjectId(), code);
    }

    private boolean onProjectOf(UserPermissionSnapshot caller, String projectId, String code) {
        if (projectId == null) {
            return caller.resolver().hasProjectAccessAll() && caller.resolver().hasOrg(code);
        }
        return caller.resolver().hasOnProject(code, projectId);
    }

    private Optional<UserPermissionSnapshot> caller() {
        return currentUserPermissions.current();
    }
}
