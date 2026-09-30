package andromeda.sei.ms.shared.application.security;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.port.PermissionSnapshotCache;
import andromeda.sei.ms.shared.domain.security.PermissionResolver;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side effective permissions (project-scoped-permissions D5/D6). The JWT only identifies the
 * caller; the role, the overrides and the memberships are read here, per request, through a cache
 * invalidated by {@link UserPermissionsChanged}, {@link RoleChanged} and
 * {@link ProjectMembersChanged}.
 *
 * <p>Listings ({@link #accessibleProjects}, {@link #projectsWith}) always read the organization's
 * active projects, so a newly created project is visible at once to {@code PROJECT_ACCESS_ALL}
 * holders; only the per-user part (role, overrides, memberships) is cached.
 */
@Service
@RequiredArgsConstructor
public class EffectivePermissionService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ProjectRepository projectRepository;
    private final PermissionSnapshotCache cache;

    /** Empty when the user does not exist, is inactive or belongs to another organization. */
    public Optional<UserPermissionSnapshot> snapshot(String organizationId, String userId) {
        if (organizationId == null || userId == null) return Optional.empty();
        return cache.get(organizationId, userId, () -> load(organizationId, userId));
    }

    /**
     * Fresh snapshot, bypassing (and refreshing) the cache: for screens that show the user's
     * {@code version} for a later optimistic write — any save bumps it, not only permission ones.
     */
    public Optional<UserPermissionSnapshot> reload(String organizationId, String userId) {
        cache.evict(organizationId, userId);
        return snapshot(organizationId, userId);
    }

    private Optional<UserPermissionSnapshot> load(String organizationId, String userId) {
        return userRepository.findByIdAndOrganizationId(userId, organizationId)
                .filter(User::isActive)
                .map(user -> toSnapshot(organizationId, user));
    }

    private UserPermissionSnapshot toSnapshot(String organizationId, User user) {
        Optional<Role> role = Optional.ofNullable(user.getRoleId())
                .flatMap(roleRepository::findById)
                .filter(r -> r.getOrganizationId() == null || r.getOrganizationId().equals(organizationId));
        Set<String> memberProjectIds = projectRepository
                .findAllActiveByOrganizationIdAndMemberId(organizationId, user.getId()).stream()
                .map(Project::getId)
                .collect(Collectors.toSet());
        return new UserPermissionSnapshot(organizationId, user.getId(), role.map(Role::getId).orElse(null),
                role.map(Role::getName).orElse(null), role.map(Role::getPermissionCodes).orElse(List.of()),
                user.getPermissionOverrides(), memberProjectIds, user.getVersion());
    }

    /** Active projects of the organization the user can open: member, ACCESS_ALL or a project grant. */
    public List<Project> accessibleProjects(UserPermissionSnapshot snapshot) {
        PermissionResolver resolver = snapshot.resolver();
        return activeProjects(snapshot).stream()
                .filter(p -> resolver.isProjectAccessible(p.getId()))
                .toList();
    }

    /** Active projects where the user effectively holds {@code code}. */
    public List<Project> projectsWith(UserPermissionSnapshot snapshot, String code) {
        PermissionResolver resolver = snapshot.resolver();
        return activeProjects(snapshot).stream()
                .filter(p -> resolver.hasOnProject(code, p.getId()))
                .toList();
    }

    public Set<String> projectIdsWith(UserPermissionSnapshot snapshot, String code) {
        return projectsWith(snapshot, code).stream().map(Project::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Accessible project id to its effective project-scopable codes (sorted). */
    public Map<String, List<String>> projectPermissions(UserPermissionSnapshot snapshot) {
        PermissionResolver resolver = snapshot.resolver();
        Map<String, List<String>> result = new LinkedHashMap<>();
        accessibleProjects(snapshot).forEach(p -> result.put(p.getId(), List.copyOf(resolver.codesOnProject(p.getId()))));
        return result;
    }

    public List<String> orgPermissions(UserPermissionSnapshot snapshot) {
        return List.copyOf(snapshot.resolver().orgCodes());
    }

    /**
     * Whether the caller may read another user's personal data (AI usage, allocations) through one
     * of {@code codes}: always for themselves; organization-wide when a code is held at organization
     * level with {@code PROJECT_ACCESS_ALL}; otherwise only when the target is a member of a project
     * where the caller holds the code.
     */
    public boolean canReadUserData(UserPermissionSnapshot caller, String targetUserId, Collection<String> codes) {
        if (targetUserId == null || targetUserId.equals(caller.userId())) return true;
        Optional<Set<String>> visible = visibleUserIds(caller, codes);
        return visible.isEmpty() || visible.get().contains(targetUserId);
    }

    /**
     * Users whose personal data the caller may read through {@code codes}: {@code Optional.empty()}
     * means every user of the organization; otherwise the members of the projects where the caller
     * holds one of the codes, plus the caller.
     */
    public Optional<Set<String>> visibleUserIds(UserPermissionSnapshot caller, Collection<String> codes) {
        PermissionResolver resolver = caller.resolver();
        if (resolver.hasProjectAccessAll() && codes.stream().anyMatch(resolver::hasOrg)) return Optional.empty();
        Set<String> result = new LinkedHashSet<>();
        result.add(caller.userId());
        List<Project> projects = activeProjects(caller);
        for (Project project : projects) {
            if (codes.stream().anyMatch(code -> resolver.hasOnProject(code, project.getId()))) {
                result.addAll(project.getMemberIds());
            }
        }
        return Optional.of(result);
    }

    public void evictUser(String organizationId, String userId) {
        cache.evict(organizationId, userId);
    }

    public void evictAll() {
        cache.evictAll();
    }

    private List<Project> activeProjects(UserPermissionSnapshot snapshot) {
        return projectRepository.findAllActiveByOrganizationId(snapshot.organizationId());
    }
}
