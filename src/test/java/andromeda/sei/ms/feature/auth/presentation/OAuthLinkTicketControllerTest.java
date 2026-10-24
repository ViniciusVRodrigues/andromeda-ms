package andromeda.sei.ms.feature.auth.presentation;

import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketInput;
import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketOutput;
import andromeda.sei.ms.feature.auth.application.usecase.IssueOAuthLinkTicketUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.shared.domain.exception.ForbiddenOperationException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OAuthLinkTicketControllerTest {

    private static final String URL = "/api/v1/auth/oauth2/link-ticket";

    @Mock IssueOAuthLinkTicketUseCase useCase;
    private MockMvc mockMvc;
    private UsernamePasswordAuthenticationToken auth;

    @org.junit.jupiter.api.AfterEach
    void clear() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OAuthLinkTicketController(useCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        AndromedaUserDetails p = AndromedaUserDetails.builder()
                .userId("u1").organizationId("o1").permissions(List.of("AZURE_CONFIGURE")).build();
        auth = new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void should_return201WithTicket_andPassPrincipalToUseCase() throws Exception {
        when(useCase.execute(any())).thenReturn(new IssueOAuthLinkTicketOutput("tk", 60));

        mockMvc.perform(post(URL).principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"GITHUB_PROFILE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticket").value("tk"))
                .andExpect(jsonPath("$.expiresIn").value(60));

        ArgumentCaptor<IssueOAuthLinkTicketInput> in = ArgumentCaptor.forClass(IssueOAuthLinkTicketInput.class);
        verify(useCase).execute(in.capture());
        assertThat(in.getValue().userId()).isEqualTo("u1");
        assertThat(in.getValue().organizationId()).isEqualTo("o1");
        assertThat(in.getValue().purpose()).isEqualTo(OAuthLinkPurpose.GITHUB_PROFILE);
    }

    @Test
    void should_return400_forUnknownPurpose() throws Exception {
        mockMvc.perform(post(URL).principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"HACK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_PURPOSE_INVALID"));
        verify(useCase, never()).execute(any());
    }

    @Test
    void should_return400_forMissingPurpose() throws Exception {
        mockMvc.perform(post(URL).principal(auth).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OAUTH_PURPOSE_INVALID"));
    }

    @Test
    void should_return403_whenUseCaseForbids() throws Exception {
        when(useCase.execute(any())).thenThrow(new ForbiddenOperationException("X", "no"));

        mockMvc.perform(post(URL).principal(auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"AZURE_ORG_CONNECT\",\"azureOrgUrl\":\"https://dev.azure.com/a\"}"))
                .andExpect(status().isForbidden());
    }
}
