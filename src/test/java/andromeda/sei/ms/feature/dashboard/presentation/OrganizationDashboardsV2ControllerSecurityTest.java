package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetIntegrationsDashboardV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetOrganizationDashboardV2UseCase;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.ProjectPermissionEvaluator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.11/1.12 — the {@code @PreAuthorize} of {@code /organization} and
 * {@code /integrations} really runs through method security (plain context with
 * {@code @EnableMethodSecurity} and a mocked {@code perm}; standalone MockMvc never evaluates it).
 * A denial is {@link AccessDeniedException} (403) and the use case is never reached.
 */
class OrganizationDashboardsV2ControllerSecurityTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final GetOrganizationDashboardV2UseCase ORGANIZATION = mock(GetOrganizationDashboardV2UseCase.class);
    private static final GetIntegrationsDashboardV2UseCase INTEGRATIONS = mock(GetIntegrationsDashboardV2UseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean OrganizationDashboardsV2Controller organizationDashboardsV2Controller() {
            return new OrganizationDashboardsV2Controller(ORGANIZATION, INTEGRATIONS, new OrganizationDashboardsV2Mapper(),
                    mock(DataFreshnessResolver.class), mock(CurrentUserPermissions.class),
                    mock(EffectivePermissionService.class));
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
        reset(PERM, ORGANIZATION, INTEGRATIONS);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_deny403_when_strategicIsHeldOnNoProject() {
        authenticateWith("DASHBOARD_STRATEGIC");
        when(PERM.onAnyProject("DASHBOARD_STRATEGIC")).thenReturn(false);

        assertThatThrownBy(() -> controller().organization(null)).isInstanceOf(AccessDeniedException.class);
        verify(ORGANIZATION, never()).execute(any(), any(), anyInt());
    }

    @Test
    void should_reachTheOrganizationUseCase_when_strategicIsHeldOnAnyProject() {
        // a project-level GRANT is enough: no organization-level authority is required
        authenticateWith();
        when(PERM.onAnyProject("DASHBOARD_STRATEGIC")).thenReturn(true);

        // the mocked use case returns null, so the code after it may fail: only the gate matters
        assertThat(catchThrowable(() -> controller().organization("90"))).isNotInstanceOf(AccessDeniedException.class);
        verify(ORGANIZATION).execute(any(), any(), anyInt());
    }

    @Test
    void should_deny403_onIntegrations_withoutAnyOfTheThreeAuthorities() {
        authenticateWith("DASHBOARD_STRATEGIC", "AZURE_READ", "REPOS_READ", "ROLE_MANAGE");
        when(PERM.onAnyProject(any())).thenReturn(true);

        assertThatThrownBy(() -> controller().integrations()).isInstanceOf(AccessDeniedException.class);
        verify(INTEGRATIONS, never()).execute(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AZURE_CONFIGURE", "REPOS_CONFIGURE", "USER_MANAGE"})
    void should_reachTheIntegrationsUseCase_withAnyOfTheThreeAuthorities(String authority) {
        authenticateWith(authority);

        assertThat(catchThrowable(() -> controller().integrations())).isNotInstanceOf(AccessDeniedException.class);
        verify(INTEGRATIONS).execute(any());
    }

    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-a", null, AuthorityUtils.createAuthorityList(authorities)));
    }

    private static OrganizationDashboardsV2Controller controller() {
        return context.getBean(OrganizationDashboardsV2Controller.class);
    }
}
