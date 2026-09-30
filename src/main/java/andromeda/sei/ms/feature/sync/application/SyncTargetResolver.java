package andromeda.sei.ms.feature.sync.application;

import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.AzureProjectBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.feature.sync.domain.SyncTargetKind;
import andromeda.sei.ms.feature.vcs.application.VcsIntegrations;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolve quais bindings um pedido de sync alcanca. Toda consulta e escopada pela organizacao, e
 * so bindings {@code active && syncEnabled} entram (exceto o sync por binding do wizard, em que
 * quem chama ja decidiu).
 */
@Component
@RequiredArgsConstructor
public class SyncTargetResolver {

    private final AzureProjectBindingRepository azureProjectBindingRepository;
    private final VcsRepoBindingRepository vcsRepoBindingRepository;
    private final AzureConnectionRepository azureConnectionRepository;
    private final GithubConnectionRepository githubConnectionRepository;

    /** Bindings de work tracking e de repositorio do projeto. */
    public List<SyncTarget> forProject(String organizationId, String projectId) {
        List<SyncTarget> targets = new ArrayList<>();
        azureProjectBindingRepository.findByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                .filter(AzureProjectBinding::isSyncEnabled)
                .map(SyncTargetResolver::toTarget)
                .ifPresent(targets::add);
        vcsRepoBindingRepository.findAllActiveByOrganizationIdAndProjectId(organizationId, projectId)
                .stream()
                .filter(VcsRepoBinding::isSyncEnabled)
                .map(SyncTargetResolver::toTarget)
                .forEach(targets::add);
        return targets;
    }

    /**
     * Bindings sincronizaveis de uma integracao na organizacao.
     *
     * @param projectId quando presente, limita ao projeto (usado pelo repair)
     */
    public List<SyncTarget> forOrganization(String organizationId, Integration integration, String projectId) {
        Stream<SyncTarget> targets = switch (integration) {
            /* Boards first, then the Azure Repos bindings that share the same connection. */
            case AZURE_DEVOPS -> Stream.concat(
                    azureProjectBindingRepository.findAllByOrganizationIdAndSyncEnabledTrueAndActiveTrue(organizationId)
                            .stream().map(SyncTargetResolver::toTarget),
                    repositoryTargets(organizationId, VcsProvider.AZURE_REPOS));
            case GITHUB -> repositoryTargets(organizationId, VcsProvider.GITHUB);
            default -> Stream.empty();
        };
        return targets.filter(target -> projectId == null || projectId.equals(target.projectId())).toList();
    }

    /**
     * Alvos do sync agendado: todos os bindings sincronizaveis das conexoes {@code CONNECTED}. Uma
     * conexao em {@code ERROR} fica de fora do cron ate um refresh bem-sucedido ou reconexao.
     */
    public List<SyncTarget> forScheduled(String organizationId) {
        List<SyncTarget> targets = new ArrayList<>();
        boolean azureConnected = azureConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .filter(connection -> connection.getStatus() == AzureConnectionStatus.CONNECTED)
                .isPresent();
        if (azureConnected) {
            targets.addAll(forOrganization(organizationId, Integration.AZURE_DEVOPS, null));
        }
        boolean githubConnected = githubConnectionRepository.findByOrganizationIdAndActiveTrue(organizationId)
                .filter(connection -> connection.getStatus() == GithubConnectionStatus.CONNECTED)
                .isPresent();
        if (githubConnected) {
            targets.addAll(forOrganization(organizationId, Integration.GITHUB, null));
        }
        return targets;
    }

    private Stream<SyncTarget> repositoryTargets(String organizationId, VcsProvider provider) {
        return vcsRepoBindingRepository.findAllSyncEnabledByOrganizationIdAndProvider(organizationId, provider)
                .stream().map(SyncTargetResolver::toTarget);
    }

    public static SyncTarget toTarget(AzureProjectBinding binding) {
        return new SyncTarget(Integration.AZURE_DEVOPS, binding.getId(), binding.getProjectId());
    }

    /** A repository binding is filed under its provider's integration (Azure Repos under AZURE_DEVOPS). */
    public static SyncTarget toTarget(VcsRepoBinding binding) {
        return new SyncTarget(VcsIntegrations.integrationOf(binding.getProvider()), binding.getId(),
                binding.getProjectId(), SyncTargetKind.REPOSITORY);
    }
}
