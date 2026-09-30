package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
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
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 4.4 — the {@code perm} evaluator: resolves the project of the resource
 * within the caller's organization, {@code false} (403) when the code is not held there, a
 * {@code *NotFoundException} (404) for a resource of another organization.
 */
@ExtendWith(MockitoExtension.class)
class ProjectPermissionEvaluatorTest {

    private static final String ORG = "org-1";
    private static final String P = "project-p";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private EffectivePermissionService effectivePermissionService;
    @Mock private CurrentUserPermissions currentUserPermissions;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private VcsRepositoryRepository vcsRepositoryRepository;
    @Mock private VcsRepoBindingRepository vcsRepoBindingRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private WorkItemRepository workItemRepository;

    private ProjectPermissionEvaluator evaluator(UserPermissionSnapshot caller) {
        when(currentUserPermissions.current()).thenReturn(Optional.ofNullable(caller));
        return new ProjectPermissionEvaluator(effectivePermissionService, currentUserPermissions, projectRepository,
                sprintRepository, vcsRepositoryRepository, vcsRepoBindingRepository, azureProjectBindingRepository,
                workItemRepository);
    }

    @Test
    void should_allowMember_and_denyNonMember_onProject() {
        givenProject(P);

        assertThat(evaluator(techLead(Set.of(P))).onProject(P, "DASHBOARD_TEAM")).isTrue();
        assertThat(evaluator(techLead(Set.of())).onProject(P, "DASHBOARD_TEAM")).isFalse();
    }

    @Test
    void should_throwNotFound_when_projectIsOfAnotherOrganization() {
        when(projectRepository.findActiveByIdAndOrganizationId("foreign", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> evaluator(techLead(Set.of())).onProject("foreign", "DASHBOARD_TEAM"))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_denyEverything_when_unauthenticated() {
        assertThat(evaluator(null).onProject(P, "DASHBOARD_TEAM")).isFalse();
        assertThat(evaluator(null).onAnyProject("DASHBOARD_TEAM")).isFalse();
    }

    @Test
    void should_resolveProjectOfSprint() {
        Sprint sprint = mock(Sprint.class);
        when(sprint.getProjectId()).thenReturn(P);
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s1", ORG)).thenReturn(Optional.of(sprint));
        when(sprintRepository.findByIdAndOrganizationIdAndActiveTrue("s2", ORG)).thenReturn(Optional.empty());
        ProjectPermissionEvaluator perm = evaluator(techLead(Set.of()));

        assertThat(perm.onSprint("s1", "DASHBOARD_TEAM")).isFalse();
        assertThatThrownBy(() -> perm.onSprint("s2", "DASHBOARD_TEAM")).isInstanceOf(SprintNotFoundException.class);
    }

    @Test
    void should_resolveProjectOfBoundRepository_and_404ForeignRepository() {
        when(vcsRepositoryRepository.findByIdAndOrganizationId("r1", ORG)).thenReturn(Optional.of(mock(VcsRepository.class)));
        when(vcsRepoBindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "r1"))
                .thenReturn(Optional.of(new VcsRepoBinding("b1", ORG, P, "r1", VcsProvider.GITHUB, true)));
        when(vcsRepositoryRepository.findByIdAndOrganizationId("foreign", ORG)).thenReturn(Optional.empty());

        assertThat(evaluator(developer(Set.of(P), "REPOS_READ")).onRepository("r1", "REPOS_READ")).isTrue();
        assertThat(evaluator(developer(Set.of(), "REPOS_READ")).onRepository("r1", "REPOS_READ")).isFalse();
        assertThatThrownBy(() -> evaluator(developer(Set.of(P), "REPOS_READ")).onRepository("foreign", "REPOS_READ"))
                .isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void should_allowUnboundRepository_onlyToOrganizationWideHolder() {
        when(vcsRepositoryRepository.findByIdAndOrganizationId("r1", ORG)).thenReturn(Optional.of(mock(VcsRepository.class)));
        when(vcsRepoBindingRepository.findActiveByOrganizationIdAndRepositoryId(ORG, "r1")).thenReturn(Optional.empty());

        assertThat(evaluator(developer(Set.of(P), "REPOS_READ")).onRepository("r1", "REPOS_READ")).isFalse();
        assertThat(evaluator(developer(Set.of(), "REPOS_READ", "PROJECT_ACCESS_ALL")).onRepository("r1", "REPOS_READ"))
                .isTrue();
    }

    @Test
    void should_resolveAzureBinding_and_404UnknownBinding() {
        AzureProjectBinding binding = mock(AzureProjectBinding.class);
        when(binding.getProjectId()).thenReturn(P);
        when(vcsRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue("az-1", ORG)).thenReturn(Optional.empty());
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("az-1", ORG)).thenReturn(Optional.of(binding));
        when(vcsRepoBindingRepository.findByIdAndOrganizationIdAndActiveTrue("nope", ORG)).thenReturn(Optional.empty());
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("nope", ORG)).thenReturn(Optional.empty());

        assertThat(evaluator(developer(Set.of(P), "AZURE_READ")).onBinding("az-1", "AZURE_READ")).isTrue();
        assertThatThrownBy(() -> evaluator(developer(Set.of(P), "AZURE_READ")).onBinding("nope", "AZURE_READ"))
                .isInstanceOf(RepoBindingNotFoundException.class);
    }

