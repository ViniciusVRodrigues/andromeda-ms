package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommentDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzIdentityDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzThreadDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pull request threads → review signals (design D3, azure-repos-integration "Review signals from
 * pull request threads"). Pure: no I/O, tolerant of missing properties.
 *
 * <ul>
 *   <li>A {@code VoteUpdate} system thread is one vote event, id {@code threadId}: voter from
 *       {@code CodeReviewVotedByTfId} (normalized GUID) — never the comment author, which is the
 *       collection service account — time from the thread's {@code publishedDate}, kind from
 *       {@code CodeReviewVoteResult}: 10 approved, 5 approved with suggestions, -5 changes
 *       requested (waiting for author), -10 rejected; 0 (reset) and anything else is ignored.</li>
 *   <li>Every {@code text} comment, in any thread (code threads included), is one
 *       {@code COMMENTED} event, id {@code threadId:commentId}, with its own author and time.</li>
 *   <li>Other system threads ({@code MergeAttempt}, {@code ReviewersUpdate}, {@code RefUpdate}, …)
 *       and {@code system}/{@code codeChange} comments produce nothing.</li>
 *   <li>A deleted comment, or any signal of a deleted thread, comes out with {@code deleted = true}:
 *       the sync marks an event stored earlier and creates none.</li>
 * </ul>
 */
public final class AzureThreadReviewSignalMapper {

    static final String THREAD_TYPE = "CodeReviewThreadType";
    static final String VOTE_UPDATE = "VoteUpdate";
    static final String VOTED_BY_ID = "CodeReviewVotedByTfId";
    static final String VOTED_BY_NAME = "CodeReviewVotedByDisplayName";
    static final String VOTE_RESULT = "CodeReviewVoteResult";
    static final String TEXT_COMMENT = "text";

    private static final Map<Integer, ReviewKind> VOTES = Map.of(
            10, ReviewKind.APPROVED,
            5, ReviewKind.APPROVED_WITH_SUGGESTIONS,
            -5, ReviewKind.CHANGES_REQUESTED,
            -10, ReviewKind.REJECTED);

    private AzureThreadReviewSignalMapper() {}

    /** @param serviceIdentities the organization's own service identity list (bots) */
    public static List<VcsReviewSignal> map(List<AzThreadDto> threads, Collection<String> serviceIdentities) {
        List<VcsReviewSignal> signals = new ArrayList<>();
        if (threads == null) return signals;
        for (AzThreadDto thread : threads) {
            if (thread == null || thread.id() == null) continue;
            vote(thread, serviceIdentities).ifPresent(signals::add);
            signals.addAll(comments(thread, serviceIdentities));
        }
        return signals;
    }

    static Optional<ReviewKind> voteKind(String voteResult) {
        if (voteResult == null) return Optional.empty();
        try {
            return Optional.ofNullable(VOTES.get(Integer.parseInt(voteResult.trim())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<VcsReviewSignal> vote(AzThreadDto thread, Collection<String> serviceIdentities) {
        if (!VOTE_UPDATE.equals(thread.property(THREAD_TYPE))) return Optional.empty();
        Optional<ReviewKind> kind = voteKind(thread.property(VOTE_RESULT));
        String voterId = AzureIdentityIds.normalize(thread.property(VOTED_BY_ID));
        if (kind.isEmpty() || voterId == null) return Optional.empty();
        String voterName = thread.property(VOTED_BY_NAME);
        boolean bot = AzureServiceIdentityDetector.isServiceIdentity(voterId, null, voterName, false, serviceIdentities);
        Instant at = AzureDates.parse(thread.publishedDate());
        return Optional.of(new VcsReviewSignal(String.valueOf(thread.id()), voterId, voterName, bot, kind.get(),
                false, at, thread.isDeleted(), null));
    }

    private static List<VcsReviewSignal> comments(AzThreadDto thread, Collection<String> serviceIdentities) {
        List<VcsReviewSignal> signals = new ArrayList<>();
        if (thread.comments() == null) return signals;
        for (AzCommentDto comment : thread.comments()) {
            if (comment == null || comment.id() == null || !TEXT_COMMENT.equals(comment.commentType())) continue;
            AzIdentityDto author = comment.author();
            String authorId = author == null ? null : AzureIdentityIds.normalize(author.id());
            String login = author == null ? null : author.displayName();
            String email = author == null ? null : author.uniqueName();
            boolean bot = author != null && AzureServiceIdentityDetector.isServiceIdentity(authorId, email, login,
                    author.isContainer(), serviceIdentities);
            signals.add(new VcsReviewSignal(thread.id() + ":" + comment.id(), authorId, login, bot,
                    ReviewKind.COMMENTED, false, AzureDates.parse(comment.publishedDate()),
                    thread.isDeleted() || comment.isDeleted(), email));
        }
        return signals;
    }
}
