package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureAuthMethod;
import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Disconnecting Azure DevOps pauses the Azure Repos bindings that depend on the same connection. */
@ExtendWith(MockitoExtension.class)
class DisconnectAzureUseCaseTest {

    private static final String ORG = "org-1";

    @Mock private AzureConnectionRepository azureConnectionRepository;
    @Mock private AzureProjectBindingRepository azureProjectBindingRepository;
    @Mock private VcsRepoBindingRepository vcsRepoBindingRepository;
    @InjectMocks private DisconnectAzureUseCase useCase;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldDisableSyncOfAzureReposBindings() {
        AzureConnection connection = new AzureConnection("c-1", ORG, "https://dev.azure.com/acme", null, null, null,
                null, AzureConnectionStatus.CONNECTED, null, 0, null, null, true, Instant.now(), Instant.now(), 1L,
                AzureAuthMethod.PAT, "enc");
        VcsRepoBinding binding = new VcsRepoBinding("b-1", ORG, "proj-1", "repo-1", VcsProvider.AZURE_REPOS, true);
        when(azureConnectionRepository.findByOrganizationIdAndActiveTrue(ORG)).thenReturn(Optional.of(connection));
        when(azureProjectBindingRepository.findAllByOrganizationIdAndActiveTrue(ORG)).thenReturn(List.of());
        when(vcsRepoBindingRepository.findAllActiveByOrganizationIdAndProvider(ORG, VcsProvider.AZURE_REPOS))
                .thenReturn(List.of(binding));

        useCase.execute();

        assertThat(connection.getStatus()).isEqualTo(AzureConnectionStatus.DISCONNECTED);
        assertThat(binding.isSyncEnabled()).isFalse();
        verify(vcsRepoBindingRepository).saveAll(List.of(binding));
    }
}
