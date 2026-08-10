package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class GithubRepoNotFoundException extends ResourceNotFoundException {

    public GithubRepoNotFoundException(String externalId) {
        super("GITHUB_REPO_NOT_FOUND", "GitHub repo not found: " + externalId);
    }
}
