package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class RepoBindingNotFoundException extends ResourceNotFoundException {

    public RepoBindingNotFoundException(String bindingId) {
        super("REPO_BINDING_NOT_FOUND", "Repository binding not found: " + bindingId);
    }
}
