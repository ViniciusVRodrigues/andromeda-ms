package andromeda.sei.ms.shared.domain.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static andromeda.sei.ms.shared.domain.security.PermissionEffect.DENY;
import static andromeda.sei.ms.shared.domain.security.PermissionEffect.GRANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * project-scoped-permissions 3.3 — the resolution matrix of the spec: role / organization /
 * project level × GRANT / DENY × member / non-member × PROJECT_ACCESS_ALL.
 */
class PermissionResolverTest {

    private static final String P = "project-p";
    private static final String OTHER = "project-q";
    private static final String CODE = "DASHBOARD_TEAM";
    private static final String ACCESS_ALL = PermissionCatalog.PROJECT_ACCESS_ALL;

    /** Level of an override in a scenario: absent, GRANT, DENY, or both (DENY must win). */
    enum Level { NONE, GRANT, DENY, BOTH }

    static Stream<Arguments> matrix() {
        List<Arguments> rows = new ArrayList<>();
        for (boolean role : new boolean[]{false, true})
            for (Level org : Level.values())
                for (Level project : Level.values())
                    for (boolean member : new boolean[]{false, true})
                        for (boolean accessAll : new boolean[]{false, true})
                            rows.add(Arguments.of(role, org, project, member, accessAll));
        return rows.stream();
    }

    @ParameterizedTest(name = "role={0} org={1} project={2} member={3} accessAll={4}")
    @MethodSource("matrix")
    void should_followPrecedence_projectThenOrgThenRole(boolean role, Level org, Level project,
                                                        boolean member, boolean accessAll) {
        List<String> roleCodes = new ArrayList<>();
        if (role) roleCodes.add(CODE);
        if (accessAll) roleCodes.add(ACCESS_ALL);
        List<PermissionOverride> overrides = new ArrayList<>();
        add(overrides, org, null);
        add(overrides, project, P);

        PermissionResolver resolver = new PermissionResolver(roleCodes, overrides, member ? Set.of(P) : Set.of());

        boolean expectedOrg = decide(org) != null ? decide(org) : role;
        boolean expectedOnP = decide(project) != null ? decide(project) : expectedOrg && (member || accessAll);
        assertThat(resolver.hasOrg(CODE)).isEqualTo(expectedOrg);
        assertThat(resolver.hasOnProject(CODE, P)).isEqualTo(expectedOnP);
        // A project-level override on P never leaks to another project.
        assertThat(resolver.hasOnProject(CODE, OTHER)).isEqualTo(expectedOrg && accessAll);
    }

    private static Boolean decide(Level level) {
        return switch (level) {
            case NONE -> null;
            case GRANT -> true;
            case DENY, BOTH -> false;
        };
    }

    private static void add(List<PermissionOverride> overrides, Level level, String projectId) {
        if (level == Level.GRANT || level == Level.BOTH) overrides.add(override(CODE, GRANT, projectId));
        if (level == Level.DENY || level == Level.BOTH) overrides.add(override(CODE, DENY, projectId));
    }

    private static PermissionOverride override(String code, PermissionEffect effect, String projectId) {
        return new PermissionOverride(code, effect, projectId, "admin", Instant.EPOCH);
    }

    @Nested
    class SpecScenarios {

        @Test
        void should_denyOnlyOnProject_when_roleGrantsAndProjectDenies() {
            PermissionResolver r = new PermissionResolver(List.of("AI_READ_ALL"),
                    List.of(override("AI_READ_ALL", DENY, P)), Set.of(P, OTHER));

            assertThat(r.hasOnProject("AI_READ_ALL", P)).isFalse();
            assertThat(r.hasOnProject("AI_READ_ALL", OTHER)).isTrue();
        }

        @Test
        void should_grantOnlyThatProject_when_roleLacksAndProjectGrants() {
            PermissionResolver r = new PermissionResolver(List.of(), List.of(override(CODE, GRANT, P)), Set.of());

            assertThat(r.hasOnProject(CODE, P)).isTrue();
            assertThat(r.hasOnProject(CODE, OTHER)).isFalse();
            assertThat(r.hasOrg(CODE)).isFalse();
            assertThat(r.isProjectAccessible(P)).isTrue();
            assertThat(r.projectGrantIds()).containsExactly(P);
        }

