package andromeda.sei.ms.feature.dashboardlayout.presentation;

import andromeda.sei.ms.feature.dashboardlayout.application.usecase.DeleteDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.ListDashboardLayoutsUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.SaveDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.infrastructure.security.DashboardLayoutAccess;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.ProjectPermissionEvaluator;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The per-view gates really run through method security (design.md D3): the controller bean is
 * proxied by {@code @EnableMethodSecurity} and driven over HTTP, so a denial is a 403 through
 * {@code GlobalExceptionHandler} and an unknown viewKey is a 422 raised by the gate itself, before
 * any permission is consulted.
 */
class DashboardLayoutMethodSecurityTest {

    private static final ProjectPermissionEvaluator PERM = mock(ProjectPermissionEvaluator.class);
    private static final DashboardLayoutRepository REPOSITORY = mock(DashboardLayoutRepository.class);
    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final String VALID_BODY = """
            {"sections": [{"id": "fluxo", "collapsed": false, "widgets": [{"id": "x.burndown", "size": "G"}]}],
             "hidden": [], "version": null}
            """;

    private static AnnotationConfigApplicationContext context;
    private MockMvc mockMvc;

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean ProjectPermissionEvaluator perm() { return PERM; }

        @Bean DashboardLayoutAccess dashboardLayoutAccess(ProjectPermissionEvaluator perm) {
            return new DashboardLayoutAccess(perm);
        }

        @Bean DashboardLayoutController dashboardLayoutController(DashboardLayoutAccess access) {
            return new DashboardLayoutController(new ListDashboardLayoutsUseCase(REPOSITORY),
                    new SaveDashboardLayoutUseCase(REPOSITORY), new DeleteDashboardLayoutUseCase(REPOSITORY),
                    access, new DashboardLayoutPresentationMapper());
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
        reset(PERM, REPOSITORY);
        mockMvc = MockMvcBuilders.standaloneSetup(context.getBean(DashboardLayoutController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        when(REPOSITORY.findActive(anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(REPOSITORY.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void individual_shouldBeForbidden_withoutDashboardIndividual() throws Exception {
        authenticateWith("DASHBOARD_TEAM");

        putLayout("individual").andExpect(status().isForbidden());
        deleteLayout("individual").andExpect(status().isForbidden());
        verify(REPOSITORY, never()).save(any());
    }

    @Test
    void individual_shouldBeAllowed_withDashboardIndividual() throws Exception {
        authenticateWith("DASHBOARD_INDIVIDUAL");

        putLayout("individual").andExpect(status().isOk());
        deleteLayout("individual").andExpect(status().isNoContent());
    }

    @Test
    void equipe_shouldBeForbidden_whenNoProjectGrantsDashboardTeam() throws Exception {
        authenticateWith("DASHBOARD_INDIVIDUAL", "DASHBOARD_STRATEGIC");
        when(PERM.onAnyProject("DASHBOARD_TEAM")).thenReturn(false);

        putLayout("equipe").andExpect(status().isForbidden());
        deleteLayout("equipe").andExpect(status().isForbidden());
        verify(REPOSITORY, never()).save(any());
    }

    @Test
    void equipe_shouldBeAllowed_whenSomeProjectGrantsDashboardTeam() throws Exception {
        authenticateWith();
        when(PERM.onAnyProject("DASHBOARD_TEAM")).thenReturn(true);

        putLayout("equipe").andExpect(status().isOk());
    }

    @Test
    void projetoAndOrg_shouldBeForbidden_withoutDashboardStrategicOnAnyProject() throws Exception {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        when(PERM.onAnyProject("DASHBOARD_TEAM")).thenReturn(true);
        when(PERM.onAnyProject("DASHBOARD_STRATEGIC")).thenReturn(false);

        putLayout("projeto").andExpect(status().isForbidden());
        putLayout("org").andExpect(status().isForbidden());
        deleteLayout("org").andExpect(status().isForbidden());
        verify(REPOSITORY, never()).save(any());
    }

    @Test
    void projetoAndOrg_shouldBeAllowed_withDashboardStrategicOnAnyProject() throws Exception {
        authenticateWith();
        when(PERM.onAnyProject("DASHBOARD_STRATEGIC")).thenReturn(true);

        putLayout("projeto").andExpect(status().isOk());
        putLayout("org").andExpect(status().isOk());
    }

    @Test
    void unknownViewKey_shouldBe422_beforeAnyPermissionCheck() throws Exception {
        authenticateWith();

        putLayout("qualquer").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DASHBOARD_LAYOUT_VIEW_UNKNOWN"));
        deleteLayout("qualquer").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DASHBOARD_LAYOUT_VIEW_UNKNOWN"));
        verifyNoInteractions(PERM, REPOSITORY);
    }

    @Test
    void get_shouldListOnlyViewsTheCallerMaySee() throws Exception {
        authenticateWith("DASHBOARD_INDIVIDUAL");
        when(PERM.onAnyProject("DASHBOARD_TEAM")).thenReturn(false);
        when(PERM.onAnyProject("DASHBOARD_STRATEGIC")).thenReturn(true);
        when(REPOSITORY.findAllActive(ORG, USER)).thenReturn(Arrays.stream(DashboardViewKey.values())
                .map(DashboardLayoutMethodSecurityTest::stored).toList());

        mockMvc.perform(get("/api/v1/dashboards/layouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[?(@.viewKey == 'equipe')]").isEmpty());
    }

    @Test
    void get_shouldReturnEmptyItems_whenNoViewIsAllowed() throws Exception {
        authenticateWith();

        mockMvc.perform(get("/api/v1/dashboards/layouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        verifyNoInteractions(REPOSITORY);
    }

    private ResultActions putLayout(String viewKey) throws Exception {
        return mockMvc.perform(put("/api/v1/dashboards/layouts/" + viewKey)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY));
    }

    private ResultActions deleteLayout(String viewKey) throws Exception {
        return mockMvc.perform(delete("/api/v1/dashboards/layouts/" + viewKey));
    }

    private static DashboardLayout stored(DashboardViewKey viewKey) {
        return new DashboardLayout("id-" + viewKey.value(), ORG, USER, viewKey, List.of(), List.of(), true, 1L,
                Instant.EPOCH, Instant.EPOCH);
    }

    private static void authenticateWith(String... authorities) {
        AndromedaUserDetails principal = AndromedaUserDetails.builder()
                .userId(USER).organizationId(ORG).permissions(List.of(authorities)).build();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
