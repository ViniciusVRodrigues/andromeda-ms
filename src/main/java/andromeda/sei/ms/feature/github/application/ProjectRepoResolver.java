package andromeda.sei.ms.feature.github.application;

import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves a project's bound repositories into the repository rows that pull requests and
 * commits reference.
 *
 * <p>Bindings carry the GitHub {@code owner/name} full name — the value an admin supplies,
 * chosen deliberately because a GitHub PAT is not scoped to one organization and so there is
 * no org-wide listing step that could hand back an internal id at bind time. Pull requests and
 * commits, in turn, are keyed by the {@code github_repos} id assigned once the repo is first
 * fetched. Reading a project's repository data therefore goes:
 *
 * <pre>
 *   binding.repoId ("owner/name") -> github_repos.fullName -> github_repos.id
 * </pre>
 *
 * <p>The lookup is batched: one query resolves every binding of the project, backed by the
 * existing {@code {organizationId, fullName, active}} index. A binding whose repository has
 * never been fetched simply drops out — it has no pull requests or commits to show anyway.
 */
@Component
@RequiredArgsConstructor
public class ProjectRepoResolver {

    private final GithubRepoBindingRepository githubRepoBindingRepository;
    private final GithubRepoRepository githubRepoRepository;

    public List<GithubRepo> resolve(String organizationId, String projectId) {
        List<String> fullNames = githubRepoBindingRepository
                .findAllByOrganizationIdAndProjectIdAndActiveTrue(organizationId, projectId)
                .stream()
                .map(GithubRepoBinding::getRepoId)
                .distinct()
                .toList();

        if (fullNames.isEmpty()) return List.of();

        return githubRepoRepository
                .findAllByOrganizationIdAndFullNameInAndActiveTrue(organizationId, fullNames);
    }

    /**
     * @return the repository of this organization carrying {@code repoId}, or empty when it
     *         belongs to another organization — the tenant check every read must make.
     */
    public java.util.Optional<GithubRepo> resolveOne(String organizationId, String repoId) {
        return githubRepoRepository.findByIdAndOrganizationId(repoId, organizationId);
    }
}
