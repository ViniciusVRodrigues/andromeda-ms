package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.BranchListOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoBindingNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsListBranchesFailedException;
import andromeda.sei.ms.shared.domain.exception.DomainException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lists the branches of a repository, for a repository not bound yet ({@link #ofAvailable}) or for a
 * binding ({@link #ofBinding}). Reads at most {@link VcsProviderClient#MAX_BRANCHES_LISTED} and says when
 * the provider has more. A provider failure becomes {@code 502 VCS_LIST_BRANCHES_FAILED}; domain errors
 * (a dead GitHub token, a missing connection) keep their own codes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListRepositoryBranchesUseCase {

    private final VcsProviderRegistry providerRegistry;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsRepoBindingRepository bindingRepository;

    public BranchListOutput ofAvailable(VcsProvider provider, String externalRepoId, String fullName) {
        String orgId = TenantContextHolder.get();
        VcsProvider effective = provider != null ? provider : VcsProvider.GITHUB;
        VcsCredentials credentials = credentialsOf(orgId, effective);
        VcsProviderClient client = providerRegistry.client(effective);
        VcsRemoteRepository remote = RemoteRepositoryLookup.fetch(client, credentials, effective,
                RemoteRepositoryLookup.blankToNull(externalRepoId), RemoteRepositoryLookup.blankToNull(fullName));
        VcsRepository repository = new VcsRepository(null, orgId, effective, remote.externalRepoId(),
                remote.displayName());
        repository.setExternalProjectId(remote.externalProjectId());
        repository.setDefaultBranch(remote.defaultBranch());
        return list(client, credentials, repository);
    }

    public BranchListOutput ofBinding(String bindingId) {
        String orgId = TenantContextHolder.get();
        VcsRepoBinding binding = bindingRepository.findByIdAndOrganizationIdAndActiveTrue(bindingId, orgId)
                .orElseThrow(() -> new RepoBindingNotFoundException(bindingId));
        VcsRepository repository = repositoryRepository.findByIdAndOrganizationId(binding.getRepositoryId(), orgId)
                .orElseThrow(() -> new RepoNotFoundException(binding.getRepositoryId()));
        return list(providerRegistry.client(binding.getProvider()), credentialsOf(orgId, binding.getProvider()),
                repository);
    }

    private VcsCredentials credentialsOf(String orgId, VcsProvider provider) {
        return providerRegistry.credentialsProvider(provider).credentials(orgId)
                .orElseThrow(() -> new VcsConnectionNotFoundException(provider.name()));
    }

    private BranchListOutput list(VcsProviderClient client, VcsCredentials credentials, VcsRepository repository) {
        List<VcsBranch> read;
        try {
            read = client.listBranches(credentials, repository);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Listing branches failed: repo={} provider={}: {}", repository.getDisplayName(),
                    repository.getProvider(), e.getMessage());
            throw new VcsListBranchesFailedException(e);
        }
        boolean truncated = read.size() > VcsProviderClient.MAX_BRANCHES_LISTED;
        List<VcsBranch> branches = truncated ? read.subList(0, VcsProviderClient.MAX_BRANCHES_LISTED) : read;
        return new BranchListOutput(List.copyOf(branches), branches.size(), truncated);
    }
}
