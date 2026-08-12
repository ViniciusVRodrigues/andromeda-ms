package andromeda.sei.ms.feature.github.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceConflictException;

/**
 * One external resource binds to at most one Andromeda project per organization
 * (design.md D3) — the repo is already actively bound to a DIFFERENT project.
 */
public class GithubRepoAlreadyBoundException extends ResourceConflictException {

    public GithubRepoAlreadyBoundException(String repoId) {
        super("GITHUB_REPO_ALREADY_BOUND", "GitHub repo already bound to another project: " + repoId);
    }
}
