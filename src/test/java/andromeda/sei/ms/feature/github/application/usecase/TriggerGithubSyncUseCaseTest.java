package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
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
 * O trigger so valida a conexao e submete ao orquestrador. Isolamento de falhas por binding e a
 * regra de saude (que agora vale para o trigger manual e recupera ERROR) estao em
 * {@code GithubBindingSyncRunnerTest}.
 */
@ExtendWith(MockitoExtension.class)
class TriggerGithubSyncUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();

    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private SyncTargetResolver syncTargetResolver;
    @Mock private SyncOrchestrator syncOrchestrator;

    @InjectMocks
    private TriggerGithubSyncUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldSubmitEverySyncEnabledActiveBindingWithManualTrigger() {
        List<SyncTarget> targets = List.of(
                new SyncTarget(Integration.GITHUB, "bind-1", "proj-1"),
                new SyncTarget(Integration.GITHUB, "bind-2", "proj-1"));
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection()));
        when(syncTargetResolver.forOrganization(ORG, Integration.GITHUB, null)).thenReturn(targets);
        when(syncOrchestrator.submit(any())).thenReturn("sync-1");

        assertThat(useCase.execute()).isEqualTo("sync-1");

        ArgumentCaptor<SyncRequest> captor = ArgumentCaptor.forClass(SyncRequest.class);
        verify(syncOrchestrator).submit(captor.capture());
        assertThat(captor.getValue().operation()).isEqualTo(SyncOperation.ORGANIZATION_SYNC);
        assertThat(captor.getValue().integration()).isEqualTo(Integration.GITHUB);
        assertThat(captor.getValue().trigger()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(captor.getValue().targets()).isEqualTo(targets);
    }

    @Test
    void shouldThrowConnectionNotFoundWhenNoActiveConnection() {
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute()).isInstanceOf(GithubConnectionNotFoundException.class);
        verifyNoInteractions(syncOrchestrator);
    }

    private static GithubConnection connection() {
        return new GithubConnection("gh-1", ORG, "enc-pat", "user-1", GithubConnectionStatus.CONNECTED,
                null, 0, null, null, true, NOW, NOW, 1L);
    }
}
