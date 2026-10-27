package andromeda.sei.ms.feature.vcs.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Port of {@code vcs_review_events}. Every query is scoped by {@code organizationId}. */
public interface VcsReviewEventRepository {

    Optional<VcsReviewEvent> findByOrganizationIdAndPullRequestIdAndExternalEventId(
            String organizationId, String pullRequestId, String externalEventId);

    /** Active events of many pull requests in one query, so metric recalculation is not N+1. */
    List<VcsReviewEvent> findAllByOrganizationIdAndPullRequestIdIn(String organizationId,
                                                                   Collection<String> pullRequestIds);

    /**
     * Gives an owner to the ownerless review events of the provider whose {@code actorExternalId} is
     * one of {@code externalIds}. Never overwrites an existing owner; scoped by organization.
     */
    VcsAttribution attributeByActorExternalIds(String organizationId, VcsProvider provider,
                                               Collection<String> externalIds, String userId);

    VcsReviewEvent save(VcsReviewEvent event);
}
