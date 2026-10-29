package andromeda.sei.ms.feature.connector.presentation;

import andromeda.sei.ms.feature.aiusage.application.usecase.UpsertAIUsageUseCase;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The AIEF upload answers how many entries were stored and how many were ignored for being agent days. */
class ConnectorUploadIgnoredTest {

    static final String BODY = "[{\"gitHubLogin\":\"someone\",\"day\":\"2026-10-29\",\"model\":\"gpt\","
            + "\"inputTokens\":1,\"outputTokens\":2,\"interactions\":3},"
            + "{\"gitHubLogin\":\"someone\",\"day\":\"2026-10-28\",\"model\":\"gpt\","
            + "\"inputTokens\":1,\"outputTokens\":2,\"interactions\":3}]";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void should_reportTheIgnoredEntries() throws Exception {
        UpsertAIUsageUseCase upsert = mock(UpsertAIUsageUseCase.class);
        when(upsert.execute(any(), any(), any())).thenReturn(1);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConnectorController(upsert)).build();

        mvc.perform(post("/connector/acme/raw-token/api/upload").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploaded").value(1))
                .andExpect(jsonPath("$.ignored").value(1));
    }

    @Test
    void should_reportZeroIgnored_when_thePersonHasNoAgent() throws Exception {
        UpsertAIUsageUseCase upsert = mock(UpsertAIUsageUseCase.class);
        when(upsert.execute(any(), any(), any())).thenReturn(0);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConnectorController(upsert)).build();

        mvc.perform(post("/connector/acme/raw-token/api/upload").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.uploaded").value(2))
                .andExpect(jsonPath("$.ignored").value(0));
    }
}
