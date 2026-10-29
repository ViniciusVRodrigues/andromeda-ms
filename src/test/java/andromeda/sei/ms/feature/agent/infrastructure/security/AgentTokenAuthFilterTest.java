package andromeda.sei.ms.feature.agent.infrastructure.security;

import andromeda.sei.ms.feature.agent.application.usecase.AuthenticateAgentTokenUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentAuthorities;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.infrastructure.security.JwtAuthenticationFilter;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentTokenAuthFilterTest {

    private final AuthenticateAgentTokenUseCase authenticate = mock(AuthenticateAgentTokenUseCase.class);
    private final AgentTokenAuthFilter filter = new AgentTokenAuthFilter(authenticate);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private static MockHttpServletRequest request(String uri, String bearer, String version) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
        r.setRequestURI(uri);
        if (bearer != null) r.addHeader("Authorization", "Bearer " + bearer);
        if (version != null) r.addHeader(AgentTokenAuthFilter.VERSION_HEADER, version);
        return r;
    }

    @Test
    void should_ignoreNonAgentRoutesAndThePublicDeviceRoutes() throws Exception {
        for (String uri : List.of("/api/v1/auth/me", "/connector/acme/tok/api/x", "/agent/v1/device/code",
                "/agent/v1/device/token")) {
            filter.doFilter(request(uri, null, null), new MockHttpServletResponse(), (req, res) -> { });
        }
        verifyNoInteractions(authenticate);
    }

    @Test
    void should_answer401AgentTokenInvalidWithoutCallingTheChain() throws Exception {
        when(authenticate.execute(any(), any())).thenThrow(new AgentTokenInvalidException());
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> chained = new AtomicReference<>(false);
        filter.doFilter(request("/agent/v1/heartbeat", "adt_bad", "0.2.0"), response, (q, s) -> chained.set(true));
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"AGENT_TOKEN_INVALID\"");
        assertThat(chained.get()).isFalse();
    }

    @Test
    void should_answer426WithMinVersionForUnsupportedVersions() throws Exception {
        AgentPrincipal principal = new AgentPrincipal("org-1", "user-1", "dev-1");
        when(authenticate.execute(eq("adt_ok"), eq("0.0.1")))
                .thenReturn(new AuthenticateAgentTokenUseCase.Result(principal, false, "0.1.0"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/agent/v1/heartbeat", "adt_ok", "0.0.1"), response, (q, s) -> { });
        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getContentAsString()).contains("AGENT_VERSION_UNSUPPORTED").contains("\"minVersion\":\"0.1.0\"");
    }

    @Test
    void should_setThePrincipalWithOnlyTheAgentAuthoritiesAndClearAfterwards() throws Exception {
        AgentPrincipal principal = new AgentPrincipal("org-1", "user-1", "dev-1");
        when(authenticate.execute(eq("adt_ok"), eq("0.2.0")))
                .thenReturn(new AuthenticateAgentTokenUseCase.Result(principal, true, "0.1.0"));
        AtomicReference<Authentication> seen = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        filter.doFilter(request("/agent/v1/heartbeat", "adt_ok", "0.2.0"), new MockHttpServletResponse(), (q, s) -> {
            seen.set(SecurityContextHolder.getContext().getAuthentication());
            tenant.set(TenantContextHolder.get());
        });
        assertThat(seen.get().getPrincipal()).isEqualTo(principal);
        assertThat(seen.get().getAuthorities().stream().map(Object::toString))
                .containsExactlyInAnyOrder(AgentAuthorities.AGENT_USAGE_WRITE, AgentAuthorities.AGENT_TASKS_READ);
        assertThat(tenant.get()).isEqualTo("org-1");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void should_notLetTheJwtFilterProcessAgentRoutes() throws Exception {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes()));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);
        EffectivePermissionService permissions = mock(EffectivePermissionService.class);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, permissions);
        String jwt = jwtService.generate("user-1", "org-1", List.of());

        AtomicReference<Authentication> seen = new AtomicReference<>();
        jwtFilter.doFilter(request("/agent/v1/heartbeat", jwt, null), new MockHttpServletResponse(),
                (q, s) -> seen.set(SecurityContextHolder.getContext().getAuthentication()));
        assertThat(seen.get()).isNull();
        verifyNoInteractions(permissions);
    }

    @Test
    void should_denyARouteThatRequiresAUserPermissionToTheAgentPrincipal() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            Guarded guarded = ctx.getBean(Guarded.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AgentPrincipal("org-1", "user-1", "dev-1"), null,
                    List.of(new SimpleGrantedAuthority(AgentAuthorities.AGENT_USAGE_WRITE),
                            new SimpleGrantedAuthority(AgentAuthorities.AGENT_TASKS_READ))));
            assertThatThrownBy(guarded::readOwnUsage).isInstanceOf(AccessDeniedException.class);
            assertThat(guarded.agentWrite()).isEqualTo("ok");
        }
    }

    @Test
    void should_denyAUserTokenOnAnAgentOnlyRoute() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(Cfg.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "user", null, List.of(new SimpleGrantedAuthority("AI_READ_OWN"))));
            assertThatThrownBy(() -> ctx.getBean(Guarded.class).agentWrite()).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Configuration
    @EnableMethodSecurity
    static class Cfg {
        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    static class Guarded {
        @PreAuthorize("hasAuthority('AI_READ_OWN')")
        public String readOwnUsage() {
            return "usage";
        }

        @PreAuthorize("hasAuthority('AGENT_USAGE_WRITE')")
        public String agentWrite() {
            return "ok";
        }
    }
}
