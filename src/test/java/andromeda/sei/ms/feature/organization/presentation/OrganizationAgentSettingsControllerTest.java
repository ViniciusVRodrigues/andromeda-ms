package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.organization.application.dto.AgentSettingsOutput;
import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.usecase.GetAgentSettingsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.PreviewAgentBranchPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateAgentSettingsUseCase;
import andromeda.sei.ms.feature.organization.domain.exception.AgentBranchPatternInvalidException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: {@code @PreAuthorize} is not evaluated; REPOS_CONFIGURE sits on the PUT annotation. */
@ExtendWith(MockitoExtension.class)
class OrganizationAgentSettingsControllerTest {

    private static final String URL = "/api/v1/organizations/me/agent-settings";

    @Mock private GetAgentSettingsUseCase getUseCase;
    @Mock private UpdateAgentSettingsUseCase updateUseCase;
    @Mock private PreviewAgentBranchPatternsUseCase previewUseCase;
    @InjectMocks private OrganizationAgentSettingsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnPatternsDefaultsAndFlag_when_getting() throws Exception {
        when(getUseCase.execute("org-1")).thenReturn(
                new AgentSettingsOutput(List.of("d(\\d+)"), List.of("d(\\d+)"), true, AgentSettingsOutput.defaultPresets()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchPatterns[0]").value("d(\\d+)"))
                .andExpect(jsonPath("$.defaults[0]").value("d(\\d+)"))
                .andExpect(jsonPath("$.usingDefaults").value(true))
                .andExpect(jsonPath("$.presets[0].id").value("AB_HASH"))
                .andExpect(jsonPath("$.presets[1].id").value("JIRA_KEY"))
                .andExpect(jsonPath("$.presets[2].id").value("NUMBER"))
                .andExpect(jsonPath("$.presets[0].regex").value("AB#(\\d+)"));
    }

    @Test
    void should_update_when_putting() throws Exception {
        when(updateUseCase.execute("org-1", List.of("^wi-(\\d+)"))).thenReturn(
                new AgentSettingsOutput(List.of("^wi-(\\d+)"), List.of("d(\\d+)"), false, AgentSettingsOutput.defaultPresets()));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchPatterns\":[\"^wi-(\\\\d+)\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.branchPatterns[0]").value("^wi-(\\d+)"))
                .andExpect(jsonPath("$.usingDefaults").value(false));
        verify(updateUseCase).execute("org-1", List.of("^wi-(\\d+)"));
    }

    @Test
    void should_answer422WithIndex_when_patternIsInvalid() throws Exception {
        when(updateUseCase.execute("org-1", List.of("feature/\\d+")))
                .thenThrow(new AgentBranchPatternInvalidException(List.of(new BranchPatternMatcher.Error(0,
                        BranchPatternMatcher.ErrorCode.PATTERN_NO_GROUP, "pattern needs at least one capturing group"))));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchPatterns\":[\"feature/\\\\d+\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AGENT_BRANCH_PATTERN_INVALID"))
                .andExpect(jsonPath("$.errors[0].index").value(0))
                .andExpect(jsonPath("$.errors[0].code").value("PATTERN_NO_GROUP"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void should_previewBranches() throws Exception {
        when(previewUseCase.execute(List.of("AB#(\\d+)"), List.of("feature/AB#12-x", "main"))).thenReturn(List.of(
                new PreviewAgentBranchPatternsUseCase.Result("feature/AB#12-x", "12", 0, 8, 13),
                new PreviewAgentBranchPatternsUseCase.Result("main", null, null, null, null)));

        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchPatterns\":[\"AB#(\\\\d+)\"],\"branches\":[\"feature/AB#12-x\",\"main\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workItemNumber").value("12"))
                .andExpect(jsonPath("$[0].patternIndex").value(0))
                .andExpect(jsonPath("$[0].match[0]").value(8))
                .andExpect(jsonPath("$[0].match[1]").value(13))
                .andExpect(jsonPath("$[1].workItemNumber").doesNotExist())
                .andExpect(jsonPath("$[1].match").doesNotExist());
    }

    @Test
    void should_answer400_when_tooManyBranchesOrPatterns() throws Exception {
        String branches = String.join(",", java.util.Collections.nCopies(21, "\"b\""));
        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branches\":[" + branches + "]}"))
                .andExpect(status().isBadRequest());
        String patterns = String.join(",", java.util.Collections.nCopies(11, "\"(a)\""));
        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"branchPatterns\":[" + patterns + "],\"branches\":[\"b\"]}"))
                .andExpect(status().isBadRequest());
    }
}
