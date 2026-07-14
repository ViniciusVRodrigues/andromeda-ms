package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriggerAzureSyncUseCaseTest {

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private SyncAzureSprintsUseCase syncAzureSprintsUseCase;
    @Mock private SyncAzureWorkItemsUseCase syncAzureWorkItemsUseCase;

    @InjectMocks
    private TriggerAzureSyncUseCase useCase;

    private static final Instant NOW = Instant.now();

    private AzureConnection connection() {
        return new AzureConnection("conn-1", "org-1", "https://dev.azure.com/org",
                null, null, null, "user-1", AzureConnectionStatus.CONNECTED,
                null, 0, null, null, true, NOW, NOW, null,
                AzureAuthMethod.PAT, "enc-pat");
    }

    private AzureProjectBinding binding(String id) {
        return new AzureProjectBinding(id, "org-1", "proj-" + id, "azure-" + id, "Project " + id,
                true, null, null, true, NOW, NOW, null);
    }

    @BeforeEach
    void setUp() {
        TenantContextHolder.set("org-1");
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void unscopedExecute_shouldSyncAllSyncEnabledBindings() {
        AzureConnection connection = connection();
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding("bind-1"), binding("bind-2")));

        useCase.execute();

        verify(syncAzureSprintsUseCase, times(2)).execute(eq(connection), any(), eq(SyncTrigger.MANUAL));
        verify(syncAzureWorkItemsUseCase, times(2)).execute(eq(connection), any(), eq(SyncTrigger.MANUAL));
        verify(azureProjectBindingRepository, never()).findByIdAndOrganizationIdAndActiveTrue(any(), any());
    }

    @Test
    void scopedExecute_shouldSyncOnlyTheGivenBinding() {
        AzureConnection connection = connection();
        AzureProjectBinding target = binding("bind-1");
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("bind-1", "org-1"))
                .thenReturn(Optional.of(target));

        useCase.execute("bind-1");

        verify(syncAzureSprintsUseCase, times(1)).execute(connection, target, SyncTrigger.MANUAL);
        verify(syncAzureWorkItemsUseCase, times(1)).execute(connection, target, SyncTrigger.MANUAL);
        verify(azureProjectBindingRepository, never())
                .findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(any());
    }

    @Test
    void scopedExecute_shouldThrowWhenBindingNotFound() {
        AzureConnection connection = connection();
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.of(connection));
        when(azureProjectBindingRepository.findByIdAndOrganizationIdAndActiveTrue("missing", "org-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("missing"))
                .isInstanceOf(AzureBindingNotFoundException.class);
    }

    @Test
    void execute_shouldThrowWhenNoConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue("org-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute())
                .isInstanceOf(AzureConnectionNotFoundException.class);
    }
}
