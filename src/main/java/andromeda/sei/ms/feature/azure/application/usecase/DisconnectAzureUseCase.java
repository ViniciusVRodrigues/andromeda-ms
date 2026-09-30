package andromeda.sei.ms.feature.azure.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureConnection;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.azure.domain.exception.AzureConnectionNotFoundException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DisconnectAzureUseCase {

    private final AzureConnectionRepository azureConnectionRepository;
    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;

    public void execute() {
        String orgId = TenantContextHolder.get();
        AzureConnection connection = azureConnectionRepository
                .findByOrganizationIdAndActiveTrue(orgId)
                .orElseThrow(() -> new AzureConnectionNotFoundException(orgId));

        connection.setActive(false);
        connection.setStatus(AzureConnectionStatus.DISCONNECTED);
        azureConnectionRepository.save(connection);

        List<AzureProjectBinding> bindings = azureProjectBindingRepository
                .findAllByOrganizationIdAndActiveTrue(orgId);
        bindings.forEach(b -> b.setSyncEnabled(false));
        azureProjectBindingRepository.saveAll(bindings);

        /* Azure Repos shares the connection: its bindings stop syncing too (like GitHub's on disconnect). */
        List<VcsRepoBinding> repoBindings = vcsRepoBindingRepository
                .findAllActiveByOrganizationIdAndProvider(orgId, VcsProvider.AZURE_REPOS);
        repoBindings.forEach(b -> b.setSyncEnabled(false));
        vcsRepoBindingRepository.saveAll(repoBindings);
    }
}
