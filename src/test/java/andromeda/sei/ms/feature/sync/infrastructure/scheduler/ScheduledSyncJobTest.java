package andromeda.sei.ms.feature.sync.infrastructure.scheduler;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledSyncJobTest {

    /** 12:07 de proposito: o tick gravado deve ser a hora cheia (12:00). */
    private static final Instant NOW = Instant.parse("2026-09-30T12:07:31Z");
    private static final Instant TICK = Instant.parse("2026-09-30T12:00:00Z");
    private static final List<SyncTarget> TARGETS = List.of(new SyncTarget(Integration.GITHUB, "bind-1", "proj-1"));

    @Mock private OrganizationRepository organizationRepository;
    @Mock private SyncTargetResolver syncTargetResolver;
    @Mock private SyncOrchestrator syncOrchestrator;

    @InjectMocks
    private ScheduledSyncJob job;

    @Test
    void shouldMakeNoCallForOrganizationWithSchedulingOff() {
        when(organizationRepository.findAllDueForScheduledSync(TICK))
                .thenReturn(List.of(organization(ScheduledSyncFrequency.OFF, null)));

        job.run(NOW);

        verifyNoInteractions(syncTargetResolver, syncOrchestrator);
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldSyncDailyOrganizationWhoseIntervalElapsed() {
        Organization organization = organization(ScheduledSyncFrequency.DAILY, TICK.minus(Duration.ofHours(24)));
        givenDue(organization);

        job.run(NOW);

        SyncRequest request = capturedRequest();
        assertThat(request.trigger()).isEqualTo(SyncTrigger.SCHEDULED);
        assertThat(request.operation()).isEqualTo(SyncOperation.ORGANIZATION_SYNC);
        assertThat(request.targets()).isEqualTo(TARGETS);
        assertThat(organization.getLastScheduledSyncAt()).isEqualTo(TICK);
        verify(organizationRepository).save(organization);
    }

    @Test
    void shouldSkipDailyOrganizationSyncedLessThanADayAgo() {
        Organization organization = organization(ScheduledSyncFrequency.DAILY, TICK.minus(Duration.ofHours(23)));
        when(organizationRepository.findAllDueForScheduledSync(TICK)).thenReturn(List.of(organization));

        job.run(NOW);

        verifyNoInteractions(syncOrchestrator);
    }

    @Test
    void shouldSyncEverySixHoursOrganizationAfterSixHours() {
        Organization organization = organization(ScheduledSyncFrequency.EVERY_6_HOURS, TICK.minus(Duration.ofHours(6)));
        givenDue(organization);

        job.run(NOW);

        verify(syncOrchestrator).runNow(any());
    }

    @Test
    void shouldSkipEverySixHoursOrganizationBeforeSixHours() {
        Organization organization = organization(ScheduledSyncFrequency.EVERY_6_HOURS, TICK.minus(Duration.ofHours(5)));
        when(organizationRepository.findAllDueForScheduledSync(TICK)).thenReturn(List.of(organization));

        job.run(NOW);

        verifyNoInteractions(syncOrchestrator);
    }

    @Test
    void shouldSyncOrganizationThatNeverRanAndClearTenantAfterwards() {
        givenDue(organization(ScheduledSyncFrequency.DAILY, null));

        job.run(NOW);

        verify(syncOrchestrator).runNow(any());
        assertThat(TenantContextHolder.get()).isNull();
    }

    @Test
    void shouldNotCallOrchestratorWhenOrganizationHasNothingToSync() {
        Organization organization = organization(ScheduledSyncFrequency.DAILY, null);
        when(organizationRepository.findAllDueForScheduledSync(TICK)).thenReturn(List.of(organization));
        when(syncTargetResolver.forScheduled("org-1")).thenReturn(List.of());

        job.run(NOW);

        verifyNoInteractions(syncOrchestrator);
    }

    @Test
    void shouldKeepGoingWhenOneOrganizationFails() {
        Organization failing = organization(ScheduledSyncFrequency.DAILY, null);
        Organization healthy = organization(ScheduledSyncFrequency.DAILY, null);
        healthy.setId("org-2");
        when(organizationRepository.findAllDueForScheduledSync(TICK)).thenReturn(List.of(failing, healthy));
        when(syncTargetResolver.forScheduled("org-1")).thenThrow(new RuntimeException("mongo hiccup"));
        when(syncTargetResolver.forScheduled("org-2")).thenReturn(TARGETS);

        job.run(NOW);

        verify(syncOrchestrator).runNow(any());
    }

    private void givenDue(Organization organization) {
        when(organizationRepository.findAllDueForScheduledSync(TICK)).thenReturn(List.of(organization));
        when(syncTargetResolver.forScheduled(organization.getId())).thenReturn(TARGETS);
    }

    private SyncRequest capturedRequest() {
        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncOrchestrator).runNow(captor.capture());
        return captor.getValue();
    }

    private static Organization organization(ScheduledSyncFrequency frequency, Instant lastScheduledSyncAt) {
        Organization organization = new Organization("org-1", "Org", "org", null, null, null, null, null,
                true, 1L, NOW, NOW);
        organization.setSyncSettings(new SyncSettings(frequency, SyncSettings.DEFAULT_BACKFILL_DAYS));
        organization.setLastScheduledSyncAt(lastScheduledSyncAt);
        return organization;
    }
}
