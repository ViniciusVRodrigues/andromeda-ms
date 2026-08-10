package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import andromeda.sei.ms.feature.github.infrastructure.client.GhRepoDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Fetches/upserts the single repo a {@link GithubRepoBinding} points at.
 *
 * <p>Design note (open question in design.md, resolved here): {@code GithubRepoBinding.repoId}
 * is the GitHub {@code "owner/name"} full name -- the value an admin supplies when binding a
 * repo -- rather than an internal id resolved from a prior org-wide repo listing. GitHub PATs
 * are not scoped to a single org the way an Azure DevOps connection is, so there is no
 * equivalent of {@code SyncAzureOrgLevelDataUseCase}'s "list every project up front" step;
 * binding a specific repo directly by full name is both simpler and avoids requiring
 * organization-admin scope just to enumerate repos the token can already access individually.
 * {@code GithubRepo.externalId} (the numeric GitHub id) is populated once the repo is first
 * fetched and used as the stable key for commits/PRs/reviews once resolved.
 */
@Component
@RequiredArgsConstructor
public class SyncGithubReposUseCase {

    private final GithubRepoRepository githubRepoRepository;
    private final GithubApiClient githubApiClient;
    private final EncryptionService encryptionService;

    public GithubRepo execute(GithubConnection connection, GithubRepoBinding binding) {
        String[] ownerAndName = splitOwnerAndName(binding.getRepoId());
        String owner = ownerAndName[0];
        String name = ownerAndName[1];

        String authHeader = "Bearer " + encryptionService.decrypt(connection.getPersonalAccessToken());
        String orgId = connection.getOrganizationId();

        GhRepoDto fetched = githubApiClient.getRepo(owner, name, authHeader);

        String externalId = String.valueOf(fetched.id());
        var existing = githubRepoRepository.findByOrganizationIdAndExternalId(orgId, externalId);
        Instant now = Instant.now();

        GithubRepo repo = existing.orElseGet(() -> new GithubRepo(
                null, orgId, externalId, owner, name, fetched.fullName(), null, true, now, now, null));

        repo.setOwner(fetched.owner() != null ? fetched.owner().login() : owner);
        repo.setName(fetched.name());
        repo.setFullName(fetched.fullName());
        repo.setExternalUpdatedAt(parseInstant(fetched.updatedAt()));

        return githubRepoRepository.save(repo);
    }

    private String[] splitOwnerAndName(String ownerSlashName) {
        int slash = ownerSlashName.indexOf('/');
        if (slash <= 0 || slash == ownerSlashName.length() - 1) {
            throw new IllegalArgumentException("GithubRepoBinding.repoId must be in 'owner/name' form: " + ownerSlashName);
        }
        return new String[] { ownerSlashName.substring(0, slash), ownerSlashName.substring(slash + 1) };
    }

    private Instant parseInstant(String iso) {
        if (iso == null) return null;
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
