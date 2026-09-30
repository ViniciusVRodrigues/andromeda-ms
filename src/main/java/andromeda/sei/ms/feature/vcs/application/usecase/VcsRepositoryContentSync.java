package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.organization.domain.WorkItemLinkConfig;
import andromeda.sei.ms.feature.vcs.application.WorkItemLinkResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The content steps of {@link SyncRepositoryUseCase}: commits, pull requests (with work item link
 * and earliest commit) and review events of one repository. Every item is isolated: one bad
 * payload is logged and skipped instead of aborting the stream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VcsRepositoryContentSync {

    private final VcsCommitRepository commitRepository;
    private final VcsPullRequestRepository pullRequestRepository;
    private final VcsReviewEventRepository reviewEventRepository;

    /** What every step needs about the repository being synced. */
    public record Context(VcsCredentials credentials, VcsProviderClient client, VcsAuthorResolver authors,
                          VcsRepository repository, WorkItemLinkConfig linkConfig) {

        String organizationId() {
            return repository.getOrganizationId();
        }
    }

    // ------------------------------------------------------------------
    // Commits
    // ------------------------------------------------------------------

    public int syncCommits(Context context, Instant since) {
        int processed = 0;
        for (VcsRemoteCommit remote : context.client().commitsSince(context.credentials(), context.repository(), since)) {
            try {
                upsertCommit(context, remote);
                processed++;
            } catch (RuntimeException e) {
                log.warn("Failed to upsert commit sha={} repo={}: {}", remote.sha(),
                        context.repository().getDisplayName(), e.getMessage());
            }
        }
        return processed;
    }

    private void upsertCommit(Context context, VcsRemoteCommit remote) {
        VcsRepository repository = context.repository();
        VcsCommit commit = commitRepository
                .findByOrganizationIdAndRepositoryIdAndSha(context.organizationId(), repository.getId(), remote.sha())
                .orElseGet(() -> new VcsCommit(null, context.organizationId(), repository.getId(),
                        repository.getProvider(), remote.sha()));
        commit.setMessage(remote.message());
        commit.setAuthorLogin(remote.authorLogin());
        commit.setAuthorUserId(resolveUser(context, remote.authorExternalId(), remote.authorEmail()));
        commit.setAuthoredAt(remote.authoredAt());
        commitRepository.save(commit);
    }

    // ------------------------------------------------------------------
    // Pull requests
    // ------------------------------------------------------------------

    public List<VcsPullRequest> syncPullRequests(Context context, Instant watermark, Instant backfillFrom) {
        return context.client()
                .pullRequestsChangedSince(context.credentials(), context.repository(), watermark, backfillFrom)
                .stream()
                .map(remote -> upsertIsolated(context, remote))
                .filter(Objects::nonNull)
                .toList();
    }

    private VcsPullRequest upsertIsolated(Context context, VcsRemotePullRequest remote) {
        try {
            return upsertPullRequest(context, remote);
        } catch (RuntimeException e) {
            log.warn("Skipping pull request #{} of repo={}: {}", remote.number(),
                    context.repository().getDisplayName(), e.getMessage());
            return null;
        }
    }

    private VcsPullRequest upsertPullRequest(Context context, VcsRemotePullRequest remote) {
        VcsRepository repository = context.repository();
        VcsPullRequest pr = pullRequestRepository
                .findByOrganizationIdAndRepositoryIdAndExternalId(context.organizationId(), repository.getId(),
                        remote.externalId())
                .orElseGet(() -> new VcsPullRequest(null, context.organizationId(), repository.getId(),
                        repository.getProvider(), remote.externalId(), remote.number(), remote.title(),
                        remote.status()));
        pr.setNumber(remote.number());
        pr.setTitle(remote.title());
        pr.setStatus(remote.status());
        pr.setAuthorLogin(remote.authorLogin());
        pr.setAuthorExternalId(remote.authorExternalId());
        pr.setAuthorUserId(resolveUser(context, remote.authorExternalId(), remote.authorEmail()));
        if (pr.getCreatedAt() == null) pr.setCreatedAt(remote.createdAt());
        pr.setMergedAt(remote.mergedAt());
        pr.setClosedAt(remote.closedAt());
        pr.setExternalUpdatedAt(remote.updatedAt());
        pr.setLinkedWorkItemExternalId(resolveWorkItemLink(context, remote));
        pr.setDraft(remote.draft());
        pr.setBaseBranch(remote.baseBranch());
        pr.setSourceBranch(remote.sourceBranch());
        pr.setLabels(remote.labels() == null ? List.of() : remote.labels());
        if (remote.status() == PullRequestStatus.MERGED && pr.getEarliestCommitAt() == null) {
            pr.setEarliestCommitAt(earliestCommitAt(context, pr));
        }
        return pullRequestRepository.save(pr);
    }

    /** Best effort: linking never fails the sync (vcs-work-item-linking cascade). */
    private String resolveWorkItemLink(Context context, VcsRemotePullRequest remote) {
        try {
            List<String> nativeLinks = context.client()
                    .nativeWorkItemLinks(context.credentials(), context.repository(), remote);
            return WorkItemLinkResolver.resolve(nativeLinks, context.linkConfig(), remote.sourceBranch(),
                    remote.body(), remote.title()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("Work item link resolution failed for PR #{}: {}", remote.number(), e.getMessage());
            return null;
        }
    }

    /**
     * Earliest authoring time among the pull request's commits, or {@code null} when it cannot be
     * read — change lead time then falls back to the pull request's creation time.
     */
    private Instant earliestCommitAt(Context context, VcsPullRequest pr) {
        try {
            return context.client().pullRequestCommits(context.credentials(), context.repository(), pr).stream()
                    .map(VcsRemoteCommit::authoredAt)
                    .filter(Objects::nonNull)
                    .min(Instant::compareTo)
                    .orElse(null);
        } catch (RuntimeException e) {
            log.warn("Could not read commits of PR #{} repo={}: {}", pr.getNumber(),
                    context.repository().getDisplayName(), e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Review events
    // ------------------------------------------------------------------

    /**
     * Review signals only of pull requests updated after the watermark: there is no bulk endpoint,
     * so scoping to changed pull requests bounds the N+1 cost.
     */
    public int syncReviewEvents(Context context, List<VcsPullRequest> pullRequests, Instant watermark) {
        int processed = 0;
        for (VcsPullRequest pr : pullRequests) {
            if (!isUpdatedSince(pr, watermark)) continue;
            try {
                for (VcsReviewSignal signal : context.client()
                        .reviewSignals(context.credentials(), context.repository(), pr)) {
                    if (upsertEvent(context, pr, signal)) processed++;
                }
            } catch (RuntimeException e) {
                log.warn("Failed to sync review events of PR #{} repo={}: {}", pr.getNumber(),
                        context.repository().getDisplayName(), e.getMessage());
            }
        }
        return processed;
    }

    private static boolean isUpdatedSince(VcsPullRequest pr, Instant watermark) {
        if (watermark == null) return true;
        return pr.getExternalUpdatedAt() != null && pr.getExternalUpdatedAt().isAfter(watermark);
    }

    /**
     * Upserts one signal. A deleted signal only flags an event stored earlier; one never stored is
     * skipped (azure-repos-integration "Deleted comment ignored").
     *
     * @return whether an event was written
     */
    private boolean upsertEvent(Context context, VcsPullRequest pr, VcsReviewSignal signal) {
        Optional<VcsReviewEvent> existing = reviewEventRepository.findByOrganizationIdAndPullRequestIdAndExternalEventId(
                context.organizationId(), pr.getId(), signal.externalEventId());
        if (signal.deleted()) {
            existing.filter(event -> !event.isDeleted()).ifPresent(event -> {
                event.setDeleted(true);
                reviewEventRepository.save(event);
            });
            return existing.isPresent();
        }
        VcsReviewEvent event = existing.orElseGet(() -> new VcsReviewEvent(null, context.organizationId(), pr.getId(),
                context.repository().getId(), context.repository().getProvider(), signal.externalEventId(),
                null, signal.occurredAt()));
        event.setKind(kindOf(signal, existing));
        event.setOccurredAt(signal.occurredAt());
        event.setActorExternalId(signal.actorExternalId());
        event.setActorLogin(signal.actorLogin());
        event.setActorUserId(resolveUser(context, signal.actorExternalId(), signal.actorEmail()));
        event.setBot(signal.bot());
        event.setDeleted(false);
        reviewEventRepository.save(event);
        return true;
    }

    /** The email-aware lookup only when the provider gave an email (GitHub never does). */
    private static String resolveUser(Context context, String externalId, String email) {
        return email == null
                ? context.authors().resolveUserId(context.organizationId(), externalId)
                : context.authors().resolveUserId(context.organizationId(), externalId, email);
    }

    /** A dismissed signal keeps the kind it had when first seen, falling back to {@code COMMENTED}. */
    static ReviewKind kindOf(VcsReviewSignal signal, Optional<VcsReviewEvent> existing) {
        if (!signal.dismissed() && signal.kind() != null) return signal.kind();
        return existing.map(VcsReviewEvent::getKind).filter(Objects::nonNull).orElse(ReviewKind.COMMENTED);
    }
}
