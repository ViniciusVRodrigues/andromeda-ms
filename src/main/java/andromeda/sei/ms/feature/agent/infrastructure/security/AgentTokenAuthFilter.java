package andromeda.sei.ms.feature.agent.infrastructure.security;

import andromeda.sei.ms.feature.agent.application.usecase.AuthenticateAgentTokenUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentAuthorities;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
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
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Authenticates {@code /agent/v1/**} (except the two public device routes) by the Bearer access token
 * of a paired device. The principal is an {@link AgentPrincipal} with only the agent authorities,
 * never the permissions of the person, so the token opens nothing under {@code /api/**} (where the
 * JWT filter does not read it either).
 */
@Component
@RequiredArgsConstructor
public class AgentTokenAuthFilter extends OncePerRequestFilter {

    public static final String PATH_PREFIX = "/agent/";
    public static final String VERSION_HEADER = "X-Andromeda-Agent-Version";
    private static final String BEARER = "Bearer ";
    private static final Set<String> PUBLIC_PATHS = Set.of("/agent/v1/device/code", "/agent/v1/device/token");

    private final AuthenticateAgentTokenUseCase authenticateUseCase;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(PATH_PREFIX) || PUBLIC_PATHS.contains(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthenticateAgentTokenUseCase.Result result;
        try {
            result = authenticateUseCase.execute(bearer(request), request.getHeader(VERSION_HEADER));
        } catch (AgentTokenInvalidException e) {
            write(response, HttpStatus.UNAUTHORIZED, "{\"code\":\"" + e.getCode() + "\",\"message\":\""
                    + e.getMessage() + "\",\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }

        if (!result.versionSupported()) {
            write(response, HttpStatus.UPGRADE_REQUIRED, "{\"code\":\"AGENT_VERSION_UNSUPPORTED\",\"message\":"
                    + "\"Versão do agente não suportada\",\"minVersion\":\"" + result.minVersion()
                    + "\",\"timestamp\":\"" + Instant.now() + "\"}");
            return;
        }

        AgentPrincipal principal = result.principal();
        TenantContextHolder.set(principal.organizationId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority(AgentAuthorities.AGENT_USAGE_WRITE),
                new SimpleGrantedAuthority(AgentAuthorities.AGENT_TASKS_READ))));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()).trim() : null;
    }

    private static void write(HttpServletResponse response, HttpStatus status, String body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }
}
