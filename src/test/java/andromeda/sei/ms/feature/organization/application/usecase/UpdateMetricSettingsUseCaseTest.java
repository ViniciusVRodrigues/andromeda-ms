package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.MetricSettingsOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateMetricSettingsInput;
import andromeda.sei.ms.feature.organization.domain.MetricSettings;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.MetricSettingsInvalidException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateMetricSettingsUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private UpdateMetricSettingsUseCase useCase;
    @InjectMocks private GetMetricSettingsUseCase getUseCase;

    @Test
    void should_persistTrimmedDistinctPrefixes_when_inputIsValid() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MetricSettingsOutput output = useCase.execute(ORG,
                new UpdateMetricSettingsInput(List.of(" fix/prod- ", "fix/prod-", "hotfix/"), false, true));

        assertThat(output.remediationBranchPrefixes()).containsExactly("fix/prod-", "hotfix/");
        assertThat(output.bugLinkedCountsAsRemediation()).isFalse();
        assertThat(output.revertCountsAsRemediation()).isTrue();
    }

    @Test
    void should_acceptEmptyPrefixList_when_prefixRuleIsDisabled() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MetricSettingsOutput output = useCase.execute(ORG, new UpdateMetricSettingsInput(List.of(), true, true));

        assertThat(output.remediationBranchPrefixes()).isEmpty();
    }

    @Test
    void should_throwMetricSettingsInvalid_when_aPrefixIsBlank() {
        assertThatThrownBy(() -> useCase.execute(ORG,
                new UpdateMetricSettingsInput(List.of("hotfix/", "  "), true, true)))
                .isInstanceOf(MetricSettingsInvalidException.class)
                .extracting("code").isEqualTo("METRIC_SETTINGS_INVALID");
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void should_throwMetricSettingsInvalid_when_aPrefixIsNull() {
        assertThatThrownBy(() -> useCase.execute(ORG,
                new UpdateMetricSettingsInput(Arrays.asList("hotfix/", null), true, true)))
                .isInstanceOf(MetricSettingsInvalidException.class);
    }

    @Test
    void should_throwNotFound_when_organizationDoesNotExist() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, new UpdateMetricSettingsInput(List.of("hotfix/"), true, true)))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void should_returnDefaults_when_organizationNeverConfiguredMetricSettings() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));

        MetricSettingsOutput output = getUseCase.execute(ORG);

        assertThat(output).isEqualTo(MetricSettingsOutput.from(MetricSettings.defaults()));
        assertThat(output.remediationBranchPrefixes()).containsExactly("hotfix/");
        assertThat(output.bugLinkedCountsAsRemediation()).isTrue();
        assertThat(output.revertCountsAsRemediation()).isTrue();
    }

    private Organization organization() {
        Instant now = Instant.now();
        return new Organization(ORG, "Org", "org", null, null, null, null, null, true, 1L, now, now);
    }
}
