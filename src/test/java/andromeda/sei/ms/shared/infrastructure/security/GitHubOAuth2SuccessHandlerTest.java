package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.LinkGithubUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkTicket;
import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.application.usecase.LinkProviderAccountUseCase;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountAlreadyLinkedException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GitHubOAuth2SuccessHandlerTest {

    private static final String FRONT = "http://localhost:5173";
    private static final String NONCE = "nonce-value-123";

    @Mock private LinkGithubUseCase linkGithubUseCase;
    @Mock private LinkProviderAccountUseCase linkProviderAccountUseCase;
    @Mock private OAuth2AuthorizedClientService authorizedClientService;

    private GitHubOAuth2SuccessHandler handler;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new GitHubOAuth2SuccessHandler(linkGithubUseCase, linkProviderAccountUseCase,
                authorizedClientService, new OAuthFlowCompletion(authorizedClientService, FRONT, true));
        request = new MockHttpServletRequest("GET", "/api/v1/auth/oauth2/callback/github");
        response = new MockHttpServletResponse();
    }

    @Test
    void success_linksAccountThenGithubId_andRedirectsLinked() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        stubClient();

        handler.onAuthenticationSuccess(request, response, token());

        var order = inOrder(linkProviderAccountUseCase, linkGithubUseCase);
        ArgumentCaptor<LinkProviderAccountInput> captor = ArgumentCaptor.forClass(LinkProviderAccountInput.class);
        order.verify(linkProviderAccountUseCase).execute(captor.capture());
        order.verify(linkGithubUseCase).execute("user-1", "org-1", "58421");
        assertThat(captor.getValue().organizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().externalUserId()).isEqualTo("58421");
        assertThat(captor.getValue().externalLogin()).isEqualTo("octocat");
        assertThat(captor.getValue().accessToken()).isEqualTo("access-plain");
        assertEnded("linked", null);
    }

    @Test
    void duplicate_writesNothing_andRedirectsAlreadyLinked() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        stubClient();
        doThrow(new ProviderAccountAlreadyLinkedException("GITHUB", "58421"))
                .when(linkProviderAccountUseCase).execute(any());

        handler.onAuthenticationSuccess(request, response, token());

        verify(linkGithubUseCase, never()).execute(any(), any(), any());
        assertEnded("error", "PROVIDER_ACCOUNT_ALREADY_LINKED");
    }

    @Test
    void accountWriteFails_doesNotWriteGithubId_andRedirectsLinkFailed() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        stubClient();
        doThrow(new RuntimeException("boom")).when(linkProviderAccountUseCase).execute(any());

        handler.onAuthenticationSuccess(request, response, token());

        verify(linkGithubUseCase, never()).execute(any(), any(), any());
        assertEnded("error", "OAUTH_LINK_FAILED");
    }

    @Test
    void githubIdWriteFails_redirectsLinkFailed() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));
        stubClient();
        doThrow(new RuntimeException("boom")).when(linkGithubUseCase).execute(any(), any(), any());

        handler.onAuthenticationSuccess(request, response, token());

        assertEnded("error", "OAUTH_LINK_FAILED");
    }

    @Test
    void missingNonceCookie_isSessionMismatch_withoutWrites() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);

        handler.onAuthenticationSuccess(request, response, token());

        assertNoWrites();
        assertEnded("error", "OAUTH_SESSION_MISMATCH");
    }

    @Test
    void wrongNonceCookie_isSessionMismatch_withoutWrites() throws Exception {
        bind(OAuthLinkPurpose.GITHUB_PROFILE, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, "other-nonce"));

        handler.onAuthenticationSuccess(request, response, token());

        assertNoWrites();
        assertEnded("error", "OAUTH_SESSION_MISMATCH");
    }

    @Test
    void noBindingInSession_isSessionMismatch_not401() throws Exception {
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));

        handler.onAuthenticationSuccess(request, response, token());

        assertNoWrites();
        assertEnded("error", "OAUTH_SESSION_MISMATCH");
    }

    @Test
    void bindingOfAnotherPurpose_isSessionMismatch() throws Exception {
        bind(OAuthLinkPurpose.AZURE_ORG_CONNECT, NONCE);
        request.setCookies(new Cookie(OAuthNonceCookie.NAME, NONCE));

        handler.onAuthenticationSuccess(request, response, token());

        assertNoWrites();
        assertEnded("error", "OAUTH_SESSION_MISMATCH");
    }

    private void assertNoWrites() {
        verify(linkProviderAccountUseCase, never()).execute(any());
        verify(linkGithubUseCase, never()).execute(any(), any(), any());
    }

    /** Todo caminho: redirect para o retorno, authorized client removido, sessao invalidada, cookie apagado. */
    private void assertEnded(String status, String code) {
        assertThat(response.getStatus()).isEqualTo(302);
        String expected = FRONT + "/oauth/retorno?provider=github&status=" + status
                + (code != null ? "&code=" + code : "");
        assertThat(response.getRedirectedUrl()).isEqualTo(expected);
        verify(authorizedClientService).removeAuthorizedClient("github", "58421");
        assertThat(request.getSession(false)).isNull();
        assertThat(response.getHeader("Set-Cookie")).startsWith("andromeda_oauth_nonce=;")
                .contains("Max-Age=0", "HttpOnly", "Path=/api/v1/auth/oauth2");
    }

    private void bind(OAuthLinkPurpose purpose, String nonce) {
        request.getSession(true).setAttribute(LinkBinding.SESSION_ATTRIBUTE,
                new LinkBinding("org-1", "user-1", purpose, null, OAuthLinkTicket.hash(nonce)));
    }

    private void stubClient() {
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-plain",
                Instant.now(), Instant.now().plusSeconds(3600), new LinkedHashSet<>(List.of("read:user")));
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("c").clientSecret("s").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://x/cb").authorizationUri("http://x/a").tokenUri("http://x/t").build();
        when(authorizedClientService.loadAuthorizedClient("github", "58421"))
                .thenReturn(new OAuth2AuthorizedClient(registration, "58421", accessToken));
    }

    private OAuth2AuthenticationToken token() {
        return new OAuth2AuthenticationToken(new DefaultOAuth2User(List.of(),
                Map.of("id", 58421, "login", "octocat", "email", "octocat@example.com"), "id"), List.of(), "github");
    }
}
