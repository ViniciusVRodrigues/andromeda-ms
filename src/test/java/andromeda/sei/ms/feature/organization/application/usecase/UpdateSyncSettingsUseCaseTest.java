package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.UpdateSyncSettingsInput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationBackfillDaysInvalidException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSyncSettingsUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private UpdateSyncSettingsUseCase useCase;

    @Test
    void shouldPersistNewFrequencyAndBackfill() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var output = useCase.execute(ORG, new UpdateSyncSettingsInput(ScheduledSyncFrequency.EVERY_6_HOURS, 30));

        assertThat(output.scheduledSync()).isEqualTo(ScheduledSyncFrequency.EVERY_6_HOURS);
        assertThat(output.backfillDays()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(ints = {7, 730})
    void shouldAcceptBackfillAtTheBoundaries(int days) {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization()));
        when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var output = useCase.execute(ORG, new UpdateSyncSettingsInput(ScheduledSyncFrequency.DAILY, days));

        assertThat(output.backfillDays()).isEqualTo(days);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, 731})
    void shouldRejectBackfillOutsideSevenToSevenHundredThirtyDays(int days) {
        assertThatThrownBy(() -> useCase.execute(ORG, new UpdateSyncSettingsInput(ScheduledSyncFrequency.DAILY, days)))
                .isInstanceOf(OrganizationBackfillDaysInvalidException.class)
                .extracting("code").isEqualTo("ORGANIZATION_BACKFILL_DAYS_INVALID");
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundForUnknownOrganization() {
        when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ORG, new UpdateSyncSettingsInput(ScheduledSyncFrequency.OFF, 90)))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    private static Organization organization() {
        Instant now = Instant.now();
        Organization organization = new Organization(ORG, "Org", "org", null, null, null, null, null, true, 1L, now, now);
        organization.setSyncSettings(SyncSettings.defaults());
        return organization;
    }
}
