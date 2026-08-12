package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.LinkGithubUseCase;
import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.application.usecase.LinkProviderAccountUseCase;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubOAuth2SuccessHandlerTest {

    @Mock private JwtService jwtService;
    @Mock private LinkGithubUseCase linkGithubUseCase;
    @Mock private LinkProviderAccountUseCase linkProviderAccountUseCase;
    @Mock private OAuth2AuthorizedClientService authorizedClientService;

    @InjectMocks
    private GitHubOAuth2SuccessHandler handler;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HttpSession session;
    @Mock private Claims claims;

    @Test
    void shouldPersistProviderAccount_andKeepLegacyLink_onSuccess() throws Exception {
        setUpSession();
        OAuth2AuthenticationToken token = oauthToken(Map.of(
                "id", 58421, "login", "octocat", "email", "octocat@example.com"));

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-plain", Instant.now(), Instant.now().plusSeconds(3600), scopes("repo", "read:org"));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                clientRegistration(), "58421", accessToken);
        when(authorizedClientService.loadAuthorizedClient("github", "58421")).thenReturn(authorizedClient);

        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        handler.onAuthenticationSuccess(request, response, token);

        verify(linkGithubUseCase).execute("user-1", "org-1", "58421");

        ArgumentCaptor<LinkProviderAccountInput> captor = ArgumentCaptor.forClass(LinkProviderAccountInput.class);
        verify(linkProviderAccountUseCase).execute(captor.capture());
        assertThat(captor.getValue().organizationId()).isEqualTo("org-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().externalUserId()).isEqualTo("58421");
        assertThat(captor.getValue().externalLogin()).isEqualTo("octocat");
        assertThat(captor.getValue().externalEmail()).isEqualTo("octocat@example.com");
        assertThat(captor.getValue().accessToken()).isEqualTo("access-plain");

        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertThat(body.toString()).isEqualTo("{\"linked\":true}");
        verify(session).removeAttribute("ANDROMEDA_JWT");
    }

    @Test
    void shouldStillCompleteLegacyLinkAndResponse_whenProviderAccountPersistenceFails() throws Exception {
        setUpSession();
        OAuth2AuthenticationToken token = oauthToken(Map.of(
                "id", 58421, "login", "octocat", "email", "octocat@example.com"));

        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "access-plain", Instant.now(), Instant.now().plusSeconds(3600), scopes("repo"));
        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                clientRegistration(), "58421", accessToken);
        when(authorizedClientService.loadAuthorizedClient("github", "58421")).thenReturn(authorizedClient);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(linkProviderAccountUseCase).execute(any());

        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        handler.onAuthenticationSuccess(request, response, token);

        verify(linkGithubUseCase).execute("user-1", "org-1", "58421");
        verify(response).setStatus(HttpServletResponse.SC_OK);
        assertThat(body.toString()).isEqualTo("{\"linked\":true}");
    }

    @Test
    void shouldReturn401_whenSessionHasNoJwt() throws Exception {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("ANDROMEDA_JWT")).thenReturn(null);
        OAuth2AuthenticationToken token = oauthToken(Map.of("id", 58421));

        handler.onAuthenticationSuccess(request, response, token);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(linkGithubUseCase, never()).execute(any(), any(), any());
        verify(linkProviderAccountUseCase, never()).execute(any());
    }

    private void setUpSession() {
        when(request.getSession()).thenReturn(session);
        when(session.getAttribute("ANDROMEDA_JWT")).thenReturn("jwt-token");
        when(jwtService.parse("jwt-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("user-1");
        lenient().when(claims.get("orgId", String.class)).thenReturn("org-1");
    }

    private OAuth2AuthenticationToken oauthToken(Map<String, Object> attributes) {
        OAuth2User principal = new DefaultOAuth2User(List.of(), attributes, "id");
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(principal, List.of(), "github");
        return token;
    }

    private Set<String> scopes(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private org.springframework.security.oauth2.client.registration.ClientRegistration clientRegistration() {
        return org.springframework.security.oauth2.client.registration.ClientRegistration
                .withRegistrationId("github")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .build();
    }
}
