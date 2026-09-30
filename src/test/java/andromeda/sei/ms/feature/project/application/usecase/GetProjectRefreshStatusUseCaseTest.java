package andromeda.sei.ms.feature.project.application.usecase;

import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.project.domain.exception.ProjectRefreshNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProjectRefreshStatusUseCaseTest {

    private static final String ORG = "org-1";
    private static final String PROJECT = "proj-1";
    private static final Instant NOW = Instant.now();

    @Mock private IntegrationSyncLogRepository syncLogRepository;

    @InjectMocks
    private GetProjectRefreshStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldReturnParentStatusWithOneEntryPerBinding() {
        IntegrationSyncLog parent = log("sync-1", null, null, SyncOperation.PROJECT_REFRESH, SyncStatus.PARTIAL, PROJECT);
        IntegrationSyncLog azure = log("c-1", "az-1", Integration.AZURE_DEVOPS, SyncOperation.SYNC_BINDING, SyncStatus.SUCCESS, PROJECT);
        IntegrationSyncLog github = log("c-2", "gh-1", Integration.GITHUB, SyncOperation.SYNC_BINDING, SyncStatus.FAILED, PROJECT);
        github.setErrorCode("GITHUB_SYNC_FAILED");
        when(syncLogRepository.findByIdAndOrganizationId("sync-1", ORG)).thenReturn(Optional.of(parent));
        when(syncLogRepository.findAllByOrganizationIdAndParentSyncId(ORG, "sync-1")).thenReturn(List.of(azure, github));

        var output = useCase.execute(PROJECT, "sync-1");

        assertThat(output.status()).isEqualTo(SyncStatus.PARTIAL);
        assertThat(output.bindings()).hasSize(2);
        assertThat(output.bindings().get(1).bindingId()).isEqualTo("gh-1");
        assertThat(output.bindings().get(1).status()).isEqualTo(SyncStatus.FAILED);
        assertThat(output.bindings().get(1).errorCode()).isEqualTo("GITHUB_SYNC_FAILED");
    }

    @Test
    void shouldReportRunningWhileBackgroundWorkIsNotFinished() {
        IntegrationSyncLog parent = log("sync-1", null, null, SyncOperation.PROJECT_REFRESH, SyncStatus.RUNNING, PROJECT);
        when(syncLogRepository.findByIdAndOrganizationId("sync-1", ORG)).thenReturn(Optional.of(parent));
        when(syncLogRepository.findAllByOrganizationIdAndParentSyncId(ORG, "sync-1")).thenReturn(List.of());

        assertThat(useCase.execute(PROJECT, "sync-1").status()).isEqualTo(SyncStatus.RUNNING);
    }

    @Test
    void shouldNotFindRefreshOfAnotherProject() {
        IntegrationSyncLog parent = log("sync-1", null, null, SyncOperation.PROJECT_REFRESH, SyncStatus.SUCCESS, "proj-2");
        when(syncLogRepository.findByIdAndOrganizationId("sync-1", ORG)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> useCase.execute(PROJECT, "sync-1")).isInstanceOf(ProjectRefreshNotFoundException.class);
    }

    @Test
    void shouldNotFindLogThatIsNotAProjectRefresh() {
        IntegrationSyncLog other = log("sync-1", null, Integration.GITHUB, SyncOperation.SYNC_REPOS, SyncStatus.SUCCESS, PROJECT);
        when(syncLogRepository.findByIdAndOrganizationId("sync-1", ORG)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> useCase.execute(PROJECT, "sync-1")).isInstanceOf(ProjectRefreshNotFoundException.class);
    }

    @Test
    void shouldNotFindRefreshOfAnotherOrganization() {
        when(syncLogRepository.findByIdAndOrganizationId("sync-1", ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(PROJECT, "sync-1")).isInstanceOf(ProjectRefreshNotFoundException.class);
    }

    private static IntegrationSyncLog log(String id, String bindingId, Integration integration, SyncOperation operation,
                                          SyncStatus status, String projectId) {
        IntegrationSyncLog log = new IntegrationSyncLog(id, ORG, integration, operation, status, SyncTrigger.MANUAL,
                projectId, NOW, null, null, 0, 0, null, null);
        log.setBindingId(bindingId);
        return log;
    }
}
