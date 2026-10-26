package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.usecase.DeleteOrganizationUseCase;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationDeleteConfirmationMismatchException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: {@code @PreAuthorize} is covered by {@code OrganizationDeleteSecurityTest}. */
@ExtendWith(MockitoExtension.class)
class OrganizationDeleteControllerTest {

    @Mock private DeleteOrganizationUseCase deleteOrganizationUseCase;

    @InjectMocks
    private OrganizationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user-1", null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void should_return204_when_slugIsConfirmed() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"acme\"}"))
                .andExpect(status().isNoContent());

        verify(deleteOrganizationUseCase).execute("org-1", "user-1", "acme");
    }

    @Test
    void should_return400_when_confirmSlugIsMissing() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(deleteOrganizationUseCase, never()).execute(any(), any(), any());
    }

    @Test
    void should_return400_when_confirmSlugIsBlank() throws Exception {
        mockMvc.perform(delete("/api/v1/organizations/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return422WithCode_when_slugDoesNotMatch() throws Exception {
        doThrow(new OrganizationDeleteConfirmationMismatchException())
                .when(deleteOrganizationUseCase).execute("org-1", "user-1", "wrong");

        mockMvc.perform(delete("/api/v1/organizations/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmSlug\":\"wrong\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORGANIZATION_DELETE_CONFIRMATION_MISMATCH"));
    }
}
