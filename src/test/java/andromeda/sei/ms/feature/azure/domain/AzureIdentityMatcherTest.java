package andromeda.sei.ms.feature.azure.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static andromeda.sei.ms.feature.azure.domain.AzureIdentityMatcher.Status.*;
import static org.assertj.core.api.Assertions.assertThat;

class AzureIdentityMatcherTest {

    private static AzureOrgUser identity(String azureId, String email, String principal, boolean active) {
        Instant now = Instant.now();
        return new AzureOrgUser(null, "org", azureId, "N", email, principal,
                List.of(AzureOrgUserSource.DIRECTORY), active, now, now, null);
    }

    @Test
    void should_returnLinked_whenUserAlreadyHasAzureId_evenWithCandidate() {
        var r = AzureIdentityMatcher.match("a@x.com", true, List.of(identity("1", "a@x.com", null, true)), Set.of());
        assertThat(r.status()).isEqualTo(LINKED);
    }

    @Test
    void should_returnNotFound_whenNoCandidateOrBlankEmail() {
        assertThat(AzureIdentityMatcher.match("a@x.com", false, List.of(identity("1", "b@x.com", null, true)), Set.of())
                .status()).isEqualTo(NOT_FOUND);
        assertThat(AzureIdentityMatcher.match("  ", false, List.of(identity("1", "b@x.com", null, true)), Set.of())
                .status()).isEqualTo(NOT_FOUND);
    }

    @Test
    void should_match_withNormalizedEmail() {
        var c = identity("1", "pedro@empresa.com", null, true);
        var r = AzureIdentityMatcher.match(" Pedro@Empresa.com ", false, List.of(c), Set.of());
        assertThat(r.status()).isEqualTo(MATCH);
        assertThat(r.candidate()).isSameAs(c);
    }

    @Test
    void should_match_byPrincipalName_whenEmailEmpty() {
        var r = AzureIdentityMatcher.match("pedro@empresa.com", false,
                List.of(identity("1", "", "PEDRO@empresa.com", true)), Set.of());
        assertThat(r.status()).isEqualTo(MATCH);
    }

    @Test
    void should_returnAmbiguous_whenTwoDistinctIdentitiesMatch() {
        var r = AzureIdentityMatcher.match("a@x.com", false,
                List.of(identity("1", "a@x.com", null, true), identity("2", null, "A@x.com", true)), Set.of());
        assertThat(r.status()).isEqualTo(AMBIGUOUS);
    }

    @Test
    void should_notBeAmbiguous_whenSameIdentityMatchesByEmailAndPrincipal() {
        var r = AzureIdentityMatcher.match("a@x.com", false,
                List.of(identity("1", "a@x.com", "a@x.com", true)), Set.of());
        assertThat(r.status()).isEqualTo(MATCH);
    }

    @Test
    void should_returnTaken_whenOnlyCandidateBelongsToAnotherUser() {
        var r = AzureIdentityMatcher.match("a@x.com", false, List.of(identity("1", "a@x.com", null, true)), Set.of("1"));
        assertThat(r.status()).isEqualTo(TAKEN);
        assertThat(r.candidate()).isNull();
    }

    @Test
    void should_ignoreInactiveCandidates() {
        var r = AzureIdentityMatcher.match("a@x.com", false, List.of(identity("1", "a@x.com", null, false)), Set.of());
        assertThat(r.status()).isEqualTo(NOT_FOUND);
    }
}
