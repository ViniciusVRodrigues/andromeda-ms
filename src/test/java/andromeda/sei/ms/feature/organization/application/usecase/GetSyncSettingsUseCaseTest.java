package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSyncSettingsUseCaseTest {

    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private GetSyncSettingsUseCase useCase;

    @Test
    void shouldReturnOrganizationSettings() {
        Instant now = Instant.now();
        Organization organization = new Organization("org-1", "Org", "org", null, null, null, null, null, true, 1L, now, now);
        organization.setSyncSettings(new SyncSettings(ScheduledSyncFrequency.DAILY, 120));
        organization.setLastScheduledSyncAt(now);
        when(organizationRepository.findById("org-1")).thenReturn(Optional.of(organization));

        var output = useCase.execute("org-1");

        assertThat(output.scheduledSync()).isEqualTo(ScheduledSyncFrequency.DAILY);
        assertThat(output.backfillDays()).isEqualTo(120);
        assertThat(output.lastScheduledSyncAt()).isEqualTo(now);
    }

    @Test
    void shouldThrowNotFoundForUnknownOrganization() {
        when(organizationRepository.findById("org-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("org-x")).isInstanceOf(OrganizationNotFoundException.class);
    }
}
