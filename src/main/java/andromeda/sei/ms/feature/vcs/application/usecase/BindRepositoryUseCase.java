package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.dto.BindRepositoryInput;
import andromeda.sei.ms.feature.vcs.application.dto.RepoBindingOutput;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoAlreadyBoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsConnectionNotFoundException;
import andromeda.sei.ms.shared.domain.exception.DomainException;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Binds a repository to a project (github-integration "Repo binding", vcs-core D2): requires an
 * active connection of the provider, resolves the repository to its {@code (provider,
 * externalRepoId)} identity — a GitHub {@code owner/name} typed by the admin is resolved to the
 * numeric id first — upserts it in {@code vcs_repositories}, and binds its internal id.
 *
 * <p>Exclusivity: an actively bound repository cannot be bound to another project
 * ({@code 409 REPO_ALREADY_BOUND}); re-binding to the same project only updates {@code syncEnabled}.
 */
@Component
@RequiredArgsConstructor
public class BindRepositoryUseCase {

    private final VcsProviderRegistry providerRegistry;
    private final VcsRepositoryRepository repositoryRepository;
    private final VcsRepoBindingRepository bindingRepository;

    public RepoBindingOutput execute(String projectId, BindRepositoryInput input) {
        String orgId = TenantContextHolder.get();
        VcsProvider provider = input.provider() != null ? input.provider() : VcsProvider.GITHUB;
        VcsCredentials credentials = providerRegistry.credentialsProvider(provider).credentials(orgId)
                .orElseThrow(() -> new VcsConnectionNotFoundException(provider.name()));

        VcsRepository repository = upsertRepository(orgId, provider, credentials, input);
        Optional<VcsRepoBinding> active = bindingRepository.findActiveByOrganizationIdAndRepositoryId(
                orgId, repository.getId());
        if (active.isPresent() && !active.get().getProjectId().equals(projectId)) {
            throw new RepoAlreadyBoundException(repository.getDisplayName());
        }

        VcsRepoBinding binding = active.orElseGet(() ->
                new VcsRepoBinding(null, orgId, projectId, repository.getId(), provider, input.syncEnabled()));
        binding.setSyncEnabled(input.syncEnabled());
        return RepoBindingOutputs.of(bindingRepository.save(binding), repository);
    }

    private VcsRepository upsertRepository(String orgId, VcsProvider provider, VcsCredentials credentials,
                                           BindRepositoryInput input) {
        String externalRepoId = blankToNull(input.externalRepoId());
        if (externalRepoId != null) {
            Optional<VcsRepository> known = repositoryRepository
                    .findByOrganizationIdAndProviderAndExternalRepoId(orgId, provider, externalRepoId);
            if (known.isPresent() && known.get().isActive()) return known.get();
        }
        VcsRemoteRepository remote = fetch(providerRegistry.client(provider), credentials, provider,
                externalRepoId, blankToNull(input.fullName()));
        return save(orgId, provider, remote);
    }

    private VcsRemoteRepository fetch(VcsProviderClient client, VcsCredentials credentials, VcsProvider provider,
                                      String externalRepoId, String fullName) {
        if (externalRepoId == null && fullName == null) throw new RepoIdInvalidException("(empty)");
        String requested = externalRepoId != null ? externalRepoId : fullName;
        try {
            if (externalRepoId != null) {
                return client.getRepository(credentials, new VcsRepository(null, credentials.organizationId(),
                        provider, externalRepoId, fullName));
            }
            return client.resolveRepository(credentials, fullName);
        } catch (DomainException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RepoNotFoundException(requested);
        }
    }

    /**
     * Upsert by identity. A repository pending first sync with the same display name (a migrated
     * binding never synced) is adopted instead of duplicated.
     */
    private VcsRepository save(String orgId, VcsProvider provider, VcsRemoteRepository remote) {
        VcsRepository repository = repositoryRepository
                .findByOrganizationIdAndProviderAndExternalRepoId(orgId, provider, remote.externalRepoId())
                .or(() -> repositoryRepository
                        .findByOrganizationIdAndProviderAndDisplayName(orgId, provider, remote.displayName())
                        .filter(VcsRepository::isPendingFirstSync))
                .orElseGet(() -> new VcsRepository(null, orgId, provider, remote.externalRepoId(), remote.displayName()));
        repository.setExternalRepoId(remote.externalRepoId());
        repository.setDisplayName(remote.displayName());
        repository.setExternalProjectId(remote.externalProjectId());
        repository.setDefaultBranch(remote.defaultBranch());
        repository.setExternalUpdatedAt(remote.updatedAt());
        repository.setActive(true);
        return repositoryRepository.save(repository);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
