package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class GithubRepoBindingNotFoundException extends ResourceNotFoundException {

    public GithubRepoBindingNotFoundException(String id) {
        super("GITHUB_REPO_BINDING_NOT_FOUND", "GitHub repo binding not found: " + id);
    }
}
