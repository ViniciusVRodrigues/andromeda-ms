package andromeda.sei.ms.feature.vcs.application.dto;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;

/**
 * Bind request: the repository is identified by {@code externalRepoId} or, for GitHub, by the
 * free-text {@code fullName} ({@code owner/name}) resolved to its id before anything is stored.
 */
public record BindRepositoryInput(VcsProvider provider, String externalRepoId, String fullName, boolean syncEnabled) {}
