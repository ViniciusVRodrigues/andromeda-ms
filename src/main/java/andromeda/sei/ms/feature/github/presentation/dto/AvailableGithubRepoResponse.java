package andromeda.sei.ms.feature.github.presentation.dto;

/**
 * One entry of {@code GET /api/v1/integrations/github/repos/available} (design.md D5).
 * {@code source} serializes as {@code "LIVE"} or {@code "CACHED"}.
 */
public record AvailableGithubRepoResponse(
        String repoId,
        String name,
        String fullName,
        String source,
        String boundToProjectId
) {}
