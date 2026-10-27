package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.shared.domain.exception.DomainException;

/** Resolves a repository on the provider by its external id or, for GitHub, by {@code owner/name}. */
final class RemoteRepositoryLookup {

    private RemoteRepositoryLookup() {}

    static VcsRemoteRepository fetch(VcsProviderClient client, VcsCredentials credentials, VcsProvider provider,
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

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
