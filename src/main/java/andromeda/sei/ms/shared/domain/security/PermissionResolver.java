package andromeda.sei.ms.shared.domain.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Effective permission decision for one user (project-scoped-permissions D3/D4). Pure and
 * stateless after construction.
 *
 * <p>Precedence for code C (and project P): override on P, then override on the organization,
 * then the role preset — the most specific level that mentions C decides, and within one level
 * {@code DENY} wins over {@code GRANT}. For a {@link PermissionScope#PROJECT_SCOPABLE} code, an
 * organization or role grant only applies on P when the user is a member of P or effectively holds
 * {@code PROJECT_ACCESS_ALL}; a project-level {@code GRANT} applies regardless of membership.
 *
 * <p>{@link PermissionScope#ORG_ONLY} codes ignore project overrides (writes reject them anyway);
 * "holding" one on a project means holding it at organization level and the project being
 * accessible (member, {@code PROJECT_ACCESS_ALL} or any project-level grant).
 *
 * <p>Role codes are canonicalized through {@link PermissionAliasResolver}, so a role not yet
 * migrated from {@code GITHUB_*} keeps its access as {@code REPOS_*}.
 */
public final class PermissionResolver {

    private final Set<String> roleCodes;
    private final Map<String, Set<PermissionEffect>> orgOverrides = new HashMap<>();
    private final Map<String, Map<String, Set<PermissionEffect>>> projectOverrides = new HashMap<>();
    private final Set<String> memberProjectIds;

    public PermissionResolver(Collection<String> roleCodes, Collection<PermissionOverride> overrides,
                              Collection<String> memberProjectIds) {
        this.roleCodes = Set.copyOf(PermissionAliasResolver.canonicalize(roleCodes));
        this.memberProjectIds = memberProjectIds == null ? Set.of() : Set.copyOf(memberProjectIds);
        if (overrides != null) overrides.forEach(this::index);
    }

    private void index(PermissionOverride override) {
        Map<String, Set<PermissionEffect>> level = override.isOrganizationLevel()
                ? orgOverrides
                : projectOverrides.computeIfAbsent(override.projectId(), p -> new HashMap<>());
        level.computeIfAbsent(override.code(), c -> EnumSet.noneOf(PermissionEffect.class)).add(override.effect());
    }

    /** Organization-level decision: organization override, then role preset. */
    public boolean hasOrg(String code) {
        Boolean decided = decide(orgOverrides.get(code));
        return decided != null ? decided : roleCodes.contains(code);
    }

    public boolean hasProjectAccessAll() {
        return hasOrg(PermissionCatalog.PROJECT_ACCESS_ALL);
    }

    public boolean isMember(String projectId) {
        return projectId != null && memberProjectIds.contains(projectId);
    }

    /** Whether some code is effectively granted on the project by a project-level override. */
    public boolean hasProjectGrant(String projectId) {
        Map<String, Set<PermissionEffect>> level = projectOverrides.get(projectId);
        if (level == null) return false;
        return level.entrySet().stream()
                .anyMatch(e -> PermissionCatalog.isProjectScopable(e.getKey()) && Boolean.TRUE.equals(decide(e.getValue())));
    }

    /** Member, {@code PROJECT_ACCESS_ALL} or a project-level grant: the project list criterion. */
    public boolean isProjectAccessible(String projectId) {
        return projectId != null && (hasProjectAccessAll() || isMember(projectId) || hasProjectGrant(projectId));
    }

    public boolean hasOnProject(String code, String projectId) {
        if (projectId == null) return false;
        if (!PermissionCatalog.isProjectScopable(code)) {
            return hasOrg(code) && isProjectAccessible(projectId);
        }
        Boolean decided = decide(projectOverrides.getOrDefault(projectId, Map.of()).get(code));
        if (decided != null) return decided;
        return hasOrg(code) && (isMember(projectId) || hasProjectAccessAll());
    }

    /** Effective organization-level codes, sorted. */
    public Set<String> orgCodes() {
        Set<String> candidates = new HashSet<>(roleCodes);
        candidates.addAll(orgOverrides.keySet());
        Set<String> result = new TreeSet<>();
        candidates.stream().filter(this::hasOrg).forEach(result::add);
        return result;
    }

    /** Effective project-scopable codes on the project, sorted. */
    public Set<String> codesOnProject(String projectId) {
        Set<String> result = new TreeSet<>();
        PermissionCatalog.scopes().keySet().stream()
                .filter(PermissionCatalog::isProjectScopable)
                .filter(code -> hasOnProject(code, projectId))
                .forEach(result::add);
        return result;
    }

    /** Projects where some code is effectively granted by a project-level override. */
    public Set<String> projectGrantIds() {
        Set<String> result = new TreeSet<>();
        projectOverrides.keySet().stream().filter(this::hasProjectGrant).forEach(result::add);
        return result;
    }

    public Set<String> memberProjectIds() {
        return memberProjectIds;
    }

    /** {@code null} when the level does not mention the code; DENY wins within the level. */
    private static Boolean decide(Set<PermissionEffect> effects) {
        if (effects == null || effects.isEmpty()) return null;
        return !effects.contains(PermissionEffect.DENY);
    }
}
