package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;

import java.time.Instant;
import java.util.List;

/**
 * Provider client port (vcs-core D4), use-case oriented rather than mirroring provider endpoints.
 * Implementations live in the provider's slice ({@code feature/github/infrastructure/vcs}) and map
 * provider payloads into the neutral records of this package. Failures surface as exceptions; the
 * callers isolate them per repository or per pull request.
 */
public interface VcsProviderClient {

    /** Cap of {@link #listBranches}. */
    int MAX_BRANCHES_LISTED = 1000;

    VcsProvider provider();

    /**
     * Fails when the credential cannot read this provider's repositories at all (a credential
     * shared with another integration may lack the code scope). Called before the first sync of a
     * binding; the default assumes the credential exists only for this provider.
     */
    default void verifyAccess(VcsCredentials credentials) {
        // no separate code scope to check
    }

    /** Repositories the organization's credential can see, for the bind picker. */
    List<VcsBindableRepository> listBindableRepositories(VcsCredentials credentials);

    /**
     * Resolves a repository typed by the admin (GitHub {@code owner/name}) into its metadata, or
     * throws when the name is unknown to the provider.
     */
    VcsRemoteRepository resolveRepository(VcsCredentials credentials, String displayName);

    /** Current metadata of a stored repository: by external id when known, by display name otherwise. */
    VcsRemoteRepository getRepository(VcsCredentials credentials, VcsRepository repository);

    /**
     * Branches of the repository, the default one first and the rest by name. At most
     * {@link #MAX_BRANCHES_LISTED} + 1 are read: a result larger than {@link #MAX_BRANCHES_LISTED} tells the
     * caller the listing was truncated.
     */
    List<VcsBranch> listBranches(VcsCredentials credentials, VcsRepository repository);

    /**
     * Commits of {@code branch} authored after {@code since}; {@code null} reads the full history.
     *
     * @throws andromeda.sei.ms.feature.vcs.domain.exception.VcsBranchNotFoundException when the branch no longer exists
     */
    List<VcsRemoteCommit> commitsSince(VcsCredentials credentials, VcsRepository repository, String branch,
                                       Instant since);

    /**
     * Pull requests changed after {@code watermark}. Without a watermark and with a
     * {@code backfillFrom}, only pull requests created at or after it (first-sync window,
     * on-demand-refresh D6); both {@code null} reads the full history.
     */
    List<VcsRemotePullRequest> pullRequestsChangedSince(VcsCredentials credentials, VcsRepository repository,
                                                        Instant watermark, Instant backfillFrom);

    /** Review signals of one pull request, already normalized (the provider's event mapper). */
    List<VcsReviewSignal> reviewSignals(VcsCredentials credentials, VcsRepository repository,
                                        VcsPullRequest pullRequest);

    /** Commits of one pull request, fetched live. */
    List<VcsRemoteCommit> pullRequestCommits(VcsCredentials credentials, VcsRepository repository,
                                             VcsPullRequest pullRequest);

    /**
     * Work item external ids natively linked to the pull request by the provider (Azure Repos);
     * empty when the provider has no native link (GitHub).
     */
    List<String> nativeWorkItemLinks(VcsCredentials credentials, VcsRepository repository,
                                     VcsRemotePullRequest pullRequest);
}
