package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class CompositeOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final GitHubOAuth2AuthorizationRequestResolver githubResolver;
    private final AzureOAuth2AuthorizationRequestResolver azureResolver;

    public CompositeOAuth2AuthorizationRequestResolver(
            GitHubOAuth2AuthorizationRequestResolver githubResolver,
            AzureOAuth2AuthorizationRequestResolver azureResolver) {
        this.githubResolver = githubResolver;
        this.azureResolver = azureResolver;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = extractRegistrationId(request);
        if ("azure".equals(registrationId)) {
            return azureResolver.resolve(request);
        }
        return githubResolver.resolve(request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        if ("azure".equals(clientRegistrationId)) {
            return azureResolver.resolve(request, clientRegistrationId);
        }
        return githubResolver.resolve(request, clientRegistrationId);
    }

    private String extractRegistrationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : null;
    }
}
