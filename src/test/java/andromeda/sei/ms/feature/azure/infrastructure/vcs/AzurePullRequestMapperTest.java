package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommitDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzIdentityDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzLabelDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Task 4.3: pull request status, dates, draft and branches; commit author mapping. */
class AzurePullRequestMapperTest {

    private static final Instant OBSERVED = Instant.parse("2026-09-30T12:00:00Z");
    private static final AzIdentityDto AUTHOR = new AzIdentityDto("D6245F20-2AF8-44F4-9451-8107CB2767DB",
            "Normal Paulk", "fabrikamfiber16@hotmail.com", false);

    @Test
    void shouldMapTheOfficialCompletedSampleAsMerged() throws IOException {
        AzPullRequestDto dto = page("pullrequests-completed.json").getFirst();

        VcsRemotePullRequest pr = AzurePullRequestMapper.map(dto, OBSERVED);

        assertThat(pr.status()).isEqualTo(PullRequestStatus.MERGED);
        assertThat(pr.externalId()).isEqualTo("20");
        assertThat(pr.number()).isEqualTo(20);
        assertThat(pr.mergedAt()).isEqualTo(Instant.parse("2016-11-01T16:30:02.0413914Z"));
        assertThat(pr.closedAt()).isEqualTo(pr.mergedAt());
        assertThat(pr.updatedAt()).isEqualTo(pr.closedAt());
        assertThat(pr.createdAt()).isEqualTo(Instant.parse("2016-11-01T16:28:08.8900118Z"));
        assertThat(pr.sourceBranch()).isEqualTo("npaulk/my_work");
        assertThat(pr.baseBranch()).isEqualTo("new_feature");
        assertThat(pr.authorLogin()).isEqualTo("Normal Paulk");
        assertThat(pr.authorExternalId()).isEqualTo("d6245f20-2af8-44f4-9451-8107cb2767db");
        assertThat(pr.authorEmail()).isEqualTo("fabrikamfiber16@hotmail.com");
        assertThat(pr.body()).isEqualTo("Updated pull request description");
        assertThat(pr.draft()).isFalse();
    }

    @Test
    void shouldMapActivePullRequestsAsOpenObservedNow() throws IOException {
        VcsRemotePullRequest pr = AzurePullRequestMapper.map(page("pullrequests-active.json").getFirst(), OBSERVED);

        assertThat(pr.status()).isEqualTo(PullRequestStatus.OPEN);
        assertThat(pr.mergedAt()).isNull();
        assertThat(pr.closedAt()).isNull();
        assertThat(pr.updatedAt()).isEqualTo(OBSERVED);
    }

    @Test
    void shouldMapAbandonedAsClosedWithoutMergeDate() {
        AzPullRequestDto dto = new AzPullRequestDto(7, "abandoned", AUTHOR, "2026-09-01T10:00:00Z",
                "2026-09-02T10:00:00Z", "Try", null, "refs/heads/try", "refs/heads/main", null, null);

        VcsRemotePullRequest pr = AzurePullRequestMapper.map(dto, OBSERVED);

        assertThat(pr.status()).isEqualTo(PullRequestStatus.CLOSED);
        assertThat(pr.mergedAt()).isNull();
        assertThat(pr.closedAt()).isEqualTo(Instant.parse("2026-09-02T10:00:00Z"));
    }

    @Test
    void shouldMapDraftAndActiveLabels() {
        AzPullRequestDto dto = new AzPullRequestDto(8, "active", AUTHOR, "2026-09-01T10:00:00Z", null, "WIP", null,
                "refs/heads/feature/x", "refs/heads/main", true,
                List.of(new AzLabelDto("hotfix", true), new AzLabelDto("old", false), new AzLabelDto("ui", null)));

        VcsRemotePullRequest pr = AzurePullRequestMapper.map(dto, OBSERVED);

        assertThat(pr.draft()).isTrue();
        assertThat(pr.labels()).containsExactly("hotfix", "ui");
        assertThat(pr.sourceBranch()).isEqualTo("feature/x");
    }

    @Test
    void shouldTreatTheNeverDateAsAbsent() {
        AzPullRequestDto dto = new AzPullRequestDto(9, "active", null, "2026-09-01T10:00:00Z",
                "0001-01-01T00:00:00Z", "t", null, null, null, null, null);

        VcsRemotePullRequest pr = AzurePullRequestMapper.map(dto, OBSERVED);

        assertThat(pr.closedAt()).isNull();
        assertThat(pr.authorLogin()).isNull();
        assertThat(AzurePullRequestMapper.status(null)).isEqualTo(PullRequestStatus.OPEN);
    }

    @Test
    void shouldMapCommitAuthorByEmailOnly() {
        VcsRemoteCommit commit = AzureCommitMapper.map(new AzCommitDto("abc",
                new AzCommitDto.GitUser("Norman Paulk", "Fabrikamfiber16@hotmail.com", "2018-06-15T17:06:53Z"),
                "Merged PR 2"));

        assertThat(commit.sha()).isEqualTo("abc");
        assertThat(commit.authorLogin()).isEqualTo("Norman Paulk");
        assertThat(commit.authorExternalId()).isNull();
        assertThat(commit.authorEmail()).isEqualTo("Fabrikamfiber16@hotmail.com");
        assertThat(commit.authoredAt()).isEqualTo(Instant.parse("2018-06-15T17:06:53Z"));
    }

    private static List<AzPullRequestDto> page(String fixture) throws IOException {
        try (InputStream in = AzurePullRequestMapperTest.class.getResourceAsStream("/fixtures/azure-repos/" + fixture)) {
            return new ObjectMapper().readValue(in, PullRequestPage.class).value();
        }
    }

    record PullRequestPage(List<AzPullRequestDto> value, Integer count) {}
}
