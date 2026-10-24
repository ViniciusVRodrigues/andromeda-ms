package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.LinkGithubUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.application.usecase.LinkProviderAccountUseCase;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.exception.ProviderAccountAlreadyLinkedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static andromeda.sei.ms.shared.infrastructure.security.OAuthReturnRedirect.Code;

/**
 * Callback do GitHub (perfil-integracoes D1/D3/D4/D7). Termina sempre em redirect para
 * {@code /oauth/retorno}. Ordem: confere o nonce do cookie contra o hash da sessao; grava a conta do
 * provedor (que confere duplicidade antes de qualquer escrita); so depois grava {@code User.githubId}.
 * Falha vira {@code OAUTH_LINK_FAILED} e e logada sem material de token.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final OAuthReturnRedirect.Provider GITHUB = OAuthReturnRedirect.Provider.GITHUB;

    private final LinkGithubUseCase linkGithubUseCase;
    private final LinkProviderAccountUseCase linkProviderAccountUseCase;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final OAuthFlowCompletion completion;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String principalName = authentication.getName();

        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        if (!(attribute instanceof LinkBinding binding)
                || binding.purpose() != OAuthLinkPurpose.GITHUB_PROFILE
                || !OAuthNonceCookie.matches(request, binding.nonceHash())) {
            completion.error(request, response, GITHUB, Code.OAUTH_SESSION_MISMATCH, principalName);
            return;
        }

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        Object idAttr = oauthToken.getPrincipal().getAttribute("id");
        String githubId = idAttr != null ? idAttr.toString() : null;
        String userId = binding.userId();
        String orgId = binding.organizationId();

        Code failure = null;
        try {
            if (githubId == null) {
                throw new IllegalStateException("GitHub principal has no id");
            }
            linkProviderAccountUseCase.execute(accountInput(oauthToken, userId, orgId, githubId));
            linkGithubUseCase.execute(userId, orgId, githubId);
        } catch (ProviderAccountAlreadyLinkedException e) {
            log.warn("GitHub account already linked to another user: userId={} orgId={}", userId, orgId);
            failure = Code.PROVIDER_ACCOUNT_ALREADY_LINKED;
        } catch (RuntimeException e) {
            log.error("Failed to link GitHub account: userId={} orgId={} exceptionType={}",
                    userId, orgId, e.getClass().getSimpleName(), e);
            failure = Code.OAUTH_LINK_FAILED;
        }

        if (failure != null) {
            completion.error(request, response, GITHUB, failure, principalName);
        } else {
            completion.finish(request, response, GITHUB, OAuthReturnRedirect.Status.LINKED, null, principalName);
        }
    }

    private LinkProviderAccountInput accountInput(OAuth2AuthenticationToken oauthToken, String userId,
                                                  String orgId, String githubId) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                GITHUB.id(), oauthToken.getName());
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No authorized client/access token available for GitHub");
        }
        List<String> scopes = client.getAccessToken().getScopes() != null
                ? List.copyOf(client.getAccessToken().getScopes()) : List.of();
        return new LinkProviderAccountInput(
                orgId, userId, Provider.GITHUB, githubId,
                attribute(oauthToken, "login"), attribute(oauthToken, "email"),
                client.getAccessToken().getTokenValue(),
                client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null,
                client.getAccessToken().getExpiresAt(), scopes);
    }

    private String attribute(OAuth2AuthenticationToken oauthToken, String name) {
        Object value = oauthToken.getPrincipal().getAttribute(name);
        return value != null ? value.toString() : null;
    }
}
