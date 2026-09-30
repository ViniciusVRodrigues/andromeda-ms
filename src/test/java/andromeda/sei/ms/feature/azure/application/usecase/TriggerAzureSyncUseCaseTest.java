package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.sync.domain.exception.SyncCooldownActiveException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O trigger agora so valida e submete ao orquestrador (on-demand-refresh). Os casos que viviam
 * aqui sobre o sync em si — encadeamento sprints → work items, watermark nulo no primeiro sync,
 * isolamento de falhas — foram portados para {@code AzureBindingSyncRunnerTest}.
 */
@ExtendWith(MockitoExtension.class)
class TriggerAzureSyncUseCaseTest {

    private static final Instant NOW = Instant.now();

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private SyncTargetResolver syncTargetResolver;
    @Mock private SyncOrchestrator syncOrchestrator;

    @InjectMocks
    private TriggerAzureSyncUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void unscopedExecute_shouldSubmitEverySyncEnabledBindingAsManualOrganizationSync() {
        List<SyncTarget> targets = List.of(
                new SyncTarget(Integration.AZURE_DEVOPS, "bind-1", "proj-1"),
                new SyncTarget(Integration.AZURE_DEVOPS, "bind-2", "proj-2"));
        givenConnection();
        when(syncTargetResolver.forOrganization("org-1", Integration.AZURE_DEVOPS, null)).thenReturn(targets);
        when(syncOrchestrator.submit(any())).thenReturn("sync-1");

        String syncId = useCase.execute();

        assertThat(syncId).isEqualTo("sync-1");
        SyncRequest request = capturedRequest();
        assertThat(request.operation()).isEqualTo(SyncOperation.ORGANIZATION_SYNC);
        assertThat(request.integration()).isEqualTo(Integration.AZURE_DEVOPS);
        assertThat(request.trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(request.targets()).isEqualTo(targets);
        assertThat(request.resetWatermarks()).isFalse();
    }

    @Test
    void scopedExecute_shouldSubmitOnlyTheGivenBindingEvenWhenSyncIsDisabled() {
        givenConnection();
        AzureProjectBinding binding = new AzureProjectBinding("bind-1", "org-1", "proj-1", "azure-1", "Project",
                false, null, null, true, NOW, NOW, 1L);
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(binding));
        when(syncOrchestrator.submit(any())).thenReturn("sync-2");

        assertThat(useCase.execute("bind-1")).isEqualTo("sync-2");

        SyncRequest request = capturedRequest();
        assertThat(request.targets()).containsExactly(new SyncTarget(Integration.AZURE_DEVOPS, "bind-1", "proj-1"));
        assertThat(request.projectId()).isEqualTo("proj-1");
        verifyNoInteractions(syncTargetResolver);
    }

    @Test
    void scopedExecute_shouldThrowWhenBindingNotFound() {
        givenConnection();
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("missing", "org-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing")).isInstanceOf(AzureBindingNotFoundException.class);
        verifyNoInteractions(syncOrchestrator);
    }

    @Test
    void execute_shouldThrowWhenNoConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute()).isInstanceOf(AzureConnectionNotFoundException.class);
        verifyNoInteractions(syncOrchestrator);
    }

    @Test
    void execute_shouldPropagateCooldownRejection() {
        givenConnection();
        when(syncTargetResolver.forOrganization("org-1", Integration.AZURE_DEVOPS, null)).thenReturn(List.of());
        when(syncOrchestrator.submit(any())).thenThrow(new SyncCooldownActiveException(12));

        assertThatThrownBy(() -> useCase.execute()).isInstanceOf(SyncCooldownActiveException.class);
    }

    private void givenConnection() {
        AzureConnection connection = new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                null, null, null, "user-1", AzureConnectionStatus.ERROR, null, 3, null, null, true, NOW, NOW, 1L,
                AzureAuthMethod.PAT, "enc-pat");
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1")).thenReturn(Optional.of(connection));
    }

    private SyncRequest capturedRequest() {
        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncOrchestrator).submit(captor.capture());
        return captor.getValue();
    }
}
