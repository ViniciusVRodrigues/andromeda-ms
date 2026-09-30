package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommentDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzIdentityDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzThreadDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tasks 4.1/4.2: thread → review signal mapping, first against the official 7.1 sample payload
 * (MergeAttempt, ReviewersUpdate, VoteUpdate, RefUpdate, text, code thread with a deleted reply),
 * then edge cases built by hand.
 */
class AzureThreadReviewSignalMapperTest {

    private static final String VOTER = "d6245f20-2af8-44f4-9451-8107cb2767db";

    @Test
    void shouldMapTheOfficialSamplePayload() throws IOException {
        List<VcsReviewSignal> signals = AzureThreadReviewSignalMapper.map(officialThreads(), List.of());

        assertThat(signals).extracting(VcsReviewSignal::externalEventId)
                .containsExactly("143", "147:1", "148:1", "148:2");

        VcsReviewSignal vote = signals.getFirst();
        assertThat(vote.kind()).isEqualTo(ReviewKind.APPROVED);
        assertThat(vote.actorExternalId()).isEqualTo(VOTER);
        assertThat(vote.actorLogin()).isEqualTo("Normal Paulk");
        assertThat(vote.occurredAt()).isEqualTo(Instant.parse("2016-11-01T16:30:36.58Z"));
        assertThat(vote.bot()).isFalse();
        assertThat(vote.deleted()).isFalse();

        VcsReviewSignal comment = signals.get(1);
        assertThat(comment.kind()).isEqualTo(ReviewKind.COMMENTED);
        assertThat(comment.actorExternalId()).isEqualTo(VOTER);
        assertThat(comment.actorEmail()).isEqualTo("fabrikamfiber16@hotmail.com");
        assertThat(comment.occurredAt()).isEqualTo(Instant.parse("2016-11-01T16:30:48.91Z"));

        assertThat(signals.get(2).deleted()).isFalse();
        assertThat(signals.get(3).deleted()).as("deleted reply of the code thread").isTrue();
    }

    @Test
    void shouldIgnoreSystemThreadsOtherThanVotes() throws IOException {
        List<VcsReviewSignal> signals = AzureThreadReviewSignalMapper.map(officialThreads(), List.of());

        assertThat(signals).extracting(VcsReviewSignal::externalEventId)
                .doesNotContain("141", "142", "144", "145", "146")
                .noneMatch(id -> id.startsWith("141:") || id.startsWith("145:"));
    }

    @Test
    void shouldMapEveryVoteValueAndIgnoreTheReset() {
        assertThat(AzureThreadReviewSignalMapper.voteKind("10")).contains(ReviewKind.APPROVED);
        assertThat(AzureThreadReviewSignalMapper.voteKind("5")).contains(ReviewKind.APPROVED_WITH_SUGGESTIONS);
        assertThat(AzureThreadReviewSignalMapper.voteKind("-5")).contains(ReviewKind.CHANGES_REQUESTED);
        assertThat(AzureThreadReviewSignalMapper.voteKind("-10")).contains(ReviewKind.REJECTED);
        assertThat(AzureThreadReviewSignalMapper.voteKind("0")).isEmpty();
        assertThat(AzureThreadReviewSignalMapper.voteKind("abc")).isEmpty();
        assertThat(AzureThreadReviewSignalMapper.voteKind(null)).isEmpty();
    }

    @Test
    void shouldNotCreateAnEventForAResetVote() {
        List<VcsReviewSignal> signals = AzureThreadReviewSignalMapper.map(
                List.of(voteThread(200, "0", VOTER, false)), List.of());

        assertThat(signals).isEmpty();
    }

    @Test
    void shouldNormalizeAVoterIdWithoutHyphens() {
        List<VcsReviewSignal> signals = AzureThreadReviewSignalMapper.map(
                List.of(voteThread(201, "-5", "D6245F202AF844F494518107CB2767DB", false)), List.of());

        assertThat(signals).singleElement().satisfies(signal -> {
            assertThat(signal.actorExternalId()).isEqualTo(VOTER);
            assertThat(signal.kind()).isEqualTo(ReviewKind.CHANGES_REQUESTED);
        });
    }

