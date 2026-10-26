package andromeda.sei.ms.feature.role.presentation;

import andromeda.sei.ms.feature.role.application.dto.CreateRoleInput;
import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.application.usecase.CloneRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.CreateRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.DeleteRoleUseCase;
import andromeda.sei.ms.feature.role.application.usecase.ListRolesUseCase;
import andromeda.sei.ms.feature.role.application.usecase.UpdateRolePermissionsUseCase;
import andromeda.sei.ms.feature.role.domain.exception.RoleDuplicateNameException;
import andromeda.sei.ms.feature.role.domain.exception.RolePermissionNotAssignableException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ROL1: POST /api/v1/roles had no handler and surfaced as INTERNAL_ERROR/500. */
@ExtendWith(MockitoExtension.class)
class RoleControllerCreateTest {

    @Mock private ListRolesUseCase list;
    @Mock private CreateRoleUseCase create;
    @Mock private CloneRoleUseCase clone;
    @Mock private UpdateRolePermissionsUseCase update;
    @Mock private DeleteRoleUseCase delete;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoleController(list, create, clone, update, delete, new RolePresentationMapper()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void post_shouldReturn201WithTheCreatedRole() throws Exception {
        when(create.execute(eq(new CreateRoleInput("QA", List.of("AZURE_READ"))), eq("org-1")))
                .thenReturn(new RoleOutput("r1", "QA", "QA", "org-1", false, List.of("AZURE_READ")));

        mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QA\",\"permissionCodes\":[\"AZURE_READ\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.permissionCodes[0]").value("AZURE_READ"));
    }

    @Test
    void post_shouldReturn400ValidationErrorWhenNameIsMissingOrBlank() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":\"  \"}"}) {
            mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void post_shouldReturn400WhenBodyIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void post_shouldReturn409OnDuplicateName() throws Exception {
        when(create.execute(any(), any())).thenThrow(new RoleDuplicateNameException("QA"));

        mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"QA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROLE_DUPLICATE_NAME"));
    }

    @Test
    void post_shouldReturn422OnUnassignablePermission() throws Exception {
        when(create.execute(any(), any())).thenThrow(new RolePermissionNotAssignableException("NOPE"));

        mockMvc.perform(post("/api/v1/roles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"QA\",\"permissionCodes\":[\"NOPE\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_PERMISSION_NOT_ASSIGNABLE"));
    }

    @Test
    void wrongVerbOnAnExistingRoute_shouldBe405NotAServerError() throws Exception {
        mockMvc.perform(put("/api/v1/roles"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
}
