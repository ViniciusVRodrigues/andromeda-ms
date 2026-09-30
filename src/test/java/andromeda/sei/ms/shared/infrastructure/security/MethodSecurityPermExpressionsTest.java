package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardUseCase;
import andromeda.sei.ms.feature.dashboard.presentation.TeamDashboardController;
import andromeda.sei.ms.feature.dashboard.presentation.TeamDashboardMapper;
import andromeda.sei.ms.feature.integration.application.usecase.GetIntegrationSyncLogsUseCase;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.presentation.IntegrationSyncLogController;
import andromeda.sei.ms.feature.project.application.usecase.GetProjectRefreshStatusUseCase;
import andromeda.sei.ms.feature.project.application.usecase.RefreshProjectUseCase;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.project.presentation.ProjectController;
import andromeda.sei.ms.feature.project.presentation.ProjectRefreshController;
import andromeda.sei.ms.feature.project.application.usecase.CreateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.DeactivateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.ListProjectsUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectMembersUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectUseCase;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.presentation.dto.ProjectRequest;
import andromeda.sei.ms.feature.workitem.application.usecase.GetProjectSprintsUseCase;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.feature.vcs.application.usecase.GetPullRequestCommitsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryCommitActivityUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryDeliveryMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryPullRequestsUseCase;
import andromeda.sei.ms.feature.vcs.presentation.VcsRepositoryReadController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 6.x — the {@code @perm} expressions really run through method
 * security (standalone MockMvc never evaluates {@code @PreAuthorize}): a {@code false} answer is
 * {@code AccessDeniedException} (403), and a {@code *NotFoundException} thrown by the evaluator
 * leaves the interceptor unwrapped, so {@code GlobalExceptionHandler} answers 404.
 */
class MethodSecurityPermExpressionsTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final GetTeamDashboardUseCase TEAM = mock(GetTeamDashboardUseCase.class);
    private static final RefreshProjectUseCase REFRESH = mock(RefreshProjectUseCase.class);
    private static final ListRepositoryPullRequestsUseCase PULL_REQUESTS = mock(ListRepositoryPullRequestsUseCase.class);
    private static final GetIntegrationSyncLogsUseCase SYNC_LOGS = mock(GetIntegrationSyncLogsUseCase.class);
    private static final CurrentUserPermissions CURRENT = mock(CurrentUserPermissions.class);
    private static final CreateProjectUseCase CREATE_PROJECT = mock(CreateProjectUseCase.class);
    private static final UpdateProjectUseCase UPDATE_PROJECT = mock(UpdateProjectUseCase.class);
    private static final DeactivateProjectUseCase DEACTIVATE_PROJECT = mock(DeactivateProjectUseCase.class);
    private static final UpdateProjectMembersUseCase PROJECT_MEMBERS = mock(UpdateProjectMembersUseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean TeamDashboardController teamDashboardController() {
            return new TeamDashboardController(TEAM, new TeamDashboardMapper(), mock(DataFreshnessResolver.class));
        }

        @Bean ProjectRefreshController projectRefreshController() {
            return new ProjectRefreshController(REFRESH, mock(GetProjectRefreshStatusUseCase.class));
        }

        @Bean VcsRepositoryReadController vcsRepositoryReadController() {
            return new VcsRepositoryReadController(mock(ListProjectRepositoriesUseCase.class), PULL_REQUESTS,
                    mock(GetRepositoryDeliveryMetricsUseCase.class), mock(GetRepositoryCommitActivityUseCase.class),
                    mock(GetPullRequestCommitsUseCase.class), mock(DataFreshnessResolver.class));
        }

        @Bean ProjectController projectController() {
            return new ProjectController(mock(ListProjectsUseCase.class), CREATE_PROJECT, UPDATE_PROJECT,
                    DEACTIVATE_PROJECT, PROJECT_MEMBERS, mock(GetProjectSprintsUseCase.class), CURRENT);
        }

        @Bean IntegrationSyncLogController integrationSyncLogController() {
            return new IntegrationSyncLogController(SYNC_LOGS, CURRENT);
        }
    }

    @BeforeAll
    static void startContext() {
        context = new AnnotationConfigApplicationContext(Config.class);
    }

    @AfterAll
    static void stopContext() {
        context.close();
    }

    @BeforeEach
    void authenticate() {
        reset(PERM, TEAM, REFRESH, PULL_REQUESTS, SYNC_LOGS, CREATE_PROJECT, UPDATE_PROJECT, DEACTIVATE_PROJECT,
                PROJECT_MEMBERS);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-1", null, AuthorityUtils.createAuthorityList("DASHBOARD_TEAM")));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_deny_when_teamDashboardProjectIsNotAllowed() {
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(false);

        assertThatThrownBy(() -> team().getTeamDashboard("p1", null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_invokeUseCase_when_teamDashboardProjectIsAllowed() {
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(true);

        // The mocked use case returns null, so the mapper may fail afterwards: only the gate matters.
        assertThat(catchThrowable(() -> team().getTeamDashboard("p1", null, null, null)))
                .isNotInstanceOf(AccessDeniedException.class);
        verify(TEAM).execute(any(), any(), any(), any(), any(), any());
    }

    @Test
    void should_useAnyProjectGate_when_teamDashboardHasNoProject() {
        when(PERM.onAnyProject("DASHBOARD_TEAM")).thenReturn(false);

        assertThatThrownBy(() -> team().getTeamDashboard(null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        verify(PERM).onAnyProject("DASHBOARD_TEAM");
    }

    @Test
    void should_propagateNotFound_unwrapped_when_projectIsOfAnotherOrganization() {
        when(PERM.onProject("foreign", "DASHBOARD_TEAM")).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> team().getTeamDashboard("foreign", null, null, null))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_allowRefresh_withEitherAzureOrReposReadOnProject() {
        when(PERM.onProject("p1", "AZURE_READ")).thenReturn(false);
        when(PERM.onProject("p1", "REPOS_READ")).thenReturn(true);

        assertThatCode(() -> context.getBean(ProjectRefreshController.class).refresh("p1")).doesNotThrowAnyException();
    }

    @Test
    void should_checkRepositoryProject_onPullRequests() {
        when(PERM.onRepository("r1", "REPOS_READ")).thenReturn(false);

        assertThatThrownBy(() -> context.getBean(VcsRepositoryReadController.class)
                .listPullRequests("r1", null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_useIntegrationReadCode_onSyncLogs() {
        when(PERM.onProject("p1", "AZURE_READ")).thenReturn(false);
        when(PERM.onAnyProject("REPOS_READ")).thenReturn(true);
        IntegrationSyncLogController controller = context.getBean(IntegrationSyncLogController.class);

        assertThatThrownBy(() -> controller.getSyncLogs(Integration.AZURE_DEVOPS, "p1"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> controller.getSyncLogs(Integration.GITHUB, null)).doesNotThrowAnyException();
    }

    @Test
    void should_denyProjectMutations_when_callerLacksProjectManage() {
        authenticateWith("USER_MANAGE", "PROJECT_ACCESS_ALL");
        ProjectController controller = context.getBean(ProjectController.class);
        ProjectRequest request = new ProjectRequest("Orion", "Core");

        assertThatThrownBy(() -> controller.create(request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.update("p1", request)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.deactivate("p1")).isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(CREATE_PROJECT, UPDATE_PROJECT, DEACTIVATE_PROJECT);
    }

    @Test
    void should_allowProjectMutations_when_callerHoldsProjectManage() {
        authenticateWith("PROJECT_MANAGE");
        ProjectController controller = context.getBean(ProjectController.class);
        ProjectRequest request = new ProjectRequest("Orion", "Core");
        Project project = new Project("p1", "org-1", "Orion", "orion", "Core", true, java.util.List.of(), 0L, null, null);
        when(CREATE_PROJECT.execute("Orion", "Core")).thenReturn(project);
        when(UPDATE_PROJECT.execute("p1", "Orion", "Core")).thenReturn(project);

        assertThatCode(() -> controller.create(request)).doesNotThrowAnyException();
        assertThatCode(() -> controller.update("p1", request)).doesNotThrowAnyException();
        assertThatCode(() -> controller.deactivate("p1")).doesNotThrowAnyException();
        verify(DEACTIVATE_PROJECT).execute("p1");
    }

    @Test
    void should_answerNotFound_when_updatingProjectOfAnotherOrganization() {
        authenticateWith("PROJECT_MANAGE");
        when(UPDATE_PROJECT.execute(any(), any(), any())).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> context.getBean(ProjectController.class)
                .update("foreign", new ProjectRequest("Orion", "Core")))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_keepUserManage_onProjectMembers() {
        authenticateWith("PROJECT_MANAGE");
        ProjectController controller = context.getBean(ProjectController.class);
        var request = new andromeda.sei.ms.feature.project.presentation.dto.UpdateProjectMembersRequest(java.util.List.of());

        assertThatThrownBy(() -> controller.updateMembers("p1", request)).isInstanceOf(AccessDeniedException.class);
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-1", null, AuthorityUtils.createAuthorityList(authorities)));
    }

    private static TeamDashboardController team() {
        return context.getBean(TeamDashboardController.class);
    }
}
