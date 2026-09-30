package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionHealthUpdaterTest {

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private GithubConnectionRepository githubConnectionRepository;

    @InjectMocks
    private ConnectionHealthUpdater updater;

    @Test
    void shouldRecoverErroredAzureConnectionOnFullSuccess() {
        AzureConnection connection = azure(AzureConnectionStatus.ERROR, 3);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordAzure(ORG, true);

        AzureConnection saved = captureAzure();
        assertThat(saved.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
        assertThat(saved.getConsecutiveFailures()).isZero();
        assertThat(saved.getLastSuccessAt()).isNotNull();
    }

    @Test
    void shouldIncrementAzureFailuresWithoutFlippingBeforeThreshold() {
        AzureConnection connection = azure(AzureConnectionStatus.CONNECTED, 1);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordAzure(ORG, false);

        AzureConnection saved = captureAzure();
        assertThat(saved.getConsecutiveFailures()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo(AzureConnectionStatus.CONNECTED);
    }

    @Test
    void shouldFlipAzureConnectionToErrorOnThirdConsecutiveFailure() {
        AzureConnection connection = azure(AzureConnectionStatus.CONNECTED, 2);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordAzure(ORG, false);

        assertThat(captureAzure().getStatus()).isEqualTo(AzureConnectionStatus.ERROR);
    }

    @Test
    void shouldDoNothingWhenAzureConnectionIsGone() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        updater.recordAzure(ORG, false);

        verify(azureConnectionRepository, never()).save(any());
    }

    @Test
    void shouldRecoverErroredGithubConnectionOnFullSuccess() {
        GithubConnection connection = github(GithubConnectionStatus.ERROR, 5);
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordGithub(ORG, true);

        GithubConnection saved = captureGithub();
        assertThat(saved.getStatus()).isEqualTo(GithubConnectionStatus.CONNECTED);
        assertThat(saved.getConsecutiveFailures()).isZero();
        assertThat(saved.getLastSuccessAt()).isNotNull();
    }

    @Test
    void shouldFlipGithubConnectionToErrorOnThirdConsecutiveFailure() {
        GithubConnection connection = github(GithubConnectionStatus.CONNECTED, 2);
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordGithub(ORG, false);

        GithubConnection saved = captureGithub();
        assertThat(saved.getConsecutiveFailures()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(GithubConnectionStatus.ERROR);
    }

    private AzureConnection captureAzure() {
        ArgumentCaptor<AzureConnection> captor = ArgumentCaptor.forClass(AzureConnection.class);
        verify(azureConnectionRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldRouteVcsHealthToTheProviderConnection() {
        GithubConnection connection = github(GithubConnectionStatus.CONNECTED, 2);
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));

        updater.recordVcs(andromeda.sei.ms.feature.vcs.domain.VcsProvider.GITHUB, ORG, false);

        assertThat(captureGithub().getStatus()).isEqualTo(GithubConnectionStatus.ERROR);
    }

    private GithubConnection captureGithub() {
        ArgumentCaptor<GithubConnection> captor = ArgumentCaptor.forClass(GithubConnection.class);
        verify(githubConnectionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static AzureConnection azure(AzureConnectionStatus status, int failures) {
        return new AzureConnection("az-1", ORG, "https://dev.azure.com/org", null, null, null,
                "user-1", status, null, failures, null, null, true, NOW, NOW, 1L,
                AzureAuthMethod.PAT, "enc-pat");
    }

    private static GithubConnection github(GithubConnectionStatus status, int failures) {
        return new GithubConnection("gh-1", ORG, "enc-pat", "user-1", status, null, failures,
                null, null, true, NOW, NOW, 1L);
    }
}
