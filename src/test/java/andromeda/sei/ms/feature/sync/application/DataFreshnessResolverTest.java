package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.sync.application.dto.DataFreshness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataFreshnessResolverTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private VcsRepoBindingRepository vcsRepoBindingRepository;
    @Mock private IntegrationSyncLogRepository syncLogRepository;

    @InjectMocks
    private DataFreshnessResolver resolver;

    @Test
    void shouldReportOldestSyncAmongActiveSyncEnabledBindings() {
        Instant threeDaysAgo = NOW.minus(Duration.ofDays(3));
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(Optional.of(azure(true, NOW.minus(Duration.ofHours(1)))));
        when(vcsRepoBindingRepository.findAllActiveByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of(github(true, threeDaysAgo), github(true, null), github(false, NOW.minus(Duration.ofDays(30)))));

        DataFreshness freshness = resolver.resolve(ORG, PROJECT);

        assertThat(freshness.oldestSyncAt()).isEqualTo(threeDaysAgo);
        assertThat(freshness.refreshRunning()).isFalse();
    }

    @Test
    void shouldFlagRunningRefreshOfTheProject() {
        when(azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(ORG, PROJECT))
                .thenReturn(Optional.empty());
        when(vcsRepoBindingRepository.findAllActiveByOrganizationIdAndProjectId(ORG, PROJECT))
                .thenReturn(List.of());
        when(syncLogRepository.existsRunningByOrganizationIdAndProjectIdAndOperationIn(eq(ORG), eq(PROJECT), any()))
                .thenAnswer(inv -> ((java.util.Collection<?>) inv.getArgument(2)).contains(SyncOperation.PROJECT_REFRESH));

        DataFreshness freshness = resolver.resolve(ORG, PROJECT);

        assertThat(freshness.refreshRunning()).isTrue();
        assertThat(freshness.oldestSyncAt()).isNull();
    }

    @Test
    void shouldReturnNullWhenReadIsNotScopedToAProject() {
        assertThat(resolver.resolve(ORG, null)).isNull();
        verifyNoInteractions(azureProjectBindingRepository, vcsRepoBindingRepository, syncLogRepository);
    }

    private static AzureProjectBinding azure(boolean syncEnabled, Instant lastSyncAt) {
        return new AzureProjectBinding("az-1", ORG, PROJECT, "azure-1", "Project", syncEnabled, lastSyncAt,
                null, true, NOW, NOW, 1L);
    }

    private static VcsRepoBinding github(boolean syncEnabled, Instant lastSyncAt) {
        return VcsLegacyFixtures.binding("gh-" + lastSyncAt, ORG, PROJECT, "repo-1", syncEnabled, lastSyncAt,
                true, NOW, NOW, 1L);
    }
}
