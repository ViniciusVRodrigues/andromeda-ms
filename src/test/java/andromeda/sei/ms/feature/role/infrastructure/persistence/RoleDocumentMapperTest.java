package andromeda.sei.ms.feature.role.infrastructure.persistence;

import andromeda.sei.ms.feature.role.application.dto.RoleOutput;
import andromeda.sei.ms.feature.role.domain.Role;
import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import andromeda.sei.ms.shared.presentation.PermissionController;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rollback-safe {@code GITHUB_*} -> {@code REPOS_*} rename (vcs-core-refactor): the stored role keeps
 * each renamed code paired with its legacy mirror, while everything read from it — the domain role,
 * {@code GET /roles}, the permission snapshot behind {@code /auth/me}, {@code rolePermissions},
 * member-permissions and the JWT {@code permissions} claim — only ever shows {@code REPOS_*}.
 */
class RoleDocumentMapperTest {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    private final RoleDocumentMapper mapper = new RoleDocumentMapper();

    @Test
    void should_storeLegacyMirror_nextToEveryRenamedCode_onWrite() {
        Role role = role(List.of("AZURE_READ", "REPOS_READ", "REPOS_CONFIGURE"));

        RoleDocument doc = mapper.toDocument(role);

        assertThat(doc.getPermissionCodes())
                .containsExactly("AZURE_READ", "REPOS_READ", "GITHUB_READ", "REPOS_CONFIGURE", "GITHUB_CONFIGURE");
    }

    @Test
    void should_dropTheMirror_whenTheCurrentCodeIsRemoved() {
        Role role = role(List.of("AZURE_READ"));

        assertThat(mapper.toDocument(role).getPermissionCodes()).containsExactly("AZURE_READ");
    }

    @Test
    void should_hideLegacyCodes_onRead() {
        RoleDocument doc = RoleDocument.builder().id("r1").name("Custom").permissionCodes(
                List.of("REPOS_READ", "GITHUB_READ", "GITHUB_CONFIGURE", "USER_MANAGE")).active(true).build();

        Role role = mapper.toDomain(doc);

        assertThat(role.getPermissionCodes()).containsExactly("REPOS_READ", "REPOS_CONFIGURE", "USER_MANAGE");
        assertThat(RoleOutput.from(role).permissionCodes()).noneMatch(code -> code.startsWith("GITHUB_"));
    }

    @Test
    void should_roundTrip_withoutLeakingOrDuplicatingCodes() {
        Role stored = mapper.toDomain(mapper.toDocument(role(List.of("REPOS_READ", "DASHBOARD_TEAM"))));

        assertThat(stored.getPermissionCodes()).containsExactly("REPOS_READ", "DASHBOARD_TEAM");
    }

    /** {@code /auth/me}, {@code rolePermissions}, member-permissions and authorities come from the snapshot. */
    @Test
    void should_neverExposeLegacyCodes_throughThePermissionSnapshot() {
        UserPermissionSnapshot snapshot = new UserPermissionSnapshot("org-1", "u1", "r1", "Custom",
                List.of("REPOS_READ", "GITHUB_READ", "REPOS_CONFIGURE", "GITHUB_CONFIGURE"), List.of(), Set.of("p1"), 0L);

        assertThat(snapshot.roleCodes()).containsExactly("REPOS_READ", "REPOS_CONFIGURE");
        assertThat(snapshot.resolver().orgCodes()).containsExactlyInAnyOrder("REPOS_READ", "REPOS_CONFIGURE");
        assertThat(snapshot.resolver().codesOnProject("p1")).containsExactly("REPOS_READ");
    }

    @Test
    void should_neverListLegacyCodes_inThePermissionCatalog() {
        var body = new PermissionController().list().getBody();

        assertThat(body).isNotNull();
        assertThat(body.get("permissions")).extracting(p -> p.code()).noneMatch(code -> code.startsWith("GITHUB_"));
        assertThat(PermissionCatalog.codes()).noneMatch(code -> code.startsWith("GITHUB_"));
    }

    private static Role role(List<String> codes) {
        return new Role("r1", "org-1", "Custom", "Custom", false, null, new ArrayList<>(codes), true, 0L, NOW, NOW);
    }
}
