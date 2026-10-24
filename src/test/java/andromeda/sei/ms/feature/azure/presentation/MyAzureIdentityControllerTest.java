package andromeda.sei.ms.feature.azure.presentation;

import andromeda.sei.ms.feature.azure.application.dto.MyAzureIdentityOutput;
import andromeda.sei.ms.feature.azure.application.usecase.GetMyAzureIdentityUseCase;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MyAzureIdentityControllerTest {

    @Mock private GetMyAzureIdentityUseCase getMyAzureIdentityUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MyAzureIdentityController(getMyAzureIdentityUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        TenantContextHolder.set("org-1");
        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1").permissions(List.of()).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void get_shouldUsePrincipalSubjectAndTenant_andReturnLinkedWithSourceAndDate_withoutAzureId() throws Exception {
        when(getMyAzureIdentityUseCase.execute("org-1", "user-1")).thenReturn(new MyAzureIdentityOutput(
                "LINKED", "vinicius@empresa.com", "Vinicius", "AUTO_EMAIL", Instant.parse("2026-10-24T03:00:12Z")));

        mockMvc.perform(get("/api/v1/users/me/azure-identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINKED"))
                .andExpect(jsonPath("$.azureEmail").value("vinicius@empresa.com"))
                .andExpect(jsonPath("$.displayName").value("Vinicius"))
                .andExpect(jsonPath("$.source").value("AUTO_EMAIL"))
                .andExpect(jsonPath("$.linkedAt").exists())
                .andExpect(jsonPath("$.azureId").doesNotExist());

        verify(getMyAzureIdentityUseCase).execute("org-1", "user-1");
    }

    @Test
    void get_shouldReturnTakenWithNoDataAtAll() throws Exception {
        when(getMyAzureIdentityUseCase.execute("org-1", "user-1")).thenReturn(MyAzureIdentityOutput.of("TAKEN"));

        mockMvc.perform(get("/api/v1/users/me/azure-identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TAKEN"))
                .andExpect(jsonPath("$.azureId").doesNotExist())
                .andExpect(jsonPath("$.azureEmail").value((Object) null))
                .andExpect(jsonPath("$.displayName").value((Object) null))
                .andExpect(jsonPath("$.source").value((Object) null))
                .andExpect(jsonPath("$.linkedAt").value((Object) null))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void get_shouldReturnNoConnection() throws Exception {
        when(getMyAzureIdentityUseCase.execute("org-1", "user-1")).thenReturn(MyAzureIdentityOutput.of("NO_CONNECTION"));

        mockMvc.perform(get("/api/v1/users/me/azure-identity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_CONNECTION"));
    }
}
