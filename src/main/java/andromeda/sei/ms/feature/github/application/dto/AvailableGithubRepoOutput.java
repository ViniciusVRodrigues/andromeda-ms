package andromeda.sei.ms.feature.github.application.dto;

/**
 * One entry of the available-repos listing (design.md D5). {@code repoId} is the
 * {@code "owner/name"} fullName the bind endpoint accepts. {@code boundToProjectId} is populated
 * only for the requesting project's own binding (kept for preselection); repos bound to other
 * projects are excluded upstream, and unbound repos carry {@code null}.
 */
public record AvailableGithubRepoOutput(
        String repoId,
        String name,
        String fullName,
        Source source,
        String boundToProjectId) {

    public enum Source {
        LIVE,
        CACHED
    }
}
