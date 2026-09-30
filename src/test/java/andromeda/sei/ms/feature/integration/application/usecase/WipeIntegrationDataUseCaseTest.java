package andromeda.sei.ms.feature.integration.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper;
import andromeda.sei.ms.feature.integration.domain.IntegrationDataWiper.WipeScope;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 6.3: Azure DevOps has two wipers (Boards and Azure Repos). Both run on an Azure wipe and
 * neither replaces the other; the GitHub wiper is untouched.
 */
@ExtendWith(MockitoExtension.class)
class WipeIntegrationDataUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private IntegrationDataWiper boardsWiper;
    @Mock private IntegrationDataWiper azureReposWiper;
    @Mock private IntegrationDataWiper githubWiper;
    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;

    @Test
    void shouldRunEveryWiperOfTheIntegration() {
        when(boardsWiper.integration()).thenReturn(Integration.AZURE_DEVOPS);
        when(azureReposWiper.integration()).thenReturn(Integration.AZURE_DEVOPS);
        when(githubWiper.integration()).thenReturn(Integration.GITHUB);
        WipeScope scope = WipeScope.forProject("proj-1");
        when(boardsWiper.wipe(ORG, scope)).thenReturn(Map.of("work_items", 5L));
        when(azureReposWiper.wipe(ORG, scope)).thenReturn(Map.of("vcs_pull_requests", 3L));
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(ORG))
                .thenReturn(List.of());
        WipeIntegrationDataUseCase useCase = new WipeIntegrationDataUseCase(
                List.of(boardsWiper, azureReposWiper, githubWiper), syncLogRepository, azureProjectBindingRepository);

        useCase.execute(ORG, Integration.AZURE_DEVOPS, scope);

        verify(boardsWiper).wipe(ORG, scope);
        verify(azureReposWiper).wipe(ORG, scope);
        verify(githubWiper, never()).wipe(ORG, scope);
        ArgumentCaptor<IntegrationSyncLog> audit = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository).save(audit.capture());
        assertThat(audit.getValue().getOperation()).isEqualTo(SyncOperation.WIPE_DATA);
        assertThat(audit.getValue().getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(audit.getValue().getItemsProcessed()).isEqualTo(8);
    }
}
