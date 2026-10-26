package andromeda.sei.ms.feature.organization.infrastructure.scheduling;

import andromeda.sei.ms.feature.organization.application.usecase.PurgeOrganizationDataUseCase;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingOrganizationPurgeResumerTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private PurgeOrganizationDataUseCase purgeUseCase;

    @InjectMocks
    private PendingOrganizationPurgeResumer resumer;

    @Test
    void should_runPurgeAgain_forEveryPendingTombstone_atStartup() {
        when(organizationRepository.findPendingPurge(any())).thenReturn(List.of(org("a"), org("b")));

        resumer.run(null);

        verify(purgeUseCase).execute("a");
        verify(purgeUseCase).execute("b");
    }

    @Test
    void should_waitTheGracePeriod_inThePeriodicJob() {
        when(organizationRepository.findPendingPurge(any())).thenReturn(List.of());
        Instant before = Instant.now();

        resumer.resumePending();

        ArgumentCaptor<Instant> bound = ArgumentCaptor.forClass(Instant.class);
        verify(organizationRepository).findPendingPurge(bound.capture());
        assertThat(bound.getValue()).isBefore(before.minus(Duration.ofMinutes(9)));
        verify(purgeUseCase, never()).execute(any());
    }

    @Test
    void should_notThrow_whenTheQueryFails() {
        when(organizationRepository.findPendingPurge(any())).thenThrow(new IllegalStateException("mongo down"));

        assertThatCode(() -> resumer.run(null)).doesNotThrowAnyException();
        verify(purgeUseCase, never()).execute(any());
    }

    private static Organization org(String id) {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        return new Organization(id, "n", "s-" + id, null, null, null, null, null, false, 1L, t, t);
    }
}
