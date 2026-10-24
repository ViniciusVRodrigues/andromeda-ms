package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.azure.application.dto.AzureOAuthCallbackInput;
import andromeda.sei.ms.feature.azure.application.usecase.CompleteAzureOAuthConnectionUseCase;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
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

import static andromeda.sei.ms.shared.infrastructure.security.OAuthReturnRedirect.Code;

/**
 * Microsoft callback of the Azure DevOps OAuth connection (perfil-integracoes D1/D3/D4). Org, user and
 * the organization URL come from the {@link LinkBinding} the one-time ticket created (AZURE_CONFIGURE was
 * checked when the ticket was issued); the nonce cookie must match the session, and the URL carried by
 * the {@code state} must equal the ticket's. Every path ends in a redirect to the app's return page via
 * {@link OAuthFlowCompletion}: {@code connected}, or {@code OAUTH_SESSION_MISMATCH},
 * {@code AZURE_ORG_URL_INVALID}, {@code OAUTH_PROVIDER_ERROR}. Never 401 or JSON in the popup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final OAuthReturnRedirect.Provider AZURE = OAuthReturnRedirect.Provider.AZURE;

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final CompleteAzureOAuthConnectionUseCase completeAzureOAuthConnectionUseCase;
    private final OAuthFlowCompletion completion;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String principalName = authentication.getName();

        HttpSession session = request.getSession(false);
        Object attribute = session == null ? null : session.getAttribute(LinkBinding.SESSION_ATTRIBUTE);
        if (!(attribute instanceof LinkBinding binding)
                || binding.purpose() != OAuthLinkPurpose.AZURE_ORG_CONNECT
                || !OAuthNonceCookie.matches(request, binding.nonceHash())
                || !AzureOAuthOrgUrlState.decode(request.getParameter("state"))
                        .filter(url -> url.equals(binding.azureOrgUrl())).isPresent()) {
            completion.error(request, response, AZURE, Code.OAUTH_SESSION_MISMATCH, principalName);
            return;
        }

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), principalName);
        if (client == null || client.getAccessToken() == null) {
            log.warn("No authorized client/access token for Azure OAuth: orgId={}", binding.organizationId());
            completion.error(request, response, AZURE, Code.OAUTH_PROVIDER_ERROR, principalName);
            return;
        }

        AzureOAuthCallbackInput input = new AzureOAuthCallbackInput(
                binding.organizationId(), binding.userId(), binding.azureOrgUrl(),
                client.getAccessToken().getTokenValue(),
                client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null,
                client.getAccessToken().getExpiresAt());

        Code failure = null;
        try {
            completeAzureOAuthConnectionUseCase.execute(input);
        } catch (AzureOrgUrlInvalidException e) {
            log.warn("Azure OAuth callback rejected for org={}: {}", input.organizationId(), e.getMessage());
            failure = Code.AZURE_ORG_URL_INVALID;
        } catch (RuntimeException e) {
            log.error("Azure OAuth connection failed: orgId={} exceptionType={}",
                    input.organizationId(), e.getClass().getSimpleName(), e);
            failure = Code.OAUTH_PROVIDER_ERROR;
        }

        if (failure != null) {
            completion.error(request, response, AZURE, failure, principalName);
        } else {
            completion.finish(request, response, AZURE, OAuthReturnRedirect.Status.CONNECTED, null, principalName);
        }
    }
}
