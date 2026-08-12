package andromeda.sei.ms.feature.provideraccount.presentation;

import andromeda.sei.ms.feature.provideraccount.application.dto.ProviderAccountOutput;
import andromeda.sei.ms.feature.provideraccount.application.usecase.ListMyProviderAccountsUseCase;
import andromeda.sei.ms.feature.provideraccount.application.usecase.UnlinkProviderAccountUseCase;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountNotFoundException;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProviderAccountControllerTest {

    @Mock private ListMyProviderAccountsUseCase listMyProviderAccountsUseCase;
    @Mock private UnlinkProviderAccountUseCase unlinkProviderAccountUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserProviderAccountController controller = new UserProviderAccountController(
                listMyProviderAccountsUseCase, unlinkProviderAccountUseCase,
                new UserProviderAccountPresentationMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        TenantContextHolder.set("org-1");

        AndromedaUserDetails user = AndromedaUserDetails.builder()
                .userId("user-1").organizationId("org-1")
                .permissions(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void list_shouldReturnAccounts_withNoTokenFields() throws Exception {
        Instant linkedAt = Instant.parse("2024-01-01T00:00:00Z");
        when(listMyProviderAccountsUseCase.execute("org-1", "user-1")).thenReturn(List.of(
                new ProviderAccountOutput(Provider.GITHUB, "octocat", linkedAt, null, false)));

        mockMvc.perform(get("/api/v1/users/me/provider-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("GITHUB"))
                .andExpect(jsonPath("$[0].externalLogin").value("octocat"))
                .andExpect(jsonPath("$[0].hasRefreshToken").value(false))
                .andExpect(jsonPath("$[0].accessToken").doesNotExist())
                .andExpect(jsonPath("$[0].accessTokenEncrypted").doesNotExist())
                .andExpect(jsonPath("$[0].refreshTokenEncrypted").doesNotExist());
    }

    @Test
    void unlink_shouldReturn204_whenAccountUnlinked() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/provider-accounts/GITHUB"))
                .andExpect(status().isNoContent());

        verify(unlinkProviderAccountUseCase).execute("org-1", "user-1", Provider.GITHUB);
    }

    @Test
    void unlink_shouldReturn404_whenNoActiveAccount() throws Exception {
        doThrow(new ProviderAccountNotFoundException("GITHUB"))
                .when(unlinkProviderAccountUseCase).execute(eq("org-1"), eq("user-1"), eq(Provider.GITHUB));

        mockMvc.perform(delete("/api/v1/users/me/provider-accounts/GITHUB"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROVIDER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void unlink_shouldReturn400_whenProviderUnknown() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/provider-accounts/NOT_A_PROVIDER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PROVIDER_INVALID"));
    }
}
