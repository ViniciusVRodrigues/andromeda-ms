package andromeda.sei.ms.feature.vcs.presentation.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

import java.util.List;

/**
 * Bind request. The repository is identified by {@code externalRepoId}, or — GitHub only — by the
 * free-text {@code fullName} ({@code owner/name}). {@code provider} defaults to {@code GITHUB} and
 * {@code syncEnabled} to {@code true}. {@code branches} (1 to 10), {@code productionBranch} and
 * {@code filterPullRequestsByBranch} are optional; an absent list keeps the default branch.
 *
 * @param repoId deprecated alias of {@code fullName}: the body of the legacy
 *               {@code POST /api/v1/integrations/github/projects/{id}/bindings}, which answers 308
 *               here and replays its body
 */
public record BindRepositoryRequest(VcsProvider provider, String externalRepoId, String fullName,
                                    @Deprecated String repoId, Boolean syncEnabled, List<String> branches,
                                    String productionBranch, Boolean filterPullRequestsByBranch) {

    public BindRepositoryRequest(VcsProvider provider, String externalRepoId, String fullName,
                                 String repoId, Boolean syncEnabled) {
        this(provider, externalRepoId, fullName, repoId, syncEnabled, null, null, null);
    }

    public String effectiveFullName() {
        return fullName != null && !fullName.isBlank() ? fullName : repoId;
    }

    public boolean effectiveSyncEnabled() {
        return syncEnabled == null || syncEnabled;
    }
}
