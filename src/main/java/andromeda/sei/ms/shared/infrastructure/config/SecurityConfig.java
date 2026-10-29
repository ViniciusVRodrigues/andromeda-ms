package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.feature.agent.infrastructure.security.AgentTokenAuthFilter;
import andromeda.sei.ms.feature.connector.infrastructure.security.ConnectorTokenAuthFilter;
import andromeda.sei.ms.shared.infrastructure.security.AzureOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.CompositeOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.GitHubOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.JwtAuthenticationFilter;
import andromeda.sei.ms.feature.auth.application.usecase.ConsumeOAuthLinkTicketUseCase;
import andromeda.sei.ms.shared.infrastructure.security.OAuthTicketAuthorizationFilter;
import andromeda.sei.ms.shared.infrastructure.security.TicketOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.OAuth2FailureHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ConnectorTokenAuthFilter connectorTokenAuthFilter;
    private final AgentTokenAuthFilter agentTokenAuthFilter;
    private final GitHubOAuth2SuccessHandler gitHubOAuth2SuccessHandler;
    private final AzureOAuth2SuccessHandler azureOAuth2SuccessHandler;
    private final OAuth2FailureHandler oauth2FailureHandler;
    private final ConsumeOAuthLinkTicketUseCase consumeOAuthLinkTicketUseCase;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Cookie do nonce do OAuth: Secure em dev/prod, desligado no perfil local (http). */
    @Value("${app.oauth.cookie-secure:true}")
    private boolean oauthCookieSecure;

    static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/invite/complete",
            "/api/v1/auth/github",
            "/api/v1/auth/github/callback",
            "/api/v1/auth/email-verification",         // send OTP — pre-auth
            "/api/v1/auth/email-verification/confirm", // confirm OTP — pre-auth
            "/api/v1/auth/password-reset",             // solicitar redefinição — pre-auth
            "/api/v1/auth/password-reset/confirm",     // redefinir com token — pre-auth
            "/api/v1/permissions",      // lista de permissões disponíveis — sem auth (usada no signup)
            "/api/v1/organizations",    // onboarding — cria org + primeiro admin
            "/api/v1/organizations/name-availability", // checagem durante o wizard — pre-auth
            "/api/v1/users/email-availability",        // checagem durante o wizard — pre-auth
            "/api/v1/usage-events/public",             // funil anônimo do cadastro/convite — pre-auth, só JSON
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/auth/oauth2/authorization/*", // popup do OAuth: o ticket de uso unico autoriza
            "/api/v1/auth/oauth2/callback/*",      // retorno do provedor
            "/connector/*/health",      // connector health — public, no auth
            "/agent/v1/device/code",    // pareamento do agente local (RFC 8628) — sem credencial, com rate limit por IP
            "/agent/v1/device/token"    // troca do device_code e refresh — sem credencial, com rate limit por IP
    };

    // Connector endpoints authenticated by ConnectorTokenAuthFilter — not by JWT
    private static final String[] CONNECTOR_ENDPOINTS = {
            "/connector/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(CONNECTOR_ENDPOINTS).permitAll() // ConnectorTokenAuthFilter handles auth
                        .anyRequest().authenticated()
                )
                // Nada do SecurityContext fica em sessao: a sessao serve so ao AuthorizationRequest e ao
                // LinkBinding do popup; a identidade do app e sempre o JWT (perfil-integracoes D3).
                .securityContext(ctx -> ctx.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                // 401 da API nao guarda a requisicao em sessao (HttpSessionRequestCache criaria uma).
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ep -> ep
                                .authorizationRequestResolver(
                                        new TicketOAuth2AuthorizationRequestResolver(clientRegistrationRepository)))
                        .loginProcessingUrl("/api/v1/auth/oauth2/callback/*")
                        .failureHandler(oauth2FailureHandler)
                        .successHandler(new CompositeOAuth2SuccessHandler(
                                gitHubOAuth2SuccessHandler, azureOAuth2SuccessHandler))
                )
                .exceptionHandling(apiAnswersUnauthorized())
                .addFilterBefore(new OAuthTicketAuthorizationFilter(consumeOAuthLinkTicketUseCase, frontendUrl,
                        oauthCookieSecure), OAuth2AuthorizationRequestRedirectFilter.class)
                .addFilterBefore(connectorTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(agentTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * O oauth2Login instala um entry point que redireciona (302) para a tela de login do provedor.
     * Para a API isso é errado: quem chama /api/** sem token recebe 401 e o cliente decide o que
     * fazer. O redirect continua valendo fora da API (fluxo OAuth no navegador).
     */
    static Customizer<ExceptionHandlingConfigurer<HttpSecurity>> apiAnswersUnauthorized() {
        return ex -> ex.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"));
    }

    @Bean
    public FilterRegistrationBean<ConnectorTokenAuthFilter> connectorTokenFilterRegistration(
            ConnectorTokenAuthFilter filter) {
        FilterRegistrationBean<ConnectorTokenAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); // runs only inside the Spring Security filter chain, not as a standalone servlet filter
        return bean;
    }

    @Bean
    public FilterRegistrationBean<AgentTokenAuthFilter> agentTokenFilterRegistration(AgentTokenAuthFilter filter) {
        FilterRegistrationBean<AgentTokenAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); // only inside the Spring Security chain, like the connector filter
        return bean;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
