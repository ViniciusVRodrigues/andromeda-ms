package andromeda.sei.ms.feature.dashboard.presentation;

import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectOverviewV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectQualityV2UseCase;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetProjectRoadmapV2UseCase;
import andromeda.sei.ms.feature.dashboard.domain.exception.DashboardPeriodInvalidException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.sync.application.DataFreshnessResolver;
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

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.10 — the {@code @PreAuthorize} of the three project endpoints runs
 * through real method security ({@code DashboardsV2ControllerSecurityTest} pattern): {@code false}
 * from {@code @perm.onProject(#projectId, 'DASHBOARD_STRATEGIC')} is 403; a project of another
 * organization propagates {@link ProjectNotFoundException} unwrapped (404); an invalid period is 422
 * only for a caller who may read the project (authorization first), and never reaches a use case.
 */
class ProjectDashboardsV2ControllerSecurityTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final GetProjectOverviewV2UseCase OVERVIEW = mock(GetProjectOverviewV2UseCase.class);
    private static final GetProjectRoadmapV2UseCase ROADMAP = mock(GetProjectRoadmapV2UseCase.class);
    private static final GetProjectQualityV2UseCase QUALITY = mock(GetProjectQualityV2UseCase.class);

    private static AnnotationConfigApplicationContext context;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean ProjectDashboardsV2Controller projectDashboardsV2Controller() {
            return new ProjectDashboardsV2Controller(OVERVIEW, ROADMAP, QUALITY, new ProjectDashboardsV2Mapper(),
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
    void setUp() {
        reset(PERM, OVERVIEW, ROADMAP, QUALITY);
        // Org-level DASHBOARD_STRATEGIC alone must not open a project: only @perm decides.
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "user-a", null, AuthorityUtils.createAuthorityList("DASHBOARD_STRATEGIC")));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"overview", "roadmap", "quality"})
    void should_deny403_when_strategicCodeIsNotHeldOnTheProject(String view) {
        when(PERM.onProject("p1", "DASHBOARD_STRATEGIC")).thenReturn(false);

        assertThatThrownBy(() -> call(view, "p1", "90")).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(OVERVIEW, ROADMAP, QUALITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"overview", "roadmap", "quality"})
    void should_answer404ProjectNotFound_when_projectIsOfAnotherOrganization(String view) {
        when(PERM.onProject("foreign", "DASHBOARD_STRATEGIC")).thenThrow(new ProjectNotFoundException("foreign"));

        assertThatThrownBy(() -> call(view, "foreign", "90"))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "PROJECT_NOT_FOUND");
        verifyNoInteractions(OVERVIEW, ROADMAP, QUALITY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"overview", "roadmap", "quality"})
    void should_answer422_forAnInvalidPeriod_onlyAfterTheProjectCheckPasses(String view) {
        when(PERM.onProject("p1", "DASHBOARD_STRATEGIC")).thenReturn(true);

        assertThatThrownBy(() -> call(view, "p1", "45"))
                .isInstanceOf(DashboardPeriodInvalidException.class)
                .hasFieldOrPropertyWithValue("code", "DASHBOARD_PERIOD_INVALID");
        verifyNoInteractions(OVERVIEW, ROADMAP, QUALITY);
    }

    @Test
    void should_deny403_beforeValidatingThePeriod() {
        when(PERM.onProject("p1", "DASHBOARD_STRATEGIC")).thenReturn(false);

        assertThatThrownBy(() -> call("overview", "p1", "45")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_reachEachUseCase_withTheValidatedPeriod_when_theProjectCheckPasses() {
        when(PERM.onProject("p1", "DASHBOARD_STRATEGIC")).thenReturn(true);

        // The mocked use cases return null, so the mapper may fail afterwards: only the gate matters.
        // No tenant is set in this context, so the organization reaches the use case as null.
        assertReached("overview", (org, days) -> verify(OVERVIEW).execute(org, "p1", days));
        assertReached("roadmap", (org, days) -> verify(ROADMAP).execute(org, "p1", days));
        assertReached("quality", (org, days) -> verify(QUALITY).execute(org, "p1", days));
    }

    private void assertReached(String view, BiConsumer<String, Integer> verification) {
        assertThat(catchThrowable(() -> call(view, "p1", null))).isNotInstanceOf(AccessDeniedException.class);
        verification.accept(null, 90);
    }

    private static Object call(String view, String projectId, String periodDays) {
        ProjectDashboardsV2Controller controller = context.getBean(ProjectDashboardsV2Controller.class);
        return switch (view) {
            case "overview" -> controller.overview(projectId, periodDays);
            case "roadmap" -> controller.roadmap(projectId, periodDays);
            default -> controller.quality(projectId, periodDays);
        };
    }
}
