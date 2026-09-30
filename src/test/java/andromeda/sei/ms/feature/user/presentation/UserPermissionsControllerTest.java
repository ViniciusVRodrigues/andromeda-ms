package andromeda.sei.ms.feature.user.presentation;

import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideInput;
import andromeda.sei.ms.feature.user.application.dto.PermissionOverrideOutput;
import andromeda.sei.ms.feature.user.application.dto.ProjectMemberPermissionsOutput;
import andromeda.sei.ms.feature.user.application.dto.ReplacePermissionOverridesInput;
import andromeda.sei.ms.feature.user.application.dto.UserPermissionsOutput;
import andromeda.sei.ms.feature.user.application.usecase.GetUserPermissionsUseCase;
import andromeda.sei.ms.feature.user.application.usecase.ListProjectMemberPermissionsUseCase;
import andromeda.sei.ms.feature.user.application.usecase.ReplaceUserPermissionOverridesUseCase;
import andromeda.sei.ms.feature.user.domain.exception.PermissionEscalationDeniedException;
import andromeda.sei.ms.feature.user.domain.exception.PermissionNotProjectScopableException;
import andromeda.sei.ms.feature.user.domain.exception.UserConflictException;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.infrastructure.security.CurrentUserPermissions;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * project-scoped-permissions 5.4 — contract of the permission endpoints. Standalone MockMvc (as the
 * rest of the repo): {@code @PreAuthorize} is covered by {@code MethodSecurityPermExpressionsTest}.
 */
@ExtendWith(MockitoExtension.class)
class UserPermissionsControllerTest {

    private static final String ORG = "org-1";

    @Mock private GetUserPermissionsUseCase getUserPermissionsUseCase;
    @Mock private ReplaceUserPermissionOverridesUseCase replaceUserPermissionOverridesUseCase;
    @Mock private ListProjectMemberPermissionsUseCase listProjectMemberPermissionsUseCase;
    @Mock private CurrentUserPermissions currentUserPermissions;

    private MockMvc mockMvc;
    private final UserPermissionSnapshot caller =
            new UserPermissionSnapshot(ORG, "admin-1", "r", "ADMIN", List.of("USER_MANAGE"), List.of(), Set.of(), 1L);

    private final UserPermissionsOutput output = new UserPermissionsOutput("dev-1", "role-dev", "DEVELOPER",
            List.of("DASHBOARD_INDIVIDUAL"),
            List.of(new PermissionOverrideOutput("DASHBOARD_TEAM", "GRANT", "project-a", "admin-1",
                    Instant.parse("2026-09-30T10:00:00Z"))),
            List.of("DASHBOARD_INDIVIDUAL"), Map.of("project-a", List.of("DASHBOARD_TEAM")), 4L);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserPermissionsController(getUserPermissionsUseCase,
                        replaceUserPermissionOverridesUseCase, listProjectMemberPermissionsUseCase, currentUserPermissions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TenantContextHolder.set(ORG);
        lenient().when(currentUserPermissions.require()).thenReturn(caller);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnPresetOverridesAndEffective_onGet() throws Exception {
        when(getUserPermissionsUseCase.execute(ORG, "dev-1")).thenReturn(output);

        mockMvc.perform(get("/api/v1/users/dev-1/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("dev-1"))
                .andExpect(jsonPath("$.roleId").value("role-dev"))
                .andExpect(jsonPath("$.roleName").value("DEVELOPER"))
                .andExpect(jsonPath("$.rolePermissions[0]").value("DASHBOARD_INDIVIDUAL"))
                .andExpect(jsonPath("$.overrides[0].code").value("DASHBOARD_TEAM"))
                .andExpect(jsonPath("$.overrides[0].effect").value("GRANT"))
                .andExpect(jsonPath("$.overrides[0].projectId").value("project-a"))
                .andExpect(jsonPath("$.overrides[0].updatedBy").value("admin-1"))
                .andExpect(jsonPath("$.overrides[0].updatedAt").exists())
                .andExpect(jsonPath("$.orgPermissions[0]").value("DASHBOARD_INDIVIDUAL"))
                .andExpect(jsonPath("$.projectPermissions.project-a[0]").value("DASHBOARD_TEAM"))
                .andExpect(jsonPath("$.version").value(4));
    }

    @Test
    void should_replaceOverrides_withCallerAndVersion_andAnswerSameShape() throws Exception {
        when(replaceUserPermissionOverridesUseCase.execute(any())).thenReturn(output);

        mockMvc.perform(put("/api/v1/users/dev-1/permission-overrides")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"overrides":[{"code":"DASHBOARD_TEAM","effect":"GRANT","projectId":"project-a"},
                                              {"code":"AZURE_READ","effect":"DENY","projectId":null}],
                                 "version":3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("dev-1"))
                .andExpect(jsonPath("$.version").value(4));

        ArgumentCaptor<ReplacePermissionOverridesInput> input = ArgumentCaptor.forClass(ReplacePermissionOverridesInput.class);
        verify(replaceUserPermissionOverridesUseCase).execute(input.capture());
        assertThat(input.getValue().caller()).isSameAs(caller);
        assertThat(input.getValue().userId()).isEqualTo("dev-1");
        assertThat(input.getValue().version()).isEqualTo(3L);
        assertThat(input.getValue().overrides()).containsExactly(
                new PermissionOverrideInput("DASHBOARD_TEAM", "GRANT", "project-a"),
                new PermissionOverrideInput("AZURE_READ", "DENY", null));
    }

    @Test
    void should_mapDomainErrors_toContractStatuses() throws Exception {
        when(replaceUserPermissionOverridesUseCase.execute(any()))
                .thenThrow(new UserConflictException("dev-1"))
                .thenThrow(new PermissionNotProjectScopableException("USER_MANAGE"))
                .thenThrow(new PermissionEscalationDeniedException("PRICE_TABLE_MANAGE", null));
        String body = "{\"overrides\":[],\"version\":1}";

        mockMvc.perform(put("/api/v1/users/dev-1/permission-overrides").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_CONFLICT"));
        mockMvc.perform(put("/api/v1/users/dev-1/permission-overrides").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PERMISSION_NOT_PROJECT_SCOPABLE"));
        mockMvc.perform(put("/api/v1/users/dev-1/permission-overrides").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION_DENIED"));
    }

    @Test
    void should_listProjectMembersWithEffectiveAndProjectOverrides() throws Exception {
        when(listProjectMemberPermissionsUseCase.execute(ORG, "project-a")).thenReturn(List.of(
                new ProjectMemberPermissionsOutput("dev-1", "Dev", "dev@x.com", "DEVELOPER", List.of("DASHBOARD_TEAM"),
                        List.of(new ProjectMemberPermissionsOutput.ProjectOverride("REPOS_READ", "DENY")))));

        mockMvc.perform(get("/api/v1/projects/project-a/member-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("dev-1"))
                .andExpect(jsonPath("$[0].name").value("Dev"))
                .andExpect(jsonPath("$[0].email").value("dev@x.com"))
                .andExpect(jsonPath("$[0].roleName").value("DEVELOPER"))
                .andExpect(jsonPath("$[0].effective[0]").value("DASHBOARD_TEAM"))
                .andExpect(jsonPath("$[0].projectOverrides[0].code").value("REPOS_READ"))
                .andExpect(jsonPath("$[0].projectOverrides[0].effect").value("DENY"));
    }
}
