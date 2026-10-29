package andromeda.sei.ms.feature.agent.infrastructure.attribution;

import andromeda.sei.ms.feature.agent.application.attribution.RepositoryProjectPort;
import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Looks a remote up in the neutral vcs slice. GitHub matches displayName (owner/name) ignoring
 * case; Azure Repos matches the repository name ignoring case and only when exactly one
 * repository of the organization carries it.
 */
@Component
@RequiredArgsConstructor
public class VcsRepositoryProjectAdapter implements RepositoryProjectPort {

    private final VcsRepositoryRepository repositories;
    private final VcsRepoBindingRepository bindings;

    @Override
    public Optional<String> findProjectId(String organizationId, RemoteRepositoryRef remote) {
        boolean github = remote.kind() == RemoteRepositoryRef.Kind.GITHUB;
        VcsProvider provider = github ? VcsProvider.GITHUB : VcsProvider.AZURE_REPOS;
        List<VcsRepository> matches = repositories.findAllByOrganizationIdAndProvider(organizationId, provider).stream()
                .filter(VcsRepository::isActive)
                .filter(r -> r.getDisplayName() != null && r.getDisplayName().equalsIgnoreCase(remote.name()))
                .toList();
        if (matches.isEmpty() || (!github && matches.size() > 1)) return Optional.empty();

        Set<String> repositoryIds = matches.stream().map(VcsRepository::getId).collect(Collectors.toSet());
        Set<String> projectIds = bindings.findAllActiveByOrganizationId(organizationId).stream()
                .filter(VcsRepoBinding::isActive)
                .filter(b -> repositoryIds.contains(b.getRepositoryId()))
                .map(VcsRepoBinding::getProjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return projectIds.size() == 1 ? Optional.of(projectIds.iterator().next()) : Optional.empty();
    }
}
