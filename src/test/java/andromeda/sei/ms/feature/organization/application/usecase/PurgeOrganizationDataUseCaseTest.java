package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.organization.application.port.OrganizationDataPurger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurgeOrganizationDataUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private IntegrationDataWiper wiperA;
    @Mock private IntegrationDataWiper wiperB;
    @Mock private OrganizationDataPurger purger;
    @Mock private OrganizationRepository organizationRepository;

    private PurgeOrganizationDataUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PurgeOrganizationDataUseCase(List.of(wiperA, wiperB), purger, organizationRepository);
    }

    @Test
    void should_wipeEveryIntegrationThenPurgeThenMarkPurged() {
        when(organizationRepository.findDeletedById(ORG)).thenReturn(Optional.of(tombstone(null)));

        useCase.execute(ORG);

        InOrder order = inOrder(wiperA, wiperB, purger, organizationRepository);
        order.verify(wiperA).wipe(ORG, WipeScope.wholeIntegration());
        order.verify(wiperB).wipe(ORG, WipeScope.wholeIntegration());
        order.verify(purger).purge(ORG);
        order.verify(organizationRepository).markPurged(eq(ORG), any(Instant.class));
    }

    @Test
    void should_doNothing_whenTheOrganizationHasNoTombstone() {
        when(organizationRepository.findDeletedById(ORG)).thenReturn(Optional.empty());

        useCase.execute(ORG);

        verify(wiperA, never()).wipe(any(), any());
        verify(purger, never()).purge(any());
        verify(organizationRepository, never()).markPurged(any(), any());
    }

    @Test
    void should_doNothing_whenAlreadyPurged() {
        when(organizationRepository.findDeletedById(ORG)).thenReturn(Optional.of(tombstone(Instant.now())));

        useCase.execute(ORG);

        verify(purger, never()).purge(any());
        verify(organizationRepository, never()).markPurged(any(), any());
    }

    @Test
    void should_notMarkPurged_whenThePurgeFails() {
        when(organizationRepository.findDeletedById(ORG)).thenReturn(Optional.of(tombstone(null)));
        doThrow(new IllegalStateException("boom")).when(purger).purge(ORG);

        useCase.execute(ORG);

        verify(organizationRepository, never()).markPurged(any(), any());
    }

    private static Organization tombstone(Instant purgedAt) {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        Organization org = new Organization(ORG, "Acme", "acme", null, null, null, null, null, false, 1L, t, t);
        org.setDeletedAt(t);
        org.setDeletedBy("user-1");
        org.setPurgedAt(purgedAt);
        return org;
    }
}
