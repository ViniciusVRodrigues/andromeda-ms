package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.shared.infrastructure.security.AzureOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.AzureOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.CompositeOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.CompositeOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.GitHubOAuth2AuthorizationRequestResolver;
import andromeda.sei.ms.shared.infrastructure.security.GitHubOAuth2SuccessHandler;
import andromeda.sei.ms.shared.infrastructure.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
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
    private final GitHubOAuth2SuccessHandler gitHubOAuth2SuccessHandler;
    private final AzureOAuth2SuccessHandler azureOAuth2SuccessHandler;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/invite/complete",
            "/api/v1/auth/github",
            "/api/v1/auth/github/callback",
            "/api/v1/organizations",    // onboarding — cria org + primeiro admin
            "/actuator/health",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/login/oauth2/code/azure"
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
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(ep -> ep
                                .authorizationRequestResolver(new CompositeOAuth2AuthorizationRequestResolver(
                                        githubResolver, azureResolver)))
                        .successHandler(new CompositeOAuth2SuccessHandler(
                                gitHubOAuth2SuccessHandler, azureOAuth2SuccessHandler))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
