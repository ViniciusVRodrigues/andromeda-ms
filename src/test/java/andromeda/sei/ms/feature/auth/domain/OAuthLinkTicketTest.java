package andromeda.sei.ms.feature.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthLinkTicketTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    @Test
    void should_requirePurpose() {
        assertThatThrownBy(() -> OAuthLinkTicket.issue("org", "user", "hash", null, null, NOW, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_requireAzureOrgUrl_forAzureOrgConnect() {
        assertThatThrownBy(() -> OAuthLinkTicket.issue("org", "user", "hash",
                OAuthLinkPurpose.AZURE_ORG_CONNECT, " ", NOW, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_issueGithubTicket_withoutAzureOrgUrl_andExpireInTtl() {
        OAuthLinkTicket t = OAuthLinkTicket.issue("org", "user", "hash",
                OAuthLinkPurpose.GITHUB_PROFILE, null, NOW, 60);

        assertThat(t.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(t.getConsumedAt()).isNull();
        assertThat(t.getAzureOrgUrl()).isNull();
        assertThat(t.getOrganizationId()).isEqualTo("org");
    }

    @Test
    void should_hashWithSha256Hex() {
        assertThat(OAuthLinkTicket.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
