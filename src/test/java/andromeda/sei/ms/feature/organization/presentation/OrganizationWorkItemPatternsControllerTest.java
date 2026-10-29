package andromeda.sei.ms.feature.organization.presentation;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.application.usecase.ConfirmWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.GetWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.PreviewWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.UpdateWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.vcs.application.usecase.GetWorkItemLinkSettingsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.UpdateWorkItemLinkSettingsUseCase;
import andromeda.sei.ms.feature.vcs.presentation.VcsSettingsController;
import andromeda.sei.ms.shared.infrastructure.security.AndromedaUserDetails;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.shared.presentation.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc over the real use cases and an in-memory organization: the new routes and the
 * legacy prefixes route read and write the same model. {@code @PreAuthorize} is not evaluated here,
 * so the permission of each route is asserted on the annotations.
 */
class OrganizationWorkItemPatternsControllerTest {

    private static final String URL = "/api/v1/organizations/me/work-item-patterns";
    private static final String LEGACY = "/api/v1/repos/settings/work-item-link";

    private Organization organization;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        organization = new Organization("org-1", "Acme", "acme", null, null, List.of(), null, null, true, 1L,
                Instant.now(), Instant.now());
        OrganizationRepository repository = mock(OrganizationRepository.class);
        when(repository.findById("org-1")).thenReturn(Optional.of(organization));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var patterns = new OrganizationWorkItemPatternsController(
                new GetWorkItemPatternsUseCase(repository), new UpdateWorkItemPatternsUseCase(repository),
                new ConfirmWorkItemPatternsUseCase(repository), new PreviewWorkItemPatternsUseCase(),
                new DetectWorkItemPatternsUseCase());
        var legacy = new VcsSettingsController(new GetWorkItemLinkSettingsUseCase(repository),
                new UpdateWorkItemLinkSettingsUseCase(repository));
        mockMvc = MockMvcBuilders.standaloneSetup(patterns, legacy)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_returnDefaultsPresetsAndAnExplicitNullDetection_when_getting() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.mode").value("DEFAULT"))
                .andExpect(jsonPath("$.patterns.length()").value(1))
                .andExpect(jsonPath("$.patterns[0].regex").value(BranchPatternMatcher.AB_HASH_REGEX))
                .andExpect(jsonPath("$.patterns[0].preset").value("AB_HASH"))
                .andExpect(jsonPath("$.patterns[0].alsoInText").value(true))
                .andExpect(jsonPath("$.defaults[0].preset").value("AB_HASH"))
                .andExpect(jsonPath("$.presets.length()").value(3))
                .andExpect(jsonPath("$.presets[1].id").value("JIRA_KEY"))
                .andExpect(jsonPath("$.presets[2].regex").value(BranchPatternMatcher.NUMBER_REGEX))
                .andExpect(jsonPath("$.confirmedBy").value((Object) null))
                .andExpect(jsonPath("$.detection").hasJsonPath())
                .andExpect(jsonPath("$.detection").value((Object) null));
    }

    @Test
    void should_saveAsCustomWithComputedPresetAndDefaultAlsoInText_when_putting() throws Exception {
        authenticateAs("user-7");

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"patterns\":[{\"regex\":\"(?i)\\\\Qtask-\\\\E(\\\\d+)(?!\\\\d)\"},"
                                + "{\"regex\":\"(?i)(?:^|/)(?:[a-z]+[-_])?(\\\\d{2,7})(?=[-_/.]|$)\"},"
                                + "{\"regex\":\"^x-(\\\\d+)\",\"alsoInText\":true}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CUSTOM"))
                .andExpect(jsonPath("$.patterns[0].preset").value("PREFIX"))
                .andExpect(jsonPath("$.patterns[0].alsoInText").value(true))
                .andExpect(jsonPath("$.patterns[1].preset").value("CUSTOM"))
                .andExpect(jsonPath("$.patterns[1].alsoInText").value(true))
                .andExpect(jsonPath("$.patterns[2].preset").value("NUMBER"))
                .andExpect(jsonPath("$.patterns[2].alsoInText").value(false));

        WorkItemPatterns stored = organization.getWorkItemPatterns();
        assertThat(stored.mode()).isEqualTo(WorkItemPatterns.Mode.CUSTOM);
        assertThat(stored.updatedBy()).isEqualTo("user-7");
        assertThat(stored.confirmedBy()).isEqualTo("user-7");
        assertThat(stored.updatedAt()).isNotNull();
    }

    @Test
    void should_answer422WithEveryErrorByIndex_when_patternsAreInvalid() throws Exception {
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"patterns\":[{\"regex\":\"ok-(\\\\d+)\"},"
                                + "{\"regex\":\"feature/\\\\d+\"},{\"regex\":\"(unclosed\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WORK_ITEM_PATTERN_INVALID"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].index").value(1))
                .andExpect(jsonPath("$.errors[0].code").value("PATTERN_NO_GROUP"))
                .andExpect(jsonPath("$.errors[1].index").value(2))
                .andExpect(jsonPath("$.errors[1].code").value("PATTERN_INVALID"))
                .andExpect(jsonPath("$.errors[1].message").exists());
        assertThat(organization.getWorkItemPatterns().mode()).isEqualTo(WorkItemPatterns.Mode.DEFAULT);
    }

    @Test
    void should_rejectTooManyAndTooLongPatterns() throws Exception {
        StringBuilder many = new StringBuilder("{\"patterns\":[");
        for (int i = 0; i < 11; i++) many.append(i == 0 ? "" : ",").append("{\"regex\":\"a").append(i).append("(\\\\d+)\"}");
        many.append("]}");
        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(many.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PATTERN_TOO_MANY"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patterns\":[{\"regex\":\"" + "a".repeat(201) + "(\\\\d+)\"}]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("PATTERN_TOO_LONG"));
    }

    @Test
    void should_restoreDefaults_when_putWithEmptyOrNullPatterns() throws Exception {
        organization.setWorkItemPatterns(new WorkItemPatterns(true, WorkItemPatterns.Mode.CUSTOM,
                List.of(WorkItemPatterns.fromPrefix("WI-")), "u", Instant.now(), "u", Instant.now(), null));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"patterns\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEFAULT"))
                .andExpect(jsonPath("$.patterns[0].preset").value("AB_HASH"));

        mockMvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.mode").value("DEFAULT"))
                .andExpect(jsonPath("$.patterns[0].preset").value("AB_HASH"));
    }

    @Test
    void should_keepThePatternsAndTurnDetectedIntoCustom_when_confirming() throws Exception {
        organization.setWorkItemPatterns(new WorkItemPatterns(true, WorkItemPatterns.Mode.DETECTED, List.of(
                WorkItemPatterns.Entry.of(BranchPatternMatcher.AB_HASH_REGEX, true),
                WorkItemPatterns.Entry.of(BranchPatternMatcher.NUMBER_REGEX, false)), null, null, null, null, null));
        authenticateAs("user-9");

        mockMvc.perform(post(URL + "/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("CUSTOM"))
                .andExpect(jsonPath("$.patterns.length()").value(2))
                .andExpect(jsonPath("$.patterns[1].preset").value("NUMBER"))
                .andExpect(jsonPath("$.confirmedBy").value("user-9"))
                .andExpect(jsonPath("$.confirmedAt").exists());
    }

    @Test
    void should_previewAgainstSampleBranchesAndStoreNothing() throws Exception {
        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patterns\":[{\"regex\":\"^wi-(\\\\d+)\"}],\"branches\":[\"wi-77\",\"main\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].branch").value("wi-77"))
                .andExpect(jsonPath("$[0].workItemNumber").value("77"))
                .andExpect(jsonPath("$[0].patternIndex").value(0))
                .andExpect(jsonPath("$[0].match[1]").value(5))
                .andExpect(jsonPath("$[1].workItemNumber").value((Object) null));
        assertThat(organization.getWorkItemPatterns().mode()).isEqualTo(WorkItemPatterns.Mode.DEFAULT);
    }

    @Test
    void should_previewWithAStringPatternToo_andRejectInvalidOnes() throws Exception {
        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patterns\":[\"^wi-(\\\\d+)\"],\"branches\":[\"wi-5\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workItemNumber").value("5"));

        mockMvc.perform(post(URL + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patterns\":[{\"regex\":\"nogroup\"}],\"branches\":[\"x\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WORK_ITEM_PATTERN_INVALID"));
    }

    @Test
    void should_answer501WithTheCommonErrorBody_when_detecting() throws Exception {
        mockMvc.perform(post(URL + "/detect"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value("WORK_ITEM_PATTERN_DETECTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void should_requireReposConfigure_onEveryWriteRoute_andOnlyAuthenticationOnGet() throws Exception {
        assertThat(preAuthorize("get")).isEqualTo("isAuthenticated()");
        for (String method : List.of("update", "confirm", "preview", "detect")) {
            assertThat(preAuthorize(method)).as(method).isEqualTo("hasAuthority('REPOS_CONFIGURE')");
        }
    }

    @Test
    void should_replaceTheOldRoute_withTheLegacyPrefixesOverTheNewModel() throws Exception {
        WorkItemLinkConfig former = new WorkItemLinkConfig(true, List.of("OLD-"));
        organization.setVcsSettings(new VcsSettings(former));

        mockMvc.perform(put(LEGACY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"prefixes\":[\"WI-\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prefixes[0]").value("WI-"));

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.mode").value("CUSTOM"))
                .andExpect(jsonPath("$.patterns.length()").value(1))
                .andExpect(jsonPath("$.patterns[0].regex").value(BranchPatternMatcher.prefixRegex("WI-")))
                .andExpect(jsonPath("$.patterns[0].preset").value("PREFIX"))
                .andExpect(jsonPath("$.patterns[0].alsoInText").value(true));
        mockMvc.perform(get(LEGACY))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.prefixes[0]").value("WI-"));

        assertThat(organization.getVcsSettings().workItemLink()).isSameAs(former);
    }

    private static String preAuthorize(String method) {
        return java.util.Arrays.stream(OrganizationWorkItemPatternsController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(method))
                .findFirst().orElseThrow()
                .getAnnotation(PreAuthorize.class).value();
    }

    private static void authenticateAs(String userId) {
        AndromedaUserDetails user = mock(AndromedaUserDetails.class);
        when(user.getUserId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}
