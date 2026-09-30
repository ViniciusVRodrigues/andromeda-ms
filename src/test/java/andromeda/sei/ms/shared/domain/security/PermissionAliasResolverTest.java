package andromeda.sei.ms.shared.domain.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** repository-permissions: GITHUB_* claims are honoured as their REPOS_* equivalents (vcs-core D8). */
class PermissionAliasResolverTest {

    @Test
    void should_addCurrentCode_nextToLegacyOne_whenExpandingTokenClaims() {
        assertThat(PermissionAliasResolver.expand(List.of("AZURE_READ", "GITHUB_READ", "GITHUB_CONFIGURE")))
                .containsExactly("AZURE_READ", "GITHUB_READ", "REPOS_READ", "GITHUB_CONFIGURE", "REPOS_CONFIGURE");
    }

    @Test
    void should_notDuplicate_whenTokenAlreadyCarriesCurrentCode() {
        assertThat(PermissionAliasResolver.expand(List.of("REPOS_READ", "GITHUB_READ")))
                .containsExactly("REPOS_READ", "GITHUB_READ");
    }

    @Test
    void should_replaceLegacyCodes_whenCanonicalizing() {
        assertThat(PermissionAliasResolver.canonicalize(List.of("GITHUB_READ", "AI_READ_OWN", "REPOS_READ", "GITHUB_CONFIGURE")))
                .containsExactly("REPOS_READ", "AI_READ_OWN", "REPOS_CONFIGURE");
    }

    @Test
    void should_returnEmpty_forNull() {
        assertThat(PermissionAliasResolver.expand(null)).isEmpty();
        assertThat(PermissionAliasResolver.canonicalize(null)).isEmpty();
        assertThat(PermissionAliasResolver.withLegacyMirrors(null)).isEmpty();
    }

    @Test
    void should_storeLegacyMirror_nextToEachRenamedCode() {
        assertThat(PermissionAliasResolver.withLegacyMirrors(List.of("AZURE_READ", "REPOS_READ", "REPOS_CONFIGURE")))
                .containsExactly("AZURE_READ", "REPOS_READ", "GITHUB_READ", "REPOS_CONFIGURE", "GITHUB_CONFIGURE");
    }

    @Test
    void should_normalizeLegacyOnlyCode_toTheMirroredPair() {
        assertThat(PermissionAliasResolver.withLegacyMirrors(List.of("GITHUB_READ", "REPOS_READ", "AI_READ_OWN")))
                .containsExactly("REPOS_READ", "GITHUB_READ", "AI_READ_OWN");
    }

    @Test
    void should_hideMirrors_whenCanonicalizingTheStorageForm() {
        List<String> stored = PermissionAliasResolver.withLegacyMirrors(List.of("REPOS_READ", "USER_MANAGE"));

        assertThat(PermissionAliasResolver.canonicalize(stored)).containsExactly("REPOS_READ", "USER_MANAGE");
    }
}
