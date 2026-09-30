package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.exception.AzureBindingNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairAzureDataUseCaseTest {

    @Mock private SyncTargetResolver syncTargetResolver;
    @Mock private SyncOrchestrator syncOrchestrator;

    @InjectMocks
    private RepairAzureDataUseCase useCase;

    @Test
    void shouldSubmitProjectBindingsThroughOrchestratorResettingWatermarks() {
        List<SyncTarget> targets = List.of(new SyncTarget(Integration.AZURE_DEVOPS, "bind-1", "proj-1"));
        when(syncTargetResolver.forOrganization("org-1", Integration.AZURE_DEVOPS, "proj-1")).thenReturn(targets);
        when(syncOrchestrator.submit(any())).thenReturn("sync-1");

        assertThat(useCase.execute("org-1", "proj-1")).isEqualTo("sync-1");

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncOrchestrator).submit(captor.capture());
        assertThat(captor.getValue().resetWatermarks()).isTrue();
        assertThat(captor.getValue().targets()).isEqualTo(targets);
    }

    @Test
    void shouldThrowWhenThereIsNothingToRepair() {
        when(syncTargetResolver.forOrganization("org-1", Integration.AZURE_DEVOPS, null)).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.execute("org-1", null)).isInstanceOf(AzureBindingNotFoundException.class);
        verifyNoInteractions(syncOrchestrator);
    }
}
