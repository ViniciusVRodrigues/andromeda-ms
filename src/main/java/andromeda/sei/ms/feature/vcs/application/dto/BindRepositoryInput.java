package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

import java.util.List;

/**
 * Bind request: the repository is identified by {@code externalRepoId} or, for GitHub, by the
 * free-text {@code fullName} ({@code owner/name}) resolved to its id before anything is stored.
 * {@code branches}, {@code productionBranch} and {@code filterPullRequestsByBranch} are optional
 * (branches-por-vinculo); without {@code branches} the binding follows the default branch.
 */
public record BindRepositoryInput(VcsProvider provider, String externalRepoId, String fullName, boolean syncEnabled,
                                  List<String> branches, String productionBranch,
                                  Boolean filterPullRequestsByBranch) {

    public BindRepositoryInput(VcsProvider provider, String externalRepoId, String fullName, boolean syncEnabled) {
        this(provider, externalRepoId, fullName, syncEnabled, null, null, null);
    }
}