    @Test
    void shouldFlagEverySignalOfADeletedThreadAsDeleted() {
        List<VcsReviewSignal> signals = AzureThreadReviewSignalMapper.map(
                List.of(voteThread(202, "10", VOTER, true)), List.of());

        assertThat(signals).singleElement().satisfies(signal -> assertThat(signal.deleted()).isTrue());
    }

    @Test
    void shouldIgnoreAVoteThreadWithoutVoter() {
        AzThreadDto thread = new AzThreadDto(203, "2026-09-01T10:00:00Z", null, List.of(),
                Map.of("CodeReviewThreadType", prop("VoteUpdate"), "CodeReviewVoteResult", prop("10")), false);

        assertThat(AzureThreadReviewSignalMapper.map(List.of(thread), List.of())).isEmpty();
    }

    @Test
    void shouldFlagBuildServiceCommentsAsBots() {
        AzIdentityDto buildService = new AzIdentityDto("11111111-2222-3333-4444-555555555555",
                "Payments Build Service (acme)", "Build\\11111111-2222-3333-4444-555555555555", false);
        AzThreadDto thread = new AzThreadDto(204, "2026-09-01T10:00:00Z", null,
                List.of(new AzCommentDto(1, 0, buildService, "Coverage 81%", "2026-09-01T10:00:00Z", "text", false)),
                Map.of(), false);

        assertThat(AzureThreadReviewSignalMapper.map(List.of(thread), List.of()))
                .singleElement().satisfies(signal -> assertThat(signal.bot()).isTrue());
    }

    @Test
    void shouldFlagIdentitiesListedByTheOrganizationAsBots() {
        AzIdentityDto human = new AzIdentityDto(VOTER, "Release Bot", "release@acme.com", false);
        AzThreadDto thread = new AzThreadDto(205, "2026-09-01T10:00:00Z", null,
                List.of(new AzCommentDto(1, 0, human, "lgtm", "2026-09-01T10:00:00Z", "text", false)), Map.of(), false);

        assertThat(AzureThreadReviewSignalMapper.map(List.of(thread), List.of("RELEASE@acme.com")))
                .singleElement().satisfies(signal -> assertThat(signal.bot()).isTrue());
        assertThat(AzureThreadReviewSignalMapper.map(List.of(thread), List.of()))
                .singleElement().satisfies(signal -> assertThat(signal.bot()).isFalse());
    }

    @Test
    void shouldToleratePayloadGaps() {
        AzThreadDto noComments = new AzThreadDto(206, null, null, null, null, null);

        assertThat(AzureThreadReviewSignalMapper.map(List.of(noComments), null)).isEmpty();
        assertThat(AzureThreadReviewSignalMapper.map(null, List.of())).isEmpty();
    }

    static List<AzThreadDto> officialThreads() throws IOException {
        try (InputStream in = AzureThreadReviewSignalMapperTest.class
                .getResourceAsStream("/fixtures/azure-repos/threads.json")) {
            ThreadsPage page = new ObjectMapper().readValue(in, ThreadsPage.class);
            return page.value();
        }
    }

    record ThreadsPage(List<AzThreadDto> value, Integer count) {}

    private static AzThreadDto voteThread(int id, String vote, String voterId, boolean deleted) {
        AzIdentityDto service = new AzIdentityDto("41113706-4320-4083-9150-925feb93fc22",
                "[DefaultCollection]\\Project Collection Service Accounts", null, true);
        return new AzThreadDto(id, "2026-09-01T14:00:00Z", "2026-09-01T14:00:00Z",
                List.of(new AzCommentDto(1, 0, service, "voted", "2026-09-01T14:00:00Z", "system", false)),
                Map.of("CodeReviewThreadType", prop("VoteUpdate"),
                        "CodeReviewVotedByTfId", prop(voterId),
                        "CodeReviewVotedByDisplayName", prop("Normal Paulk"),
                        "CodeReviewVoteResult", prop(vote)),
                deleted);
    }

    private static AzThreadDto.Property prop(String value) {
        return new AzThreadDto.Property("System.String", value);
    }
}
