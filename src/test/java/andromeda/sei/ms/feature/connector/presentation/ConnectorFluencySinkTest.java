package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.aiusage.application.usecase.UpsertAIUsageUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.ResolveConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.domain.ConnectorToken;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.feature.connector.infrastructure.security.ConnectorTokenAuthFilter;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** remover-fluency-score 3.1: the fluency-score route is a sink (validates, caps, discards, never writes). */
class ConnectorFluencySinkTest {

    private static final String URL = "/connector/acme/raw-token-123/api/fluency-score";
    private static final String SECRET = "SECRET-LABEL-IN-BODY";

    private final UpsertAIUsageUseCase upsert = mock(UpsertAIUsageUseCase.class);
    private final ResolveConnectorTokenUseCase resolve = mock(ResolveConnectorTokenUseCase.class);
    private final OrganizationRepository orgs = mock(OrganizationRepository.class);
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

        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn("org-1");
        when(orgs.findBySlug("acme")).thenReturn(Optional.of(org));
        ConnectorToken token = mock(ConnectorToken.class);
        when(token.getOrganizationId()).thenReturn("org-1");
        when(token.getUserId()).thenReturn("user-1");
        when(resolve.execute(anyString(), anyString())).thenReturn(token);

        mockMvc = MockMvcBuilders.standaloneSetup(new ConnectorController(upsert))
                .addFilters(new ConnectorTokenAuthFilter(resolve, orgs)).build();
    }

    @AfterEach
    void tearDown() {
        root.detachAppender(appender);
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void oldPayloadIsAcceptedAndDiscarded() throws Exception {
        send("{\"overallStage\":2,\"overallLabel\":\"" + SECRET + "\",\"computedAt\":\"x\","
                + "\"categories\":[{\"category\":\"a\",\"icon\":\"i\",\"stage\":1,\"tips\":[\"t\"]}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));

        verifyNoInteractions(upsert);
        var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).contains("connector fluency-score discarded: org=acme");
        assertThat(String.join("\n", messages)).doesNotContain(SECRET).doesNotContain("raw-token-123");
    }

    @Test
    void payloadWithoutOverallStageIsAccepted() throws Exception {
        send("{\"categories\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void bodyAtTheLimitIsAcceptedAndOneByteOverIs400() throws Exception {
        String prefix = "{\"x\":\"";
        String suffix = "\"}";
        int limit = ConnectorController.FLUENCY_SINK_MAX_BYTES;
        String atLimit = prefix + "a".repeat(limit - prefix.length() - suffix.length()) + suffix;

        send(atLimit).andExpect(status().isOk());
        send(atLimit + " ").andExpect(status().isBadRequest());
    }

    @Test
    void invalidJsonIs400() throws Exception {
        send("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void invalidTokenIs401() throws Exception {
        when(resolve.execute(anyString(), anyString())).thenThrow(new ConnectorTokenNotFoundException("x"));

        send("{}").andExpect(status().isUnauthorized());
        verifyNoInteractions(upsert);
    }
}
