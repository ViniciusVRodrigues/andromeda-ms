package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/**
 * A repo binding identifies its repository by GitHub full name in {@code owner/name} form
 * (see SyncGithubReposUseCase for why the full name, and not an internal id, is the binding
 * key). Rejecting a malformed identifier at bind time keeps the failure next to the input
 * that caused it — otherwise the binding is accepted and only breaks later, on the first
 * sync run that tries to fetch the repo.
 */
public class GithubRepoIdInvalidException extends BusinessRuleException {

    public GithubRepoIdInvalidException(String repoId) {
        super("GITHUB_REPO_ID_INVALID",
                "GitHub repo identifier must be in 'owner/name' form: " + repoId);
    }
}
