package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class GithubConnectionNotFoundException extends ResourceNotFoundException {

    public GithubConnectionNotFoundException(String organizationId) {
        super("GITHUB_CONNECTION_NOT_FOUND", "No active GitHub connection for organization: " + organizationId);
    }
}
