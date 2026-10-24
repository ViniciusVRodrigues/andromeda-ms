package andromeda.sei.ms.feature.azure.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static andromeda.sei.ms.feature.azure.domain.AzureOrgUserSource.*;
import static org.assertj.core.api.Assertions.assertThat;

class AzureOrgUserTest {

    private AzureOrgUser user(String name, String email, String principal, AzureOrgUserSource... sources) {
        Instant now = Instant.now();
        return new AzureOrgUser(null, "org", "az-1", name, email, principal, List.of(sources), true, now, now, null);
    }

    @Test
    void should_overwrite_when_directoryOverTeam() {
        var u = user("Team Name", "team@x.com", "team@x.com", TEAM);
        u.mergeFrom("Dir Name", "dir@x.com", "dir@x.com", DIRECTORY);
        assertThat(u.getDisplayName()).isEqualTo("Dir Name");
        assertThat(u.getAzureEmail()).isEqualTo("dir@x.com");
        assertThat(u.getPrincipalName()).isEqualTo("dir@x.com");
        assertThat(u.getSources()).containsExactlyInAnyOrder(TEAM, DIRECTORY);
    }

    @Test
    void should_notOverwrite_when_workItemOrPrOverTeamAndDirectory() {
        var u = user("Dir Name", "dir@x.com", "dir@x.com", DIRECTORY, TEAM);
        u.mergeFrom("Other", "other@x.com", "other@x.com", WORKITEM);
        u.mergeFrom("Other2", "other2@x.com", "o2", PR);
        assertThat(u.getDisplayName()).isEqualTo("Dir Name");
        assertThat(u.getAzureEmail()).isEqualTo("dir@x.com");
        assertThat(u.getSources()).containsExactlyInAnyOrder(DIRECTORY, TEAM, WORKITEM, PR);
    }

    @Test
    void should_fillBlankFields_when_lowerPrecedence() {
        var u = user("Dir", null, " ", DIRECTORY);
        u.mergeFrom("Ignored", "wi@x.com", "wi@x.com", WORKITEM);
        assertThat(u.getDisplayName()).isEqualTo("Dir");
        assertThat(u.getAzureEmail()).isEqualTo("wi@x.com");
        assertThat(u.getPrincipalName()).isEqualTo("wi@x.com");
    }

    @Test
    void should_keepExisting_when_observedValueBlank() {
        var u = user("Dir", "dir@x.com", "dir@x.com", TEAM);
        u.mergeFrom(null, "", null, DIRECTORY);
        assertThat(u.getDisplayName()).isEqualTo("Dir");
        assertThat(u.getAzureEmail()).isEqualTo("dir@x.com");
        assertThat(u.getSources()).contains(DIRECTORY);
    }

    @Test
    void should_beOverwrittenByTeam_when_onlyWorkItemSeenBefore() {
        var u = user("wi", "wi@x.com", null, WORKITEM);
        u.mergeFrom("Team", "t@x.com", "t@x.com", TEAM);
        assertThat(u.getDisplayName()).isEqualTo("Team");
        assertThat(u.getSources()).containsExactlyInAnyOrder(WORKITEM, TEAM);
    }
}
