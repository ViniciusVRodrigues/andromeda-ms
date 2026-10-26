package andromeda.sei.ms.shared.infrastructure.security;

import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.shared.application.security.ProjectMembersChanged;
import andromeda.sei.ms.shared.application.security.RoleChanged;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.feature.role.domain.RoleRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.application.security.EffectivePermissionService;
import andromeda.sei.ms.shared.application.security.UserPermissionsChanged;
import andromeda.sei.ms.shared.domain.security.PermissionEffect;
import andromeda.sei.ms.shared.domain.security.PermissionOverride;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * project-scoped-permissions 4.3: the JWT only identifies the caller; authorities are the effective
 * organization codes resolved on the server, so a revocation applies to the next request without a
 * new login. Real {@link EffectivePermissionService}, Caffeine cache and invalidation listener over
 * mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final String ROLE = "role-1";

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private andromeda.sei.ms.feature.organization.domain.OrganizationRepository organizationRepository;

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private PermissionCacheInvalidationListener listener;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef0123456789abcdef".getBytes()));
        ReflectionTestUtils.setField(jwtService, "expirationMs", 60_000L);
        EffectivePermissionService service = new EffectivePermissionService(userRepository, roleRepository,
                projectRepository, organizationRepository, new CaffeinePermissionSnapshotCache(Duration.ofMinutes(5)));
        filter = new JwtAuthenticationFilter(jwtService, service);
        listener = new PermissionCacheInvalidationListener(service);
        lenient().when(projectRepository.findAllActiveByOrganizationIdAndMemberId(any(), any())).thenReturn(List.of());
        lenient().when(organizationRepository.findById(any())).thenAnswer(inv -> java.util.Optional.of(
                new andromeda.sei.ms.feature.organization.domain.Organization(inv.getArgument(0), "Org", "org", null, null,
                        null, null, null, true, 1L, java.time.Instant.now(), java.time.Instant.now())));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_denyRevokedPermission_onNextRequest_withoutNewLogin() throws Exception {
        User user = user();
        givenUser(user);
        givenRole("DASHBOARD_TEAM", "DASHBOARD_INDIVIDUAL");
        String token = jwtService.generate(USER, ORG, List.of("DASHBOARD_TEAM", "DASHBOARD_INDIVIDUAL"));
        assertThat(authoritiesSeenBy(token)).contains("DASHBOARD_TEAM");

        user.setPermissionOverrides(List.of(new PermissionOverride("DASHBOARD_TEAM", PermissionEffect.DENY,
                null, "admin", Instant.now())));
        listener.on(new UserPermissionsChanged(ORG, USER));

        assertThat(authoritiesSeenBy(token)).doesNotContain("DASHBOARD_TEAM").contains("DASHBOARD_INDIVIDUAL");
    }

    @Test
    void should_readNewProject_onNextRequest_whenUserIsAddedAsMember() throws Exception {
        givenUser(user());
        givenRole("DASHBOARD_TEAM");
        String token = jwtService.generate(USER, ORG, List.of());
        Project project = new Project("P", ORG, "P", "p", null, true, new ArrayList<>(List.of(USER)), 1L,
                Instant.now(), Instant.now());
        assertThat(snapshotSeenBy(token).resolver().hasOnProject("DASHBOARD_TEAM", "P")).isFalse();

        when(projectRepository.findAllActiveByOrganizationIdAndMemberId(ORG, USER)).thenReturn(List.of(project));
        listener.on(new ProjectMembersChanged(ORG, "P"));

        assertThat(snapshotSeenBy(token).resolver().hasOnProject("DASHBOARD_TEAM", "P")).isTrue();
    }

    @Test
    void should_applyRoleChange_onNextRequest() throws Exception {
        givenUser(user());
        givenRole("DASHBOARD_INDIVIDUAL");
        String token = jwtService.generate(USER, ORG, List.of());
        assertThat(authoritiesSeenBy(token)).doesNotContain("DASHBOARD_TEAM");

        givenRole("DASHBOARD_INDIVIDUAL", "DASHBOARD_TEAM");
        listener.on(new RoleChanged(ROLE, null));

        assertThat(authoritiesSeenBy(token)).contains("DASHBOARD_TEAM");
    }

    @Test
    void should_ignorePermissionClaimsOfTheToken() throws Exception {
        givenUser(user());
        givenRole("DASHBOARD_INDIVIDUAL");

        List<String> authorities = authoritiesSeenBy(jwtService.generate(USER, ORG, List.of("USER_MANAGE")));

        assertThat(authorities).containsExactly("DASHBOARD_INDIVIDUAL");
    }

    @Test
    void should_grantReposRead_toRoleStillCarryingGithubRead() throws Exception {
        givenUser(user());
        givenRole("GITHUB_READ", "AZURE_READ");

        assertThat(authoritiesSeenBy(jwtService.generate(USER, ORG, List.of())))
                .contains("REPOS_READ", "AZURE_READ").doesNotContain("GITHUB_READ");
    }

    @Test
    void should_leaveRequestUnauthenticated_when_userWasDeactivated() throws Exception {
        when(userRepository.findByIdAndOrganizationId(USER, ORG)).thenReturn(Optional.empty());

        assertThat(authenticationSeenBy(jwtService.generate(USER, ORG, List.of("USER_MANAGE")))).isNull();
    }

    private User user() {
        return new User(USER, ORG, "dev@x.com", null, "Dev", ROLE, null, null, null, null, null,
                true, true, 1L, Instant.now(), Instant.now());
    }

    private void givenUser(User user) {
        when(userRepository.findByIdAndOrganizationId(USER, ORG)).thenReturn(Optional.of(user));
    }

    private void givenRole(String... codes) {
        when(roleRepository.findById(ROLE)).thenReturn(Optional.of(new Role(ROLE, null, "DEVELOPER", "Dev", true,
                null, new ArrayList<>(List.of(codes)), true, 1L, Instant.now(), Instant.now())));
    }

    private UserPermissionSnapshot snapshotSeenBy(String token) throws Exception {
        return ((AndromedaUserDetails) authenticationSeenBy(token).getPrincipal()).getPermissionSnapshot();
    }

    private List<String> authoritiesSeenBy(String token) throws Exception {
        Authentication auth = authenticationSeenBy(token);
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private Authentication authenticationSeenBy(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        Authentication[] seen = new Authentication[1];
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                seen[0] = SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        return seen[0];
    }
}
