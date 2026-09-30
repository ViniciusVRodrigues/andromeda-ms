package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.azure.application.dto.AzureOAuthCallbackInput;
import andromeda.sei.ms.feature.azure.application.usecase.CompleteAzureOAuthConnectionUseCase;
import andromeda.sei.ms.feature.azure.domain.exception.AzureOrgUrlInvalidException;
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

/**
 * Microsoft callback of the Azure DevOps OAuth connection. Spring has already matched the
 * {@code state} to the stored authorization request; the organization URL is read back from that
 * state ({@link AzureOAuthOrgUrlState}) — never from a token claim (the old {@code iss} fallback,
 * azure-repos-provider D7). Persistence and validation live in
 * {@link CompleteAzureOAuthConnectionUseCase}; a rejected URL answers {@code 422
 * AZURE_ORG_URL_INVALID}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final int UNPROCESSABLE_ENTITY = 422;

    private final JwtService jwtService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final CompleteAzureOAuthConnectionUseCase completeAzureOAuthConnectionUseCase;

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

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2AuthorizedClient authorizedClient = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        AzureOAuthCallbackInput input = new AzureOAuthCallbackInput(
                claims.get("orgId", String.class),
                claims.getSubject(),
                AzureOAuthOrgUrlState.decode(request.getParameter("state")).orElse(null),
                authorizedClient.getAccessToken().getTokenValue(),
                authorizedClient.getRefreshToken() != null ? authorizedClient.getRefreshToken().getTokenValue() : null,
                authorizedClient.getAccessToken().getExpiresAt());

        request.getSession().removeAttribute("ANDROMEDA_JWT");
        response.setContentType("application/json");
        try {
            completeAzureOAuthConnectionUseCase.execute(input);
        } catch (AzureOrgUrlInvalidException e) {
            log.warn("Azure OAuth callback rejected for org={}: {}", input.organizationId(), e.getMessage());
            response.setStatus(UNPROCESSABLE_ENTITY);
            response.getWriter().write("{\"code\":\"" + e.getCode() + "\",\"message\":\""
                    + e.getMessage().replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"connected\":true}");
    }
}
