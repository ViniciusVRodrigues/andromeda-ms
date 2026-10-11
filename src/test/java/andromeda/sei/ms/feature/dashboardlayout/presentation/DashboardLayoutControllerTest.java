package andromeda.sei.ms.feature.dashboardlayout.presentation;

import andromeda.sei.ms.feature.dashboardlayout.application.usecase.DeleteDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.ListDashboardLayoutsUseCase;
import andromeda.sei.ms.feature.dashboardlayout.application.usecase.SaveDashboardLayoutUseCase;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardWidget;
import andromeda.sei.ms.feature.dashboardlayout.domain.WidgetSize;
import andromeda.sei.ms.feature.dashboardlayout.application.DashboardViewAccess;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of {@code /api/v1/dashboards/layouts} (design.md D6) with the real use cases over a
 * mocked repository port. Standalone MockMvc — {@code @WebMvcTest} does not run under Boot 4 in this
 * repository; the {@code @PreAuthorize} gates are exercised in {@link DashboardLayoutMethodSecurityTest}.
 */
@ExtendWith(MockitoExtension.class)
class DashboardLayoutControllerTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final Instant UPDATED = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private DashboardLayoutRepository repository;
    @Mock private DashboardViewAccess access;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        DashboardLayoutController controller = new DashboardLayoutController(
                new ListDashboardLayoutsUseCase(repository), new SaveDashboardLayoutUseCase(repository),
                new DeleteDashboardLayoutUseCase(repository), access, new DashboardLayoutPresentationMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AndromedaUserDetails principal = AndromedaUserDetails.builder()
                .userId(USER).organizationId(ORG).permissions(List.of()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_shouldListOnlyAllowedViews_ofTheCaller_inContractShape() throws Exception {
        when(access.accessibleViewKeys()).thenReturn(Set.of("equipe"));
        when(repository.findAllActive(ORG, USER)).thenReturn(List.of(stored(DashboardViewKey.EQUIPE, 3L),
                stored(DashboardViewKey.ORG, 1L)));

        mockMvc.perform(get("/api/v1/dashboards/layouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].viewKey").value("equipe"))
                .andExpect(jsonPath("$.items[0].sections[0].id").value("fluxo"))
                .andExpect(jsonPath("$.items[0].sections[0].collapsed").value(false))
                .andExpect(jsonPath("$.items[0].sections[0].widgets[0].id").value("equipe.burndown"))
                .andExpect(jsonPath("$.items[0].sections[0].widgets[0].size").value("G"))
                .andExpect(jsonPath("$.items[0].hidden[0]").value("equipe.commits-dia"))
                .andExpect(jsonPath("$.items[0].version").value(3))
                .andExpect(jsonPath("$.items[0].updatedAt").exists())
                .andExpect(jsonPath("$.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.items[0].organizationId").doesNotExist());
    }

    @Test
    void put_shouldCreate_whenVersionIsNull_andReturnSavedLayout() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 0L));

        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON)
                        .content(body("null")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewKey").value("equipe"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.sections[0].widgets[0].size").value("G"))
                .andExpect(jsonPath("$.hidden[0]").value("equipe.commits-dia"));
    }

    @Test
    void put_shouldIgnoreUserAndOrganizationInBody_andUseThePrincipal() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 0L));

        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "someone-else", "organizationId": "org-evil",
                                 "sections": [], "hidden": [], "version": null}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG);
        verify(repository).findActive(ORG, USER, DashboardViewKey.EQUIPE);
        verify(repository, never()).findActive(anyString(), org.mockito.ArgumentMatchers.eq("someone-else"), any());
    }

    @Test
    void put_shouldUpdate_andReturnVersionPlusOne() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(DashboardViewKey.EQUIPE, 3L)));
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 4L));

        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON)
                        .content(body("3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void put_shouldReturn409_whenVersionIsStale() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(DashboardViewKey.EQUIPE, 4L)));

        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON)
                        .content(body("3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DASHBOARD_LAYOUT_VERSION_CONFLICT"));
        verify(repository, never()).save(any());
    }

    @Test
    void put_shouldReturn409_whenCreatingOverAnActiveLayout() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(DashboardViewKey.EQUIPE, 3L)));

        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON)
                        .content(body("null")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DASHBOARD_LAYOUT_VERSION_CONFLICT"));
    }

    @Test
    void put_shouldReturn422_forUnknownSize() throws Exception {
        assertInvalid("""
                {"sections": [{"id": "fluxo", "collapsed": false, "widgets": [{"id": "equipe.a", "size": "XL"}]}],
                 "hidden": [], "version": null}
                """);
    }

    @Test
    void put_shouldReturn422_forDuplicateId() throws Exception {
        assertInvalid("""
                {"sections": [{"id": "fluxo", "widgets": [{"id": "equipe.a", "size": "P"}]}],
                 "hidden": ["equipe.a"], "version": null}
                """);
    }

    @Test
    void put_shouldReturn422_forIdOutOfFormat() throws Exception {
        assertInvalid("""
                {"sections": [{"id": "fluxo", "widgets": [{"id": "Equipe Burndown", "size": "P"}]}],
                 "hidden": [], "version": null}
                """);
    }

    @Test
    void put_shouldReturn422_forSixtyOneItems() throws Exception {
        StringBuilder hidden = new StringBuilder();
        for (int i = 0; i < 61; i++) hidden.append(i == 0 ? "" : ",").append("\"equipe.w").append(i).append('"');
        assertInvalid("{\"sections\": [], \"hidden\": [" + hidden + "], \"version\": null}");
    }

    @Test
    void put_shouldReturn422_whenSectionsMissing() throws Exception {
        assertInvalid("{\"hidden\": [], \"version\": null}");
    }

    @Test
    void delete_shouldReturn204_andBeIdempotent() throws Exception {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE))
                .thenReturn(Optional.of(stored(DashboardViewKey.EQUIPE, 3L)), Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 4L));

        mockMvc.perform(delete("/api/v1/dashboards/layouts/equipe")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/dashboards/layouts/equipe")).andExpect(status().isNoContent());

        ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(repository, times(1)).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        verify(repository, times(2)).findActive(ORG, USER, DashboardViewKey.EQUIPE);
    }

    private void assertInvalid(String json) throws Exception {
        mockMvc.perform(put("/api/v1/dashboards/layouts/equipe").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DASHBOARD_LAYOUT_INVALID"));
        verify(repository, never()).save(any());
    }

    private static String body(String version) {
        return """
                {"sections": [{"id": "fluxo", "collapsed": false,
                               "widgets": [{"id": "equipe.burndown", "size": "G"}]}],
                 "hidden": ["equipe.commits-dia"], "version": %s}
                """.formatted(version);
    }

    private static DashboardLayout stored(DashboardViewKey viewKey, long version) {
        return new DashboardLayout("layout-" + viewKey.value(), ORG, USER, viewKey,
                List.of(new DashboardSection("fluxo", false, List.of(new DashboardWidget("equipe.burndown", WidgetSize.G)))),
                List.of("equipe.commits-dia"), true, version, UPDATED, UPDATED);
    }

    private static DashboardLayout persisted(DashboardLayout layout, long version) {
        return new DashboardLayout(layout.getId() == null ? "layout-new" : layout.getId(), layout.getOrganizationId(),
                layout.getUserId(), layout.getViewKey(), layout.getSections(), layout.getHidden(), layout.isActive(),
                version, UPDATED, UPDATED);
    }
}
