package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.agent.domain.attribution.BranchPatternMatcher;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsInput;
import andromeda.sei.ms.feature.vcs.application.dto.WorkItemLinkSettingsOutput;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** vcs-work-item-linking: the link configuration is an organization setting, readable without a connection. */
@ExtendWith(MockitoExtension.class)
class WorkItemLinkSettingsUseCasesTest {

    @Mock private OrganizationRepository organizationRepository;

    private GetWorkItemLinkSettingsUseCase getUseCase;
    private UpdateWorkItemLinkSettingsUseCase updateUseCase;
    private Organization organization;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
        getUseCase = new GetWorkItemLinkSettingsUseCase(organizationRepository);
        updateUseCase = new UpdateWorkItemLinkSettingsUseCase(organizationRepository);
        organization = new Organization("org-1", "Acme", "acme", null, null, List.of(), null, null, true, 1L,
                Instant.now(), Instant.now());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_returnDefaults_when_neverConfigured() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        WorkItemLinkSettingsOutput output = getUseCase.execute();

        assertThat(output.enabled()).isTrue();
        assertThat(output.prefixes()).containsExactly("AB#");
    }

    @Test
    void should_returnPrefixesOfTheNewModel() {
        organization.setWorkItemPatterns(new WorkItemPatterns(true, WorkItemPatterns.Mode.CUSTOM, List.of(
                WorkItemPatterns.fromPrefix("AB#"), WorkItemPatterns.fromPrefix("WI-"),
                WorkItemPatterns.Entry.of(BranchPatternMatcher.NUMBER_REGEX, false)),
                null, null, null, null, null));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        assertThat(getUseCase.execute().prefixes()).containsExactly("AB#", "WI-");
    }

    @Test
    void should_returnNoPrefixes_when_disabled() {
        organization.setWorkItemPatterns(new WorkItemPatterns(false, WorkItemPatterns.Mode.CUSTOM, null,
                null, null, null, null, null));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        WorkItemLinkSettingsOutput output = getUseCase.execute();

        assertThat(output.enabled()).isFalse();
        assertThat(output.prefixes()).isEmpty();
    }

    @Test
    void should_storeTrimmedPrefixes_droppingBlanks() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkItemLinkSettingsOutput output = updateUseCase.execute(
                new WorkItemLinkSettingsInput(true, Arrays.asList(" WI- ", "", null, "AB#")));

        assertThat(output.prefixes()).containsExactly("WI-", "AB#");
        WorkItemPatterns stored = organization.getWorkItemPatterns();
        assertThat(stored.mode()).isEqualTo(WorkItemPatterns.Mode.CUSTOM);
        assertThat(stored.patterns()).extracting(WorkItemPatterns.Entry::preset).containsExactly("PREFIX", "AB_HASH");
        assertThat(stored.regexes().get(0)).isEqualTo(BranchPatternMatcher.prefixRegex("WI-"));
    }

    @Test
    void should_notWriteTheFormerVcsSettingsLink_whenSavingThroughTheLegacyRoute() {
        WorkItemLinkConfig former = new WorkItemLinkConfig(true, List.of("OLD-"));
        organization.setVcsSettings(new VcsSettings(former));
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        updateUseCase.execute(new WorkItemLinkSettingsInput(true, List.of("WI-")));

        assertThat(organization.getVcsSettings().workItemLink()).isSameAs(former);
        assertThat(organization.getWorkItemPatterns().legacyPrefixes()).containsExactly("WI-");
    }

    @Test
    void should_fallBackToTheDefaultPattern_when_enabledWithoutPrefixes() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkItemLinkSettingsOutput output = updateUseCase.execute(new WorkItemLinkSettingsInput(true, List.of()));

        assertThat(output.prefixes()).containsExactly("AB#");
        assertThat(organization.getWorkItemPatterns().mode()).isEqualTo(WorkItemPatterns.Mode.CUSTOM);
    }

    @Test
    void should_answerNotFound_when_organizationMissing() {
        when(organizationRepository.findById("org-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUseCase.execute()).isInstanceOf(OrganizationNotFoundException.class);
    }
}
