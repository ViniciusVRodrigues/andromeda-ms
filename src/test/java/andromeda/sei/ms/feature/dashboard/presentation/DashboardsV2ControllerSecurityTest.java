package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetIndividualDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetTeamDashboardV2UseCase;
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
 * dashboards-customizaveis 1.1 — the {@code @PreAuthorize} of the v2 data endpoints really runs
 * through method security (the {@code MethodSecurityPermExpressionsTest} pattern; standalone MockMvc
 * never evaluates it): {@code false} from {@code @perm} is {@link AccessDeniedException} (403 by
 * {@code GlobalExceptionHandler}); a project of another organization propagates
 * {@link ProjectNotFoundException} unwrapped (404 {@code PROJECT_NOT_FOUND}); the use case is never
 * reached on a denial.
 */
class DashboardsV2ControllerSecurityTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final GetIndividualDashboardV2UseCase INDIVIDUAL = mock(GetIndividualDashboardV2UseCase.class);
    private static final GetTeamDashboardV2UseCase TEAM = mock(GetTeamDashboardV2UseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean DashboardsV2Controller dashboardsV2Controller() {
            return new DashboardsV2Controller(INDIVIDUAL, TEAM, new DashboardsV2Mapper(),
                    mock(DataFreshnessResolver.class));
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
        reset(PERM, INDIVIDUAL, TEAM);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_deny403_when_individualCodeIsNotHeldOnTheProject() {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        when(PERM.onProject("p1", "DASHBOARD_INDIVIDUAL")).thenReturn(false);

        assertThatThrownBy(() -> controller().individual("p1", null)).isInstanceOf(AccessDeniedException.class);
        verify(INDIVIDUAL, never()).execute(any(), any(), any(), any());
    }

    @Test
    void should_deny403_when_callerLacksTheIndividualAuthority_evenWithTheProjectCheckPassing() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject("p1", "DASHBOARD_INDIVIDUAL")).thenReturn(true);

        assertThatThrownBy(() -> controller().individual("p1", null)).isInstanceOf(AccessDeniedException.class);
        verify(INDIVIDUAL, never()).execute(any(), any(), any(), any());
    }

    @Test
    void should_answer404ProjectNotFound_when_individualProjectIsOfAnotherOrganization() {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        when(PERM.onProject("foreign", "DASHBOARD_INDIVIDUAL")).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> controller().individual("foreign", null))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_NOT_FOUND");
    }

    @Test
    void should_reachTheIndividualUseCase_when_authorityAndProjectCheckPass() {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        when(PERM.onProject("p1", "DASHBOARD_INDIVIDUAL")).thenReturn(true);

        // The mocked use case returns null, so the mapper may fail afterwards: only the gate matters.
        assertThat(catchThrowable(() -> controller().individual("p1", "s1"))).isNotInstanceOf(AccessDeniedException.class);
        verify(INDIVIDUAL).execute(any(), any(), any(), any());
    }

    @Test
    void should_deny403_when_teamCodeIsNotHeldOnTheProject() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(false);

        assertThatThrownBy(() -> controller().team("p1", null)).isInstanceOf(AccessDeniedException.class);
        verify(TEAM, never()).execute(any(), any(), any(), any());
    }

    @Test
    void should_answer404ProjectNotFound_when_teamProjectIsOfAnotherOrganization() {
        authenticateWith("DASHBOARD_TEAM");
        when(PERM.onProject("foreign", "DASHBOARD_TEAM")).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> controller().team("foreign", null)).isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void should_reachTheTeamUseCase_when_projectCheckPasses_withoutOrgAuthority() {
        // A project-level GRANT is enough: the team route has no org-level authority check.
        authenticateWith();
        when(PERM.onProject("p1", "DASHBOARD_TEAM")).thenReturn(true);

        assertThat(catchThrowable(() -> controller().team("p1", "s1"))).isNotInstanceOf(AccessDeniedException.class);
        verify(TEAM).execute(any(), any(), any(), any());
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-a", null, AuthorityUtils.createAuthorityList(authorities)));
    }

    private static DashboardsV2Controller controller() {
        return context.getBean(DashboardsV2Controller.class);
    }
}
