package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.connector.application.dto.ConnectorTokenOutput;
import andromeda.sei.ms.feature.connector.application.usecase.GenerateConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.ListConnectorTokensUseCase;
import andromeda.sei.ms.feature.connector.application.usecase.RevokeConnectorTokenUseCase;
import andromeda.sei.ms.feature.connector.domain.exception.ConnectorTokenNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConnectorTokenControllerTest {

    @Mock
    private GenerateConnectorTokenUseCase generateConnectorTokenUseCase;

    @Mock
    private ListConnectorTokensUseCase listConnectorTokensUseCase;

    @Mock
    private RevokeConnectorTokenUseCase revokeConnectorTokenUseCase;

    @Mock private andromeda.sei.ms.feature.integration.application.usecase.WipeIntegrationDataUseCase wipeIntegrationDataUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ConnectorTokenController controller = new ConnectorTokenController(
                generateConnectorTokenUseCase,
                listConnectorTokensUseCase,
                revokeConnectorTokenUseCase,
                wipeIntegrationDataUseCase);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        TenantContextHolder.set("org-1");
        setAuthentication("user-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturn201WhenGeneratingToken() throws Exception {
        Instant now = Instant.now();
        when(generateConnectorTokenUseCase.execute(any()))
                .thenReturn(new ConnectorTokenOutput("token-1", "org-1", "user-1",
                        "token-abcdefgh", true, now, now, "raw-uuid-token"));

        mockMvc.perform(post("/api/v1/connector-tokens"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("token-1"))
                .andExpect(jsonPath("$.rawToken").value("raw-uuid-token"));
    }

    @Test
    void shouldReturn200WhenListingTokens() throws Exception {
        Instant now = Instant.now();
        when(listConnectorTokensUseCase.execute(any(), any()))
                .thenReturn(List.of(
                        new ConnectorTokenOutput("token-1", "org-1", "user-1",
                                "token-abcdefgh", true, now, now, null)
                ));

        mockMvc.perform(get("/api/v1/connector-tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("token-1"));
    }

    @Test
    void shouldReturn204WhenRevokingToken() throws Exception {
        mockMvc.perform(delete("/api/v1/connector-tokens/token-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturn404WhenRevokingNonExistentToken() throws Exception {
        doThrow(new ConnectorTokenNotFoundException("token-99"))
                .when(revokeConnectorTokenUseCase).execute(eq("token-99"), any());

        mockMvc.perform(delete("/api/v1/connector-tokens/token-99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONNECTOR_TOKEN_NOT_FOUND"));
    }

    private void setAuthentication(String userId) {
        var userDetails = AndromedaUserDetails.builder()
                .userId(userId)
                .organizationId("org-1")
                .permissions(List.of("CONNECTOR_TOKEN_MANAGE_OWN"))
                .build();
        var auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, List.of(new SimpleGrantedAuthority("CONNECTOR_TOKEN_MANAGE_OWN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
