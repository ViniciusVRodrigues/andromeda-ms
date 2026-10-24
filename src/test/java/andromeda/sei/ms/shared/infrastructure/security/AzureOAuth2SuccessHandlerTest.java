package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.azure.application.dto.AzureOAuthCallbackInput;
import andromeda.sei.ms.feature.azure.application.usecase.CompleteAzureOAuthConnectionUseCase;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AzureOAuth2SuccessHandlerTest {

    private static final String FRONT = "http://localhost:5173";
    private static final String NONCE = "nonce-value-123";
    private static final String ORG_URL = "https://dev.azure.com/acme";

    @Mock private OAuth2AuthorizedClientService authorizedClientService;
    @Mock private CompleteAzureOAuthConnectionUseCase completeUseCase;

    private AzureOAuth2SuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new AzureOAuth2SuccessHandler(authorizedClientService, completeUseCase,
                new OAuthFlowCompletion(authorizedClientService, FRONT, true));
        request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/callback/azure");
        response = new MockHttpServletResponse();
        stubClient();
    }

    @Test
    void success_connectsWithTheTicketData_andRedirectsConnected() throws Exception {
        happyRequest();

        handler.onAuthenticationSuccess(request, response, token());

        ArgumentCaptor<AzureOAuthCallbackInput> captor = ArgumentCaptor.forClass(AzureOAuthCallbackInput.class);
        verify(completeUseCase).execute(captor.capture());
        assertThat(captor.getValue().organizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().orgUrl()).isEqualTo(ORG_URL);
        assertThat(captor.getValue().accessToken()).isEqualTo("access-plain");
        assertEnded("connected", null);
    }

    @Test
    void invalidOrgUrl_redirectsAzureOrgUrlInvalid() throws Exception {
        happyRequest();
        doThrow(new AzureOrgUrlInvalidException("unreachable")).when(completeUseCase).execute(any());

        handler.onAuthenticationSuccess(request, response, token());

        assertEnded("error", "AZURE_ORG_URL_INVALID");
    }

    @Test
    void providerOrTokenFailure_redirectsProviderError() throws Exception {
        happyRequest();
        doThrow(new RuntimeException("boom")).when(completeUseCase).execute(any());

        handler.onAuthenticationSuccess(request, response, token());

        assertEnded("error", "OAUTH_PROVIDER_ERROR");
    }

    @Test
    void missingAuthorizedClient_redirectsProviderError() throws Exception {
        happyRequest();
        when(authorizedClientService.loadAuthorizedClient("azure", "sub-1")).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, token());

        verify(completeUseCase, never()).execute(any());
        assertEnded("error", "OAUTH_PROVIDER_ERROR");
    }

    @Test
    void sessionMismatch_nonceMissing_wrongOrgUrlInState_noBinding_wrongPurpose() throws Exception {
        // sem cookie
        bind(OAuthLinkPurpose.AZURE_ORG_CONNECT, ORG_URL);
        request.setParameter("state", AzureOAuthOrgUrlState.encode("s", ORG_URL));
        handler.onAuthenticationSuccess(request, response, token());
        assertMismatch();

        // state com outra URL
        setUp();
        happyRequest();
        request.setParameter("state", AzureOAuthOrgUrlState.encode("s", "https://dev.azure.com/evil"));
        handler.onAuthenticationSuccess(request, response, token());
        assertMismatch();

        // state sem URL
        setUp();
        happyRequest();
        request.setParameter("state", "plain-state");
        handler.onAuthenticationSuccess(request, response, token());
        assertMismatch();

        // sem binding
        setUp();
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        request.setParameter("state", AzureOAuthOrgUrlState.encode("s", ORG_URL));
        handler.onAuthenticationSuccess(request, response, token());
        assertMismatch();

        // propósito errado
        setUp();
        bind(OAuthLinkPurpose.GITHUB_PROFILE, null);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        request.setParameter("state", AzureOAuthOrgUrlState.encode("s", ORG_URL));
        handler.onAuthenticationSuccess(request, response, token());
        assertMismatch();
    }

    private void assertMismatch() throws Exception {
        verify(completeUseCase, never()).execute(any());
        assertEnded("error", "OAUTH_SESSION_MISMATCH");
        org.mockito.Mockito.clearInvocations(authorizedClientService, completeUseCase);
    }

    private void happyRequest() {
        bind(OAuthLinkPurpose.AZURE_ORG_CONNECT, ORG_URL);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        request.setParameter("state", AzureOAuthOrgUrlState.encode("s", ORG_URL));
    }

    /** Todo caminho: redirect (nunca 401/JSON), authorized client removido, sessao invalidada, cookie apagado. */
    private void assertEnded(String status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(302);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONT + "/oauth/retorno?provider=azure&status=" + status
                + (code != null ? "&code=" + code : ""));
        verify(authorizedClientService).removeAuthorizedClient("azure", "sub-1");
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Set-Cookie")).startsWith("andromeda_oauth_nonce=;")
                .contains("Max-Age=0", "HttpOnly");
    }

    private void bind(OAuthLinkPurpose purpose, String orgUrl) {
        request.getSession(true).setAttribute(LinkBinding.SESSION_ATTRIBUTE,
                new LinkBinding("org-1", "user-1", purpose, orgUrl, OAuthLinkTicket.hash(NONCE)));
    }

    private void stubClient() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-plain",
                Instant.now(), Instant.now().plusSeconds(3600), Set.of("offline_access"));
        ClientRegistration registration = ClientRegistration.withRegistrationId("azure")
                .clientId("c").clientSecret("s").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://x/cb").authorizationUri("http://x/a").tokenUri("http://x/t").build();
        when(authorizedClientService.loadAuthorizedClient("azure", "sub-1"))
                .thenReturn(new OAuth2AuthorizedClient(registration, "sub-1", accessToken));
    }

    private OAuth2AuthenticationToken token() {
        return new OAuth2AuthenticationToken(
                new DefaultOAuth2User(List.of(), Map.of("sub", "sub-1"), "sub"), List.of(), "azure");
    }
}
