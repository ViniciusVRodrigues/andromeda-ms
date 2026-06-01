package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.usecase.LinkGithubUseCase;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GitHubOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final LinkGithubUseCase linkGithubUseCase;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
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

        String userId = claims.getSubject();
        String orgId = claims.get("orgId", String.class);

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        Object idAttr = oauthToken.getPrincipal().getAttribute("id");
        String githubId = idAttr != null ? idAttr.toString() : null;

        linkGithubUseCase.execute(userId, orgId, githubId);

        request.getSession().removeAttribute("ANDROMEDA_JWT");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.getWriter().write("{\"linked\":true}");
    }
}
