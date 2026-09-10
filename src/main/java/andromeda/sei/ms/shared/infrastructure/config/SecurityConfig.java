package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.feature.connector.infrastructure.security.ConnectorTokenAuthFilter;
import andromeda.sei.ms.shared.infrastructure.security.AzureOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.AzureOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.CompositeOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.CompositeOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.GitHubOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.GitHubOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
    private final GitHubOAuth2SuccessHandler gitHubOAuth2SuccessHandler;
    private final AzureOAuth2SuccessHandler azureOAuth2SuccessHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
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
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/login/oauth2/code/azure",
            "/connector/*/health"       // connector health — public, no auth
    };

    // Connector endpoints authenticated by ConnectorTokenAuthFilter — not by JWT
    private static final String[] CONNECTOR_ENDPOINTS = {
            "/connector/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        GitHubOAuth2AuthorizationRequestResolver githubResolver =
                new GitHubOAuth2AuthorizationRequestResolver(clientRegistrationRepository);
        AzureOAuth2AuthorizationRequestResolver azureResolver =
                new AzureOAuth2AuthorizationRequestResolver(clientRegistrationRepository);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(CONNECTOR_ENDPOINTS).permitAll() // ConnectorTokenAuthFilter handles auth
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ep -> ep
                                .authorizationRequestResolver(new CompositeOAuth2AuthorizationRequestResolver(
                                        githubResolver, azureResolver)))
                        .successHandler(new CompositeOAuth2SuccessHandler(
                                gitHubOAuth2SuccessHandler, azureOAuth2SuccessHandler))
                )
                .addFilterBefore(connectorTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<ConnectorTokenAuthFilter> connectorTokenFilterRegistration(
            ConnectorTokenAuthFilter filter) {
        FilterRegistrationBean<ConnectorTokenAuthFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setEnabled(false); // runs only inside the Spring Security filter chain, not as a standalone servlet filter
        return bean;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
