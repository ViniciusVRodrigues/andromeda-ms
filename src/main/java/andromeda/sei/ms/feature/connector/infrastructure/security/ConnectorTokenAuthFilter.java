package andromeda.sei.ms.feature.connector.infrastructure.security;

import andromeda.sei.ms.feature.connector.application.usecase.ResolveConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenRevokedException;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class ConnectorTokenAuthFilter extends OncePerRequestFilter {

    private static final String CONNECTOR_PATH_PREFIX = "/connector/";
    private static final String HEALTH_SUFFIX = "/health";

    private final ResolveConnectorTokenUseCase resolveConnectorTokenUseCase;
    private final OrganizationRepository organizationRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(CONNECTOR_PATH_PREFIX)) {
            return true;
        }
        // Public health endpoint: /connector/{orgSlug}/health — skip auth
        String[] segments = uri.split("/");
        // segments: ["", "connector", "{orgSlug}", ...]
        return segments.length == 4 && segments[3].equals("health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String[] segments = request.getRequestURI().split("/");
            // URI: /connector/{orgSlug}/{rawToken}/api/...
            // segments: ["", "connector", "{orgSlug}", "{rawToken}", "api", ...]
            if (segments.length < 4) {
                rejectUnauthorized(response, "Invalid connector path");
                return;
            }

            String orgSlug = segments[2];
            String rawToken = segments[3];

            Organization organization = organizationRepository.findBySlug(orgSlug)
                    .orElseThrow(() -> new OrganizationNotFoundException(orgSlug));

            String tokenHash = sha256(rawToken);
            ConnectorToken token = resolveConnectorTokenUseCase.execute(
                    organization.getId(), tokenHash);

            if (!token.getOrganizationId().equals(organization.getId())) {
                rejectUnauthorized(response, "Token does not belong to the specified organization");
                return;
            }

            TenantContextHolder.set(organization.getId());

            var auth = new UsernamePasswordAuthenticationToken(
                    token.getUserId(),
                    null,
                    List.of(new SimpleGrantedAuthority("CONNECTOR_TOKEN_AUTH"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContextHolder.clear();
                SecurityContextHolder.clearContext();
            }

        } catch (ConnectorTokenNotFoundException | ConnectorTokenRevokedException |
                 OrganizationNotFoundException e) {
            rejectUnauthorized(response, "Unauthorized");
        }
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        response.getWriter().write(body);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
