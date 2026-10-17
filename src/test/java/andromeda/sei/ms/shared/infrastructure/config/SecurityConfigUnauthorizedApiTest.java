package andromeda.sei.ms.shared.infrastructure.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API1: sem token, /api/** responde 401 em vez do 302 que o entry point de login (oauth2Login no
 * SecurityConfig; formLogin aqui, que instala o mesmo tipo de entry point) produziria.
 */
@SpringJUnitWebConfig(SecurityConfigUnauthorizedApiTest.TestSecurity.class)
class SecurityConfigUnauthorizedApiTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void should_answer401_notARedirect_whenTheApiIsCalledWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
    }

    /* O entry point do login só casa com navegador (Accept text/html); sem Accept, fora da API,
     * cai no padrão (401), o que também serve para cliente que não é navegador. */
    @Test
    void should_keepTheLoginRedirect_outsideTheApi() throws Exception {
        mockMvc.perform(get("/somewhere").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class TestSecurity {

        @Bean
        SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                    .formLogin(Customizer.withDefaults())
                    .exceptionHandling(SecurityConfig.apiAnswersUnauthorized())
                    .build();
        }

        @Bean
        StubController stubController() {
            return new StubController();
        }
    }

    @RestController
    static class StubController {
        @GetMapping({"/api/v1/projects", "/somewhere"})
        String ok() {
            return "ok";
        }
    }
}
