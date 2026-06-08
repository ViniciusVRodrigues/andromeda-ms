package andromeda.sei.ms.shared.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;

public class CompositeOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final GitHubOAuth2SuccessHandler githubHandler;
    private final AzureOAuth2SuccessHandler azureHandler;

    public CompositeOAuth2SuccessHandler(GitHubOAuth2SuccessHandler githubHandler,
                                          AzureOAuth2SuccessHandler azureHandler) {
        this.githubHandler = githubHandler;
        this.azureHandler = azureHandler;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken
                && "azure".equals(oauthToken.getAuthorizedClientRegistrationId())) {
            azureHandler.onAuthenticationSuccess(request, response, authentication);
        } else {
            githubHandler.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
