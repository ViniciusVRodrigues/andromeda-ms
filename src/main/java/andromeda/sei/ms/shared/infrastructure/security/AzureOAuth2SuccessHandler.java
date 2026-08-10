package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureOrgLevelDataUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AzureOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final EncryptionService encryptionService;
    private final AzureConnectionRepository azureConnectionRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String jwt = (String) request.getSession().getAttribute("ANDROMEDA_JWT");
        if (jwt == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        Claims claims;
        try {
            claims = jwtService.parse(jwt);
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String orgId = claims.get("orgId", String.class);
        String userId = claims.getSubject();

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String encryptedAccessToken = encryptionService.encrypt(
                authorizedClient.getAccessToken().getTokenValue());
        String encryptedRefreshToken = authorizedClient.getRefreshToken() != null
                ? encryptionService.encrypt(authorizedClient.getRefreshToken().getTokenValue())
                : null;
        Instant tokenExpiresAt = authorizedClient.getAccessToken().getExpiresAt();

        // Retrieve org URL from the Azure DevOps user info (sub claim or custom attribute)
        Object orgUrl = oauthToken.getPrincipal().getAttribute("iss");
        String azureOrgUrl = orgUrl != null ? orgUrl.toString() : "https://dev.azure.com";

        AzureConnection existing = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId).orElse(null);

        Instant now = Instant.now();
        AzureConnection saved;
        if (existing != null) {
            existing.setAccessToken(encryptedAccessToken);
            existing.setRefreshToken(encryptedRefreshToken);
            existing.setTokenExpiresAt(tokenExpiresAt);
            existing.setStatus(AzureConnectionStatus.CONNECTED);
            existing.setConsecutiveFailures(0);
            existing.setAuthorizedByUserId(userId);
            saved = azureConnectionRepository.save(existing);
        } else {
            AzureConnection connection = new AzureConnection(
                    null, orgId, azureOrgUrl,
                    encryptedAccessToken, encryptedRefreshToken, tokenExpiresAt,
                    userId, AzureConnectionStatus.CONNECTED,
                    null, 0, null, null,
                    true, now, now, null,
                    AzureAuthMethod.OAUTH, null);
            saved = azureConnectionRepository.save(connection);
        }

        try {
            syncAzureOrgLevelDataUseCase.execute(saved);
        } catch (Exception e) {
            log.warn("Org-level Azure sync failed after Entra ID connection for org={}: {}", orgId, e.getMessage(), e);
        }

        request.getSession().removeAttribute("ANDROMEDA_JWT");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write("{\"connected\":true}");
    }
}
