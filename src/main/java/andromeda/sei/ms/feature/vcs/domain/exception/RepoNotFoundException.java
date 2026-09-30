package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

/** Also raised for a repository (or pull request) of another organization: never leak its existence. */
public class RepoNotFoundException extends ResourceNotFoundException {

    public RepoNotFoundException(String id) {
        super("REPO_NOT_FOUND", "Repository not found: " + id);
    }
}
