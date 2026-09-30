package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.exception.AzureTokenRefreshFailedException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureAuthStrategyResolver;
import andromeda.sei.ms.feature.azure.infrastructure.security.AzureTokenRefreshService;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Task 5.4: Azure Repos reuses the Azure DevOps connection (PAT or refreshed OAuth token). */
@ExtendWith(MockitoExtension.class)
class AzureReposCredentialsProviderTest {

    private static final String ORG = "org-1";

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureTokenRefreshService azureTokenRefreshService;
    @Mock private AzureAuthStrategyResolver authStrategyResolver;
    @InjectMocks private AzureReposCredentialsProvider provider;

    @Test
    void shouldHandThePatHeaderAndTheOrganizationUrl() {
        AzureConnection pat = connection(AzureAuthMethod.PAT, AzureConnectionStatus.CONNECTED);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(pat));
        when(authStrategyResolver.resolve(pat)).thenReturn("Basic OnBhdA==");

        VcsCredentials credentials = provider.credentials(ORG).orElseThrow();

        assertThat(credentials.provider()).isEqualTo(VcsProvider.AZURE_REPOS);
        assertThat(credentials.token()).isEqualTo("Basic OnBhdA==");
        assertThat(credentials.baseUrl()).isEqualTo("https://dev.azure.com/acme");
        assertThat(credentials.inError()).isFalse();
        verifyNoInteractions(azureTokenRefreshService);
    }

    @Test
    void shouldRefreshTheOAuthTokenBeforeUsingIt() {
        AzureConnection oauth = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR);
        AzureConnection refreshed = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(oauth));
        when(azureTokenRefreshService.refreshIfNeeded(oauth)).thenReturn(refreshed);
        when(authStrategyResolver.resolve(refreshed)).thenReturn("Bearer new");

        VcsCredentials credentials = provider.credentials(ORG).orElseThrow();

        assertThat(credentials.token()).isEqualTo("Bearer new");
        assertThat(credentials.inError()).isTrue();
    }

    @Test
    void shouldReportTheErrorFlagSeenBeforeTheRefreshSavedTheConnection() {
        AzureConnection inError = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.ERROR);
        AzureConnection refreshedAsConnected = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.CONNECTED);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(inError));
        when(azureTokenRefreshService.refreshIfNeeded(inError)).thenReturn(refreshedAsConnected);
        when(authStrategyResolver.resolve(refreshedAsConnected)).thenReturn("Bearer new");

        assertThat(provider.credentials(ORG).orElseThrow().inError()).isTrue();
    }

    @Test
    void shouldFailWithADomainErrorWhenTheRefreshFails() {
        AzureConnection oauth = connection(AzureAuthMethod.OAUTH, AzureConnectionStatus.CONNECTED);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(oauth));
        when(azureTokenRefreshService.refreshIfNeeded(oauth)).thenThrow(new RuntimeException("AZURE_REFRESH_TOKEN_EXPIRED"));

        assertThatThrownBy(() -> provider.credentials(ORG)).isInstanceOf(AzureTokenRefreshFailedException.class)
                .hasFieldOrPropertyWithValue("code", "AZURE_TOKEN_REFRESH_FAILED");
    }

    @Test
    void shouldHaveNoCredentialWithoutAnActiveConnection() {
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.empty());

        assertThat(provider.credentials(ORG)).isEmpty();
        assertThat(provider.provider()).isEqualTo(VcsProvider.AZURE_REPOS);
    }

    static AzureConnection connection(AzureAuthMethod method, AzureConnectionStatus status) {
        return new AzureConnection("c-1", ORG, "https://dev.azure.com/acme/", "enc-access", "enc-refresh",
                Instant.now().plusSeconds(3600), "u-1", status, null, 0, null, null, true, Instant.now(), Instant.now(),
                1L, method, "enc-pat");
    }
}
