package andromeda.sei.ms.feature.agent.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import andromeda.sei.ms.feature.agent.application.usecase.GetAgentWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.usecase.GetWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns.Entry;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns.Mode;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentWorkItemPatternsControllerTest {

    private GetWorkItemPatternsUseCase source;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        source = mock(GetWorkItemPatternsUseCase.class);
        AgentWorkItemPatternsController controller =
                new AgentWorkItemPatternsController(new GetAgentWorkItemPatternsUseCase(source));
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        AgentPrincipal principal = new AgentPrincipal("org-1", "user-1", "dev-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_returnPatternsInEvaluationOrderWithPortableForm() throws Exception {
        Instant at = Instant.parse("2026-10-29T10:00:00Z");
        List<Entry> entries = List.of(
                Entry.of(BranchPatternMatcher.NUMBER_REGEX, false),
                Entry.of(BranchPatternMatcher.JIRA_KEY_REGEX, false),
                Entry.of("(?s)(\\d+)", false),
                Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true),
                WorkItemPatterns.fromPrefix("WI-"));
        when(source.execute("org-1")).thenReturn(
                new WorkItemPatterns(true, Mode.CUSTOM, entries, null, null, "u", at, null));

        mvc.perform(get("/agent/v1/work-item-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.patterns.length()").value(5))
                .andExpect(jsonPath("$.patterns[0].preset").value("AB_HASH"))
                .andExpect(jsonPath("$.patterns[0].portable.source").value("AB#(\\d+)(?!\\d)"))
                .andExpect(jsonPath("$.patterns[0].portable.flags").value("i"))
                .andExpect(jsonPath("$.patterns[1].preset").value("PREFIX"))
                .andExpect(jsonPath("$.patterns[1].portable.source").value("WI-(\\d+)(?!\\d)"))
                .andExpect(jsonPath("$.patterns[2].preset").value("JIRA_KEY"))
                .andExpect(jsonPath("$.patterns[2].portable.flags").value(""))
                .andExpect(jsonPath("$.patterns[3].preset").value("CUSTOM"))
                .andExpect(jsonPath("$.patterns[3].portable").value((Object) null))
                .andExpect(jsonPath("$.patterns[4].preset").value("NUMBER"))
                .andExpect(jsonPath("$.patterns[4].portable.flags").value("i"));
    }

    @Test
    void should_returnTheDefaultWhenUntouched() throws Exception {
        when(source.execute("org-1")).thenReturn(WorkItemPatterns.defaults());
        mvc.perform(get("/agent/v1/work-item-patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patterns.length()").value(1))
                .andExpect(jsonPath("$.patterns[0].preset").value("AB_HASH"));
    }
}