    @Test
    void should_checkWorkItemProject_withoutAssigneeShortcut_forCodes() {
        WorkItem item = mock(WorkItem.class);
        when(item.getProjectId()).thenReturn(P);
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(item));
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("foreign", ORG)).thenReturn(Optional.empty());

        assertThat(evaluator(developer(Set.of(), "WORK_ITEM_MANAGE")).onWorkItem("wi-1", "WORK_ITEM_MANAGE")).isFalse();
        assertThat(evaluator(developer(Set.of(P), "WORK_ITEM_MANAGE")).onWorkItem("wi-1", "WORK_ITEM_MANAGE")).isTrue();
        assertThatThrownBy(() -> evaluator(developer(Set.of(P))).onWorkItem("foreign", "DASHBOARD_TEAM"))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void should_letAssigneeReadOwnWorkItem_evenOutsideAccessibleProjects() {
        WorkItem item = mock(WorkItem.class);
        when(item.getAssigneeUserId()).thenReturn("user-1");
        when(workItemRepository.findByIdAndOrganizationIdAndActiveTrue("wi-1", ORG)).thenReturn(Optional.of(item));

        assertThat(evaluator(developer(Set.of())).canAccessWorkItem("wi-1")).isTrue();
    }

    @Test
    void should_acceptProjectGrantee_onAnyProject_withoutOrgCode() {
        UserPermissionSnapshot granted = new UserPermissionSnapshot(ORG, "user-1", "r", "DEVELOPER", List.of(),
                List.of(new PermissionOverride("DASHBOARD_STRATEGIC", PermissionEffect.GRANT, P, "admin", T)), Set.of(), 1L);
        when(effectivePermissionService.projectsWith(granted, "DASHBOARD_STRATEGIC"))
                .thenReturn(List.of(new Project(P, ORG, "P", "p", null, true, List.of(), 1L, T, T)));

        assertThat(evaluator(granted).onAnyProject("DASHBOARD_STRATEGIC")).isTrue();
    }

    @Test
    void should_checkAccessibility_forProjectMetadata() {
        givenProject(P);

        assertThat(evaluator(developer(Set.of(P))).canAccessProject(P)).isTrue();
        assertThat(evaluator(developer(Set.of())).canAccessProject(P)).isFalse();
    }

    private void givenProject(String id) {
        when(projectRepository.findActiveByIdAndOrganizationId(id, ORG))
                .thenReturn(Optional.of(new Project(id, ORG, id, id, null, true, List.of(), 1L, T, T)));
    }

    private static UserPermissionSnapshot techLead(Set<String> members) {
        return developer(members, "DASHBOARD_TEAM", "REPOS_READ");
    }

    private static UserPermissionSnapshot developer(Set<String> members, String... codes) {
        return new UserPermissionSnapshot(ORG, "user-1", "r", "ROLE", List.of(codes), List.of(), members, 1L);
    }
}
