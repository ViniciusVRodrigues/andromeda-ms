package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.LinkGithubUseCase;
import andromeda.sei.ms.feature.provideraccount.application.dto.LinkProviderAccountInput;
import andromeda.sei.ms.feature.provideraccount.application.usecase.LinkProviderAccountUseCase;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final LinkGithubUseCase linkGithubUseCase;
    private final LinkProviderAccountUseCase linkProviderAccountUseCase;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        Object binding = request.getSession().getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        if (!(binding instanceof LinkBinding link)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String userId = link.userId();
        String orgId = link.organizationId();

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        Object idAttr = oauthToken.getPrincipal().getAttribute("id");
        String githubId = idAttr != null ? idAttr.toString() : null;

        linkGithubUseCase.execute(userId, orgId, githubId);

        persistProviderAccount(oauthToken, userId, orgId, githubId);

        request.getSession().removeAttribute(LinkBinding.SESSION_ATTRIBUTE);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write("{\"linked\":true}");
    }

    /**
     * Wrapped so a persistence failure (e.g. duplicate-account conflict, encryption error) never
     * breaks the legacy {@code User.githubId} link or the {@code {"linked":true}} response
     * (design.md D4/Risks; spec: credential persistence failure does not break the legacy link).
     * Never logs token material — only non-sensitive metadata.
     */
    private void persistProviderAccount(OAuth2AuthenticationToken oauthToken, String userId, String orgId,
                                         String githubId) {
        try {
            OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                    "github", oauthToken.getName());
            if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
                log.warn("No authorized client/access token available for GitHub OAuth success: userId={} orgId={}",
                        userId, orgId);
                return;
            }

            String login = attributeAsString(oauthToken, "login");
            String email = attributeAsString(oauthToken, "email");
            List<String> scopes = authorizedClient.getAccessToken().getScopes() != null
                    ? List.copyOf(authorizedClient.getAccessToken().getScopes())
                    : List.of();

            LinkProviderAccountInput input = new LinkProviderAccountInput(
                    orgId, userId, Provider.GITHUB, githubId, login, email,
                    authorizedClient.getAccessToken().getTokenValue(),
                    authorizedClient.getRefreshToken() != null
                            ? authorizedClient.getRefreshToken().getTokenValue() : null,
                    authorizedClient.getAccessToken().getExpiresAt(),
                    scopes);

            linkProviderAccountUseCase.execute(input);
        } catch (Exception e) {
            log.warn("Failed to persist GitHub provider account: userId={} orgId={} exceptionType={} message=\"{}\"",
                    userId, orgId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String attributeAsString(OAuth2AuthenticationToken oauthToken, String attribute) {
        Object value = oauthToken.getPrincipal().getAttribute(attribute);
        return value != null ? value.toString() : null;
    }
}
