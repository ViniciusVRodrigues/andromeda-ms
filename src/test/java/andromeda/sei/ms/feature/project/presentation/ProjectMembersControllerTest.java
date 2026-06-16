package andromeda.sei.ms.feature.project.presentation;

import andromeda.sei.ms.feature.project.application.usecase.CreateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.DeactivateProjectUseCase;
import andromeda.sei.ms.feature.project.application.usecase.ListProjectsUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectMembersUseCase;
import andromeda.sei.ms.feature.project.application.usecase.UpdateProjectUseCase;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.exception.ProjectInvalidMemberException;
import andromeda.sei.ms.feature.project.domain.exception.ProjectNotFoundException;
import andromeda.sei.ms.feature.workitem.application.usecase.GetProjectSprintsUseCase;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProjectMembersControllerTest {

    @Mock private ListProjectsUseCase listProjectsUseCase;
    @Mock private CreateProjectUseCase createProjectUseCase;
    @Mock private UpdateProjectUseCase updateProjectUseCase;
    @Mock private DeactivateProjectUseCase deactivateProjectUseCase;
    @Mock private UpdateProjectMembersUseCase updateProjectMembersUseCase;
    @Mock private GetProjectSprintsUseCase getProjectSprintsUseCase;

    @InjectMocks
    private ProjectController controller;

    private MockMvc mockMvc;

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        TenantContextHolder.set(ORG_ID);
        var auth = new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority("USER_MANAGE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    private Project project(List<String> memberIds) {
        return new Project(PROJECT_ID, ORG_ID, "My Project", "my-project", "desc", true, memberIds, 0L, NOW, NOW);
    }

    @Test
    void updateMembers_shouldReturn200WithUpdatedMemberIds() throws Exception {
        when(updateProjectMembersUseCase.execute(eq(PROJECT_ID), any()))
                .thenReturn(project(List.of("user-1")));

        mockMvc.perform(put("/api/v1/projects/{id}/members", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"user-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds[0]").value("user-1"));
    }

    @Test
    void updateMembers_projectNotFound_shouldReturn404() throws Exception {
        when(updateProjectMembersUseCase.execute(eq(PROJECT_ID), any()))
                .thenThrow(new ProjectNotFoundException(PROJECT_ID));

        mockMvc.perform(put("/api/v1/projects/{id}/members", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"user-1\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMembers_invalidMember_shouldReturn422() throws Exception {
        when(updateProjectMembersUseCase.execute(eq(PROJECT_ID), any()))
                .thenThrow(new ProjectInvalidMemberException("user-99"));

        mockMvc.perform(put("/api/v1/projects/{id}/members", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[\"user-99\"]}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void updateMembers_emptyMemberIds_shouldReturn200() throws Exception {
        when(updateProjectMembersUseCase.execute(eq(PROJECT_ID), any()))
                .thenReturn(project(new ArrayList<>()));

        mockMvc.perform(put("/api/v1/projects/{id}/members", PROJECT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds").isEmpty());
    }
}
