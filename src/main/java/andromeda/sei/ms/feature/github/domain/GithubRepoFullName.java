package andromeda.sei.ms.feature.github.domain;

/**
 * The {@code owner/name} identifier a repo binding carries.
 *
 * <p>A GitHub PAT is not scoped to a single organization the way an Azure DevOps connection
 * is, so there is no org-wide listing step that could resolve an internal id before binding;
 * the admin supplies the full name directly and the sync fetches the repo from it. See
 * {@code SyncGithubReposUseCase} for the full rationale.
 *
 * <p>The parsing rule lives here so bind-time validation and sync-time splitting cannot drift
 * apart: both ask this type what a well-formed identifier is.
 */
public final class GithubRepoFullName {

    private final String owner;
    private final String name;

    private GithubRepoFullName(String owner, String name) {
        this.owner = owner;
        this.name = name;
    }

    /** @return the parsed identifier, or {@code null} when {@code value} is not {@code owner/name}. */
    public static GithubRepoFullName parseOrNull(String value) {
        if (value == null) return null;

        int slash = value.indexOf('/');
        // Reject a leading slash (empty owner), a trailing slash (empty name), and any value
        // carrying more than one separator -- "a/b/c" names no repository.
        if (slash <= 0 || slash == value.length() - 1) return null;
        if (value.indexOf('/', slash + 1) >= 0) return null;

        return new GithubRepoFullName(value.substring(0, slash), value.substring(slash + 1));
    }

    public static boolean isValid(String value) {
        return parseOrNull(value) != null;
    }

    public String owner() { return owner; }

    public String name() { return name; }

    public String value() { return owner + "/" + name; }
}
