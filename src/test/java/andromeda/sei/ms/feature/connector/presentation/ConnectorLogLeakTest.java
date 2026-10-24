package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.aiusage.application.usecase.UpsertAIUsageUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.ResolveConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.StoreFluencyScoreUseCase;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.infrastructure.security.ConnectorTokenAuthFilter;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.shared.infrastructure.security.JwtAuthenticationFilter;
import andromeda.sei.ms.shared.infrastructure.security.JwtService;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConnectorLogLeakTest {

    private static final String GITHUB_BEARER = "Bearer gho_test";
    private static final String RAW_TOKEN = "abcd1234-ef56-7890-abcd-1234567890ab";
    private static final String TOKEN_PREFIX = "abcd1234";
    private static final String MACHINE = "MACHINE-SECRET-NAME";

    @Mock private UpsertAIUsageUseCase upsertAIUsageUseCase;
    @Mock private StoreFluencyScoreUseCase storeFluencyScoreUseCase;
    @Mock private ResolveConnectorTokenUseCase resolveConnectorTokenUseCase;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private JwtService jwtService;
    @Mock private EffectivePermissionService effectivePermissionService;

    private ListAppender<ILoggingEvent> appender;
    private Logger root;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.INFO);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new ConnectorController(upsertAIUsageUseCase, storeFluencyScoreUseCase)).build();
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    private void assertNoLeak() {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent e : appender.list) {
            messages.add(e.getFormattedMessage());
            if (e.getThrowableProxy() != null) {
                messages.add(e.getThrowableProxy().getMessage());
            }
        }
        String all = String.join("\n", messages);
        assertThat(all).doesNotContain("gho_test").doesNotContain(MACHINE).doesNotContain(TOKEN_PREFIX);
    }

    @Test
    void uploadDoesNotLogBodyTokenOrAuthorization() throws Exception {
        String body = "[{\"gitHubLogin\":\"someone\",\"day\":\"2026-10-01\",\"model\":\"gpt\","
                + "\"machineName\":\"" + MACHINE + "\",\"inputTokens\":1,\"outputTokens\":2,\"interactions\":3}]";

        mockMvc.perform(post("/connector/acme/" + RAW_TOKEN + "/api/upload")
                        .header("Authorization", GITHUB_BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("org=acme")
                && e.getFormattedMessage().contains("entries=1") && e.getFormattedMessage().contains("ms="));
        assertNoLeak();
    }

    @Test
    void fluencyScoreDoesNotLogBodyTokenOrAuthorization() throws Exception {
        String body = "{\"overallStage\":2,\"overallLabel\":\"" + MACHINE + "\",\"categories\":[]}";

        mockMvc.perform(post("/connector/acme/" + RAW_TOKEN + "/api/fluency-score")
                        .header("Authorization", GITHUB_BEARER)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        assertThat(appender.list).anyMatch(e -> e.getFormattedMessage().contains("org=acme")
                && e.getFormattedMessage().contains("ms="));
        assertNoLeak();
    }

    @Test
    void connectorAuthFilterNeverReadsAuthorizationHeader() throws Exception {
        List<String> headersRead = new ArrayList<>();
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/connector/acme/" + RAW_TOKEN + "/api/upload") {
            @Override
            public String getHeader(String name) {
                headersRead.add(name);
                return super.getHeader(name);
            }

            @Override
            public java.util.Enumeration<String> getHeaders(String name) {
                headersRead.add(name);
                return super.getHeaders(name);
            }
        };
        request.addHeader("Authorization", GITHUB_BEARER);

        Organization organization = org.mockito.Mockito.mock(Organization.class);
        when(organization.getId()).thenReturn("org-1");
        when(organizationRepository.findBySlug("acme")).thenReturn(java.util.Optional.of(organization));
        ConnectorToken token = org.mockito.Mockito.mock(ConnectorToken.class);
        when(token.getOrganizationId()).thenReturn("org-1");
        when(token.getUserId()).thenReturn("user-1");
        when(resolveConnectorTokenUseCase.execute(anyString(), anyString())).thenReturn(token);

        new ConnectorTokenAuthFilter(resolveConnectorTokenUseCase, organizationRepository)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(headersRead).noneMatch("Authorization"::equalsIgnoreCase);
        assertNoLeak();
    }

    @Test
    void jwtFilterIgnoresAuthorizationOnConnectorRoutes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/connector/acme/" + RAW_TOKEN + "/api/upload");
        request.addHeader("Authorization", GITHUB_BEARER);

        new JwtAuthenticationFilter(jwtService, effectivePermissionService)
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        verifyNoInteractions(jwtService);
        assertNoLeak();
    }
}
