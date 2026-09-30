package andromeda.sei.ms.shared.application.security;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.infrastructure.security.CaffeinePermissionSnapshotCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 4.1/7.4 — snapshot loading, cache, and the listings for a member, a
 * non-member and a {@code PROJECT_ACCESS_ALL} holder.
 */
@ExtendWith(MockitoExtension.class)
class EffectivePermissionServiceTest {

    private static final String ORG = "org-1";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ProjectRepository projectRepository;

    private EffectivePermissionService service;

    private final Project a = project("A", "dev", "other");
    private final Project b = project("B", "other");

    @BeforeEach
    void setUp() {
        service = new EffectivePermissionService(userRepository, roleRepository, projectRepository,
                new CaffeinePermissionSnapshotCache(Duration.ofMinutes(5)));
        lenient().when(projectRepository.findAllActiveByOrganizationId(ORG)).thenReturn(List.of(a, b));
    }

    @Test
    void should_loadRoleOverridesAndMemberships_once_thenServeFromCache() {
        givenUser("dev", "role-1", List.of());
        givenRole("role-1", null, "DEVELOPER", "DASHBOARD_TEAM");
        when(projectRepository.findAllActiveByOrganizationIdAndMemberId(ORG, "dev")).thenReturn(List.of(a));

        UserPermissionSnapshot first = service.snapshot(ORG, "dev").orElseThrow();
        service.snapshot(ORG, "dev");

        assertThat(first.roleName()).isEqualTo("DEVELOPER");
        assertThat(first.resolver().hasOnProject("DASHBOARD_TEAM", "A")).isTrue();
        verify(userRepository, times(1)).findByIdAndOrganizationId("dev", ORG);
    }

    @Test
    void should_ignoreRoleOfAnotherOrganization() {
        givenUser("dev", "role-x", List.of());
        givenRole("role-x", "org-2", "FOREIGN", "USER_MANAGE");

        assertThat(service.snapshot(ORG, "dev").orElseThrow().resolver().hasOrg("USER_MANAGE")).isFalse();
    }

    @Test
    void should_returnEmpty_when_userIsInactiveOrMissing() {
        User inactive = user("gone", "role-1", List.of());
        inactive.setActive(false);
        when(userRepository.findByIdAndOrganizationId("gone", ORG)).thenReturn(Optional.of(inactive));

        assertThat(service.snapshot(ORG, "gone")).isEmpty();
    }

    @Test
    void should_listOnlyMemberProjects_forMember() {
        UserPermissionSnapshot dev = snapshot("dev", List.of("DASHBOARD_TEAM"), List.of(), Set.of("A"));

        assertThat(service.accessibleProjects(dev)).containsExactly(a);
        assertThat(service.projectIdsWith(dev, "DASHBOARD_TEAM")).containsExactly("A");
        assertThat(service.projectPermissions(dev)).containsOnlyKeys("A");
        assertThat(service.projectPermissions(dev).get("A")).containsExactly("DASHBOARD_TEAM");
    }

    @Test
    void should_listNothing_forNonMemberWithoutGrants() {
        UserPermissionSnapshot stranger = snapshot("x", List.of("DASHBOARD_TEAM"), List.of(), Set.of());

        assertThat(service.accessibleProjects(stranger)).isEmpty();
        assertThat(service.projectIdsWith(stranger, "DASHBOARD_TEAM")).isEmpty();
    }

    @Test
    void should_listProjectGrantedToNonMember() {
        UserPermissionSnapshot granted = snapshot("x", List.of(), List.of(
                new PermissionOverride("DASHBOARD_STRATEGIC", PermissionEffect.GRANT, "B", "admin", T)), Set.of());

        assertThat(service.accessibleProjects(granted)).containsExactly(b);
        assertThat(service.projectIdsWith(granted, "DASHBOARD_STRATEGIC")).containsExactly("B");
    }

    @Test
    void should_listEveryProject_forAccessAllHolder() {
        UserPermissionSnapshot manager = snapshot("m", List.of("DASHBOARD_STRATEGIC", "PROJECT_ACCESS_ALL"),
                List.of(), Set.of());

        assertThat(service.accessibleProjects(manager)).containsExactly(a, b);
        assertThat(service.projectIdsWith(manager, "DASHBOARD_STRATEGIC")).containsExactly("A", "B");
    }

    @Test
    void should_limitReadableUsers_toMembersOfProjectsWhereCallerHoldsAiReadAll() {
        UserPermissionSnapshot lead = snapshot("lead", List.of(), List.of(
                new PermissionOverride("AI_READ_ALL", PermissionEffect.GRANT, "A", "admin", T)), Set.of());

        assertThat(service.visibleUserIds(lead, List.of("AI_READ_ALL"))).contains(Set.of("lead", "dev", "other"));
        assertThat(service.canReadUserData(lead, "dev", List.of("AI_READ_ALL"))).isTrue();
        assertThat(service.canReadUserData(lead, "outsider", List.of("AI_READ_ALL"))).isFalse();
        assertThat(service.canReadUserData(lead, "lead", List.of("AI_READ_ALL"))).isTrue();
    }

    @Test
    void should_readEveryUser_whenCodeHeldOrganizationWide() {
        UserPermissionSnapshot admin = snapshot("admin", List.of("AI_READ_ALL", "PROJECT_ACCESS_ALL"), List.of(), Set.of());

        assertThat(service.visibleUserIds(admin, List.of("AI_READ_ALL"))).isEmpty();
        assertThat(service.canReadUserData(admin, "outsider", List.of("AI_READ_ALL"))).isTrue();
    }

    @Test
    void should_reloadFromRepository_afterEviction() {
        givenUser("dev", "role-1", new ArrayList<>());
        givenRole("role-1", null, "DEVELOPER", "DASHBOARD_TEAM");

        service.snapshot(ORG, "dev");
        service.evictUser(ORG, "dev");
        service.snapshot(ORG, "dev");

        verify(userRepository, times(2)).findByIdAndOrganizationId("dev", ORG);
    }

    private UserPermissionSnapshot snapshot(String userId, List<String> roleCodes, List<PermissionOverride> overrides,
                                            Set<String> members) {
        return new UserPermissionSnapshot(ORG, userId, "r", "ROLE", roleCodes, overrides, members, 1L);
    }

    private void givenUser(String id, String roleId, List<PermissionOverride> overrides) {
        when(userRepository.findByIdAndOrganizationId(id, ORG)).thenReturn(Optional.of(user(id, roleId, overrides)));
    }

    private static User user(String id, String roleId, List<PermissionOverride> overrides) {
        User user = new User(id, ORG, id + "@x.com", null, id, roleId, null, null, null, null, null,
                true, true, 1L, T, T);
        user.setPermissionOverrides(overrides);
        return user;
    }

    private void givenRole(String id, String orgId, String name, String... codes) {
        when(roleRepository.findById(id)).thenReturn(Optional.of(new Role(id, orgId, name, name, orgId == null, null,
                new ArrayList<>(List.of(codes)), true, 1L, T, T)));
    }

    private static Project project(String id, String... members) {
        return new Project(id, ORG, "Project " + id, id.toLowerCase(), null, true,
                new ArrayList<>(List.of(members)), 1L, T, T);
    }
}
