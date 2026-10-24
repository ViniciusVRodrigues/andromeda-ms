package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.auth.application.dto.IssueOAuthLinkTicketInput;
import andromeda.sei.ms.feature.auth.application.usecase.IssueOAuthLinkTicketUseCase;
import andromeda.sei.ms.feature.auth.domain.OAuthLinkPurpose;
import andromeda.sei.ms.feature.provideraccount.domain.Provider;
import andromeda.sei.ms.feature.provideraccount.domain.UserProviderAccountRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.testsupport.MongoTestcontainersConfig;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Tasks 6.1/6.2 (perfil-integracoes): fluxo completo do GitHub contra um provedor simulado (JDK
 * HttpServer, sem dependencia nova) e o Mongo do Testcontainers. Prova que o ticket abre o fluxo,
 * que o callback grava a conta, que o app recebe o redirect {@code linked} e que nada do
 * SecurityContext fica em sessao (o JWT continua sendo a unica identidade).
 */
@SpringBootTest(properties = {
        "security.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQtMDEyMzQ1Njc4OXRlc3Qtb25seS1qd3Qtc2VjcmV0LTAxMjM0NTY3ODl0ZXN0LW9ubHktand0LXNlY3JldC0wMTIzNDU2Nzg5",
        "azure.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.frontend-url=http://app.test",
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret"
})
@Import(MongoTestcontainersConfig.class)
class OAuthLinkFlowIntegrationTest {

    private static final HttpServer PROVIDER = startProvider();
    private static final AtomicReference<String> TOKEN_REQUEST_BODY = new AtomicReference<>();
    private static final String EXTERNAL_ID = "424242";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MongoTestcontainersConfig::connectionString);
        String base = "http://127.0.0.1:" + PROVIDER.getAddress().getPort();
        registry.add("spring.security.oauth2.client.provider.github.authorization-uri", () -> base + "/authorize");
        registry.add("spring.security.oauth2.client.provider.github.token-uri", () -> base + "/token");
        registry.add("spring.security.oauth2.client.provider.github.user-info-uri", () -> base + "/user");
        registry.add("spring.security.oauth2.client.provider.github.user-name-attribute", () -> "id");
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/token", exchange -> {
                TOKEN_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respondJson(exchange, "{\"access_token\":\"gho_fake_access\",\"token_type\":\"bearer\","
                        + "\"scope\":\"read:user,user:email\"}");
            });
            server.createContext("/user", exchange -> respondJson(exchange,
                    "{\"id\":" + EXTERNAL_ID + ",\"login\":\"octo\",\"email\":\"octo@example.com\"}"));
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Autowired WebApplicationContext context;
    @Autowired IssueOAuthLinkTicketUseCase issueTicket;
    @Autowired UserRepository users;
    @Autowired UserProviderAccountRepository accounts;
    @Autowired JwtService jwtService;

    private MockMvc mvc;
    private String orgId;
    private String userId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        orgId = "org-" + UUID.randomUUID();
        userId = "user-" + UUID.randomUUID();
        Instant now = Instant.now();
        users.save(new User(userId, orgId, userId + "@example.com", "x", "Dev", null, null, null, null, null,
                null, true, true, null, now, now));
    }

    private String ticket() {
        return issueTicket.execute(new IssueOAuthLinkTicketInput(
                userId, orgId, List.of(), OAuthLinkPurpose.GITHUB_PROFILE, null)).ticket();
    }

    @Test
    void shouldLinkGithubEndToEndWithoutKeepingASecurityContextInSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult start = mvc.perform(get("/api/v1/auth/oauth2/authorization/github")
                        .param("ticket", ticket()).session(session))
                .andReturn();
        MockHttpServletResponse startResponse = start.getResponse();
        assertThat(startResponse.getStatus()).isEqualTo(302);
        String location = startResponse.getRedirectedUrl();
        assertThat(location).startsWith("http://127.0.0.1:" + PROVIDER.getAddress().getPort() + "/authorize");
        var query = UriComponentsBuilder.fromUriString(location).build().getQueryParams();
        String state = java.net.URLDecoder.decode(query.getFirst("state"), StandardCharsets.UTF_8);
        assertThat(state).isNotBlank();
        assertThat(query.getFirst("client_id")).isEqualTo("test-client");
        assertThat(query.getFirst("redirect_uri")).contains("/api/v1/auth/oauth2/callback/github");
        Cookie nonce = startResponse.getCookie("andromeda_oauth_nonce");
        assertThat(nonce).isNotNull();
        assertThat(nonce.isHttpOnly()).isTrue();

        MockHttpSession startedSession = (MockHttpSession) start.getRequest().getSession(false);
        assertThat(startedSession).isNotNull();
        assertThat(startedSession.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();

        MvcResult callback = mvc.perform(get("/api/v1/auth/oauth2/callback/github")
                        .param("code", "provider-code").param("state", state)
                        .session(startedSession).cookie(nonce))
                .andReturn();

        MockHttpServletResponse callbackResponse = callback.getResponse();
        assertThat(callbackResponse.getStatus()).isEqualTo(302);
        assertThat(callbackResponse.getRedirectedUrl())
                .isEqualTo("http://app.test/oauth/retorno?provider=github&status=linked");
        assertThat(TOKEN_REQUEST_BODY.get()).contains("code=provider-code");

        var account = accounts.findByOrganizationIdAndUserIdAndProvider(orgId, userId, Provider.GITHUB);
        assertThat(account).isPresent();
        assertThat(account.get().getExternalUserId()).isEqualTo(EXTERNAL_ID);
        assertThat(account.get().getExternalLogin()).isEqualTo("octo");
        assertThat(account.get().isActive()).isTrue();
        assertThat(users.findByIdAndOrganizationId(userId, orgId).orElseThrow().getGithubId())
                .isEqualTo(EXTERNAL_ID);

        // 6.1: nada de identidade sobrevive ao callback.
        assertThat(startedSession.isInvalid()).isTrue();
        assertThat(callbackResponse.getHeaders("Set-Cookie")).noneMatch(h -> h.startsWith("JSESSIONID="));
        assertThat(callbackResponse.getCookie("andromeda_oauth_nonce")).isNotNull();
        assertThat(callbackResponse.getCookie("andromeda_oauth_nonce").getMaxAge()).isZero();
    }

    @Test
    void shouldRejectAReplayedTicket() throws Exception {
        String ticket = ticket();
        mvc.perform(get("/api/v1/auth/oauth2/authorization/github").param("ticket", ticket)).andReturn();

        MvcResult second = mvc.perform(get("/api/v1/auth/oauth2/authorization/github").param("ticket", ticket))
                .andReturn();

        assertThat(second.getResponse().getRedirectedUrl())
                .isEqualTo("http://app.test/oauth/retorno?provider=github&status=error&code=OAUTH_TICKET_INVALID");
    }

    @Test
    void shouldKeepApiRoutesWithJwtStateless() throws Exception {
        String jwt = jwtService.generate(userId, orgId, List.of());

        MvcResult result = mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwt))
                .andReturn();

        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeaders("Set-Cookie")).noneMatch(h -> h.startsWith("JSESSIONID="));
    }

    @Test
    void shouldAnswer401WithoutJwtAndWithoutCreatingASession() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/me")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getRequest().getSession(false)).isNull();
    }
}
