package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates the Bearer JWT. Since project-scoped-permissions D5 the token only identifies the
 * caller ({@code sub}, {@code orgId}): the authorities are the effective organization-level codes
 * resolved on the server ({@link EffectivePermissionService}, cached), so a revoked permission or a
 * deactivated user takes effect on the next request, without a new login. The token's
 * {@code permissions} claim is kept for the UI only and is ignored here.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ORG_ID_CLAIM = "orgId";

    private final JwtService jwtService;
    private final EffectivePermissionService effectivePermissionService;

    /**
     * Connector routes are authenticated by the token in the path; the {@code Authorization} header
     * there belongs to the person's GitHub (AIEF extension) and must never be read or validated here.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/connector/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        var token = extractToken(request);
        if (token != null) {
            authenticate(token);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();  // sempre limpar o ThreadLocal ao fim do request
        }
    }

    private void authenticate(String token) {
        Claims claims;
        try {
            claims = jwtService.parse(token);
        } catch (JwtException ignored) {
            return; // Token inválido — SecurityContext fica vazio, Spring Security retorna 401
        }
        String organizationId = claims.get(ORG_ID_CLAIM, String.class);
        Optional<UserPermissionSnapshot> snapshot =
                effectivePermissionService.snapshot(organizationId, claims.getSubject());
        if (snapshot.isEmpty()) {
            return; // usuário inexistente ou desativado: token válido não basta — 401
        }
        var userDetails = AndromedaUserDetails.builder()
                .userId(claims.getSubject())
                .organizationId(organizationId)
                .permissions(effectivePermissionService.orgPermissions(snapshot.get()))
                .permissionSnapshot(snapshot.get())
                .build();

        TenantContextHolder.set(organizationId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    private String extractToken(HttpServletRequest request) {
        var header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
