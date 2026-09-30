package andromeda.sei.ms.feature.vcs.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/** The repository is actively bound to another project of the organization (409). */
public class RepoAlreadyBoundException extends ResourceConflictException {

    public RepoAlreadyBoundException(String repository) {
        super("REPO_ALREADY_BOUND", "Repository is already bound to another project: " + repository);
    }
}
