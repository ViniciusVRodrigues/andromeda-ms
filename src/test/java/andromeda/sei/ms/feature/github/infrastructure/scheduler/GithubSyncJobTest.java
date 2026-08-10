package andromeda.sei.ms.feature.github.infrastructure.scheduler;

import andromeda.sei.ms.feature.github.application.usecase.SyncGithubRepoDataUseCase;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubSyncJobTest {

    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;
    @Mock private SyncGithubRepoDataUseCase syncGithubRepoDataUseCase;

    private GithubSyncJob job;

    @BeforeEach
    void setUp() {
        job = new GithubSyncJob(githubConnectionRepository, githubRepoBindingRepository, syncGithubRepoDataUseCase);
    }

    @Test
    void shouldIsolateOneBindingFailure_andContinueSyncingRemainingBindings() {
        var connection = connectedConnection("org-1");
        var binding1 = binding("org-1", "proj-1");
        var binding2 = binding("org-1", "proj-2");

        when(githubConnectionRepository.findAllByStatusAndActiveTrue(GithubConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding1, binding2));
        doThrow(new RuntimeException("boom")).when(syncGithubRepoDataUseCase).execute(any(), org.mockito.ArgumentMatchers.eq(binding1), any());
        doNothing().when(syncGithubRepoDataUseCase).execute(any(), org.mockito.ArgumentMatchers.eq(binding2), any());

        job.run();

        verify(syncGithubRepoDataUseCase).execute(any(), org.mockito.ArgumentMatchers.eq(binding1), any());
        verify(syncGithubRepoDataUseCase).execute(any(), org.mockito.ArgumentMatchers.eq(binding2), any());
    }

    @Test
    void shouldFlipConnectionToError_afterThreeConsecutiveFailedRuns() {
        var connection = connectedConnection("org-1");
        connection.setConsecutiveFailures(2);
        var binding = binding("org-1", "proj-1");

        when(githubConnectionRepository.findAllByStatusAndActiveTrue(GithubConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        doThrow(new RuntimeException("boom")).when(syncGithubRepoDataUseCase).execute(any(), any(), any());

        job.run();

        ArgumentCaptor<GithubConnection> captor = ArgumentCaptor.forClass(GithubConnection.class);
        verify(githubConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isEqualTo(GithubConnectionStatus.ERROR);
    }

    @Test
    void shouldResetFailureCounter_onSuccessfulRunWithZeroFailures() {
        var connection = connectedConnection("org-1");
        connection.setConsecutiveFailures(2);
        var binding = binding("org-1", "proj-1");

        when(githubConnectionRepository.findAllByStatusAndActiveTrue(GithubConnectionStatus.CONNECTED))
                .thenReturn(List.of(connection));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue("org-1"))
                .thenReturn(List.of(binding));
        doNothing().when(syncGithubRepoDataUseCase).execute(any(), any(), any());

        job.run();

        ArgumentCaptor<GithubConnection> captor = ArgumentCaptor.forClass(GithubConnection.class);
        verify(githubConnectionRepository).save(captor.capture());
        assertThat(captor.getValue().getConsecutiveFailures()).isEqualTo(0);
        assertThat(captor.getValue().getLastSuccessAt()).isNotNull();
    }

    private GithubConnection connectedConnection(String orgId) {
        Instant now = Instant.now();
        return new GithubConnection(orgId + "-conn", orgId, "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null);
    }

    private GithubRepoBinding binding(String orgId, String projectId) {
        Instant now = Instant.now();
        return new GithubRepoBinding(projectId + "-binding", orgId, projectId, "octo/repo",
                true, null, true, now, now, null);
    }
}
