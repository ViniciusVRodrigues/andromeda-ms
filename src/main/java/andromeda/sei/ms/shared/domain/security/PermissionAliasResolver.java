package andromeda.sei.ms.shared.domain.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renamed permission codes (repository-permissions, vcs-core-refactor D8): {@code GITHUB_READ} is
 * now {@code REPOS_READ} and {@code GITHUB_CONFIGURE} is {@code REPOS_CONFIGURE}.
 *
 * <p><b>Rollback-safe rename.</b> The code before vcs-core-refactor only knows {@code GITHUB_*}; if
 * the deploy is rolled back, roles holding only {@code REPOS_*} would lose repository access. So,
 * until a later cleanup change, the stored role codes keep the legacy code as a <i>mirror</i> of
 * its successor:
 * <ul>
 *   <li>everything <b>written</b> to a role goes through {@link #withLegacyMirrors} (current codes plus
 *       the legacy mirror of each renamed one — adding {@code REPOS_READ} stores {@code GITHUB_READ}
 *       too, removing it removes both);</li>
 *   <li>everything <b>read</b> from a role goes through {@link #canonicalize}, so the application,
 *       the API and the JWT only ever see {@code REPOS_*}.</li>
 * </ul>
 * The single choke point for both is {@code RoleDocumentMapper}; {@code RoleSeeder} (writes
 * documents directly) and {@code ReposPermissionsMigration} (MongoTemplate) apply the same rule.
 *
 * <p>Remove this class, the mirrors ({@code $pull} the legacy codes) and its call sites in a later
 * cleanup change, once rolling back past vcs-core-refactor is no longer an option.
 */
public final class PermissionAliasResolver {

    public static final Map<String, String> LEGACY_TO_CURRENT = Map.of(
            "GITHUB_READ", "REPOS_READ",
            "GITHUB_CONFIGURE", "REPOS_CONFIGURE");

    public static final Map<String, String> CURRENT_TO_LEGACY = Map.of(
            "REPOS_READ", "GITHUB_READ",
            "REPOS_CONFIGURE", "GITHUB_CONFIGURE");

    private PermissionAliasResolver() {}

    /** The codes plus the current equivalent of every legacy one, without duplicates, order kept. */
    public static List<String> expand(Collection<String> codes) {
        if (codes == null) return List.of();
        Set<String> expanded = new LinkedHashSet<>();
        for (String code : codes) {
            expanded.add(code);
            String current = LEGACY_TO_CURRENT.get(code);
            if (current != null) expanded.add(current);
        }
        return List.copyOf(expanded);
    }

    /**
     * The codes with every legacy one replaced by its current equivalent, without duplicates: the
     * read view of role codes (hides the legacy mirrors).
     */
    public static List<String> canonicalize(Collection<String> codes) {
        if (codes == null) return List.of();
        Set<String> canonical = new LinkedHashSet<>();
        for (String code : codes) {
            canonical.add(LEGACY_TO_CURRENT.getOrDefault(code, code));
        }
        return List.copyOf(canonical);
    }

    /**
     * The storage form of role codes: {@link #canonicalize canonical} codes, each renamed one followed
     * by its legacy mirror. A legacy code without its successor is normalized to the pair; a legacy
     * mirror whose successor is gone cannot survive, since the input is canonicalized first.
     */
    public static List<String> withLegacyMirrors(Collection<String> codes) {
        Set<String> stored = new LinkedHashSet<>();
        for (String code : canonicalize(codes)) {
            stored.add(code);
            String legacy = CURRENT_TO_LEGACY.get(code);
            if (legacy != null) stored.add(legacy);
        }
        return List.copyOf(stored);
    }
}
