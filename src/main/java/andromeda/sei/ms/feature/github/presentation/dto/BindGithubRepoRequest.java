package andromeda.sei.ms.feature.github.presentation.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code repoId} is the GitHub {@code "owner/name"} full name (see SyncGithubReposUseCase javadoc). */
public record BindGithubRepoRequest(@NotBlank String repoId, boolean syncEnabled) {}