        @Test
        void should_holdInNoProject_when_organizationDenies_unlessProjectGrants() {
            PermissionResolver r = new PermissionResolver(List.of("DASHBOARD_STRATEGIC", ACCESS_ALL),
                    List.of(override("DASHBOARD_STRATEGIC", DENY, null), override("DASHBOARD_STRATEGIC", GRANT, P)),
                    Set.of(OTHER));

            assertThat(r.hasOnProject("DASHBOARD_STRATEGIC", OTHER)).isFalse();
            assertThat(r.hasOnProject("DASHBOARD_STRATEGIC", P)).isTrue();
        }

        @Test
        void should_readEveryProject_when_managerHoldsAccessAllWithoutMembership() {
            PermissionResolver r = new PermissionResolver(List.of(CODE, ACCESS_ALL), List.of(), Set.of());

            assertThat(r.hasOnProject(CODE, P)).isTrue();
            assertThat(r.isProjectAccessible(P)).isTrue();
        }

        @Test
        void should_notReadProject_when_techLeadIsNotMember() {
            PermissionResolver r = new PermissionResolver(List.of(CODE), List.of(), Set.of(OTHER));

            assertThat(r.hasOnProject(CODE, P)).isFalse();
            assertThat(r.isProjectAccessible(P)).isFalse();
            assertThat(r.hasOnProject(CODE, OTHER)).isTrue();
        }

        @Test
        void should_loseAccessAll_when_organizationDeniesIt() {
            PermissionResolver r = new PermissionResolver(List.of(CODE, ACCESS_ALL),
                    List.of(override(ACCESS_ALL, DENY, null)), Set.of());

            assertThat(r.hasProjectAccessAll()).isFalse();
            assertThat(r.hasOnProject(CODE, P)).isFalse();
        }

        @Test
        void should_requireOrgCodeAndAccessibleProject_forOrgOnlyCodes() {
            PermissionResolver member = new PermissionResolver(List.of("DASHBOARD_INDIVIDUAL"), List.of(), Set.of(P));
            PermissionResolver stranger = new PermissionResolver(List.of("DASHBOARD_INDIVIDUAL"), List.of(), Set.of());

            assertThat(member.hasOnProject("DASHBOARD_INDIVIDUAL", P)).isTrue();
            assertThat(stranger.hasOnProject("DASHBOARD_INDIVIDUAL", P)).isFalse();
        }

        @Test
        void should_ignoreProjectOverrides_ofOrgOnlyCodes() {
            PermissionResolver r = new PermissionResolver(List.of(), List.of(override("USER_MANAGE", GRANT, P)), Set.of(P));

            assertThat(r.hasOnProject("USER_MANAGE", P)).isFalse();
            assertThat(r.hasOrg("USER_MANAGE")).isFalse();
            assertThat(r.hasProjectGrant(P)).isFalse();
        }

        @Test
        void should_canonicalizeLegacyRoleCodes() {
            PermissionResolver r = new PermissionResolver(List.of("GITHUB_READ"), List.of(), Set.of(P));

            assertThat(r.hasOrg("REPOS_READ")).isTrue();
            assertThat(r.orgCodes()).containsExactly("REPOS_READ");
        }

        @Test
        void should_listSortedOrgCodes_includingOrgGrants_andExcludingDenied() {
            PermissionResolver r = new PermissionResolver(List.of("USER_MANAGE", CODE),
                    List.of(override(CODE, DENY, null), override("PRICE_TABLE_MANAGE", GRANT, null)), Set.of());

            assertThat(r.orgCodes()).containsExactly("PRICE_TABLE_MANAGE", "USER_MANAGE");
        }

        @Test
        void should_listProjectScopableCodesOnProject() {
            PermissionResolver r = new PermissionResolver(List.of("REPOS_READ", "AZURE_READ", "USER_MANAGE"),
                    List.of(override("REPOS_READ", DENY, P)), Set.of(P));

            assertThat(r.codesOnProject(P)).containsExactly("AZURE_READ");
        }
    }
}
