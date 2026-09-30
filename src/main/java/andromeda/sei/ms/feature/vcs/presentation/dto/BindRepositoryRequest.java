package andromeda.sei.ms.feature.vcs.presentation.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

/**
 * Bind request. The repository is identified by {@code externalRepoId}, or — GitHub only — by the
 * free-text {@code fullName} ({@code owner/name}). {@code provider} defaults to {@code GITHUB} and
 * {@code syncEnabled} to {@code true}.
 *
 * @param repoId deprecated alias of {@code fullName}: the body of the legacy
 *               {@code POST /api/v1/integrations/github/projects/{id}/bindings}, which answers 308
 *               here and replays its body
 */
public record BindRepositoryRequest(VcsProvider provider, String externalRepoId, String fullName,
                                    @Deprecated String repoId, Boolean syncEnabled) {

    public String effectiveFullName() {
        return fullName != null && !fullName.isBlank() ? fullName : repoId;
    }

    public boolean effectiveSyncEnabled() {
        return syncEnabled == null || syncEnabled;
    }
}
