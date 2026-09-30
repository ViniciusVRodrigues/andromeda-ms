package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

/**
 * One entry of the bindable listing. A repository actively bound to a project carries
 * {@code boundToProjectId} and {@code bindable = false}: exclusivity is per organization, so it can
 * only be re-bound (idempotently) to that same project.
 *
 * @param source {@code LIVE} when the provider listed it now, {@code CACHED} when only known from a sync
 */
public record BindableRepositoryOutput(
        VcsProvider provider,
        String externalRepoId,
        String displayName,
        String externalProjectId,
        String externalProjectName,
        String defaultBranch,
        boolean bindable,
        String boundToProjectId,
        Source source) {

    public enum Source {
        LIVE,
        CACHED
    }
}
