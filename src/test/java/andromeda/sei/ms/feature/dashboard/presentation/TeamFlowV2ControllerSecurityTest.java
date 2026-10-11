package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamFlowV2UseCase;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.infrastructure.security.ProjectPermissionEvaluator;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.13 — the {@code @PreAuthorize} of {@code /team/flow} really runs through
 * method security (standalone MockMvc never evaluates it): {@code false} from {@code @perm} is
 * {@link AccessDeniedException} (403); a project of another organization propagates
 * {@link ProjectNotFoundException} (404); the use case is never reached on a denial.
 */
class TeamFlowV2ControllerSecurityTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final GetTeamFlowV2UseCase USE_CASE = mock(GetTeamFlowV2UseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean TeamFlowV2Controller teamFlowV2Controller() {
            return new TeamFlowV2Controller(USE_CASE, new TeamFlowV2Mapper(), mock(DataFreshnessResolver.class));
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
    void resetMocks() {
        reset(PERM, USE_CASE);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_deny403_when_teamCodeIsNotHeldOnTheProject() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(false);

        assertThatThrownBy(() -> controller().flow("p1", null)).isInstanceOf(AccessDeniedException.class);
        verify(USE_CASE, never()).execute(any(), any(), any());
    }

    @Test
    void should_deny403_when_projectIdIsMissing() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject(null, "DASHBOARD_TEAM")).thenReturn(false);

        assertThatThrownBy(() -> controller().flow(null, null)).isInstanceOf(AccessDeniedException.class);
        verify(USE_CASE, never()).execute(any(), any(), any());
    }

    @Test
    void should_answer404ProjectNotFound_when_projectIsOfAnotherOrganization() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject("foreign", "DASHBOARD_TEAM")).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> controller().flow("foreign", null))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_NOT_FOUND");
        verify(USE_CASE, never()).execute(any(), any(), any());
    }

    @Test
    void should_reachTheUseCase_when_projectCheckPasses_withoutOrgAuthority() {
        // A project-level grant is enough: the route has no org-level authority check.
        authenticateWith();
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(true);

        // The mocked use case returns null, so the mapper may fail afterwards: only the gate matters.
        assertThat(catchThrowable(() -> controller().flow("p1", "s1"))).isNotInstanceOf(AccessDeniedException.class);
        verify(USE_CASE).execute(any(), any(), any());
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-a", null, AuthorityUtils.createAuthorityList(authorities)));
    }

    private static TeamFlowV2Controller controller() {
        return context.getBean(TeamFlowV2Controller.class);
    }
}
