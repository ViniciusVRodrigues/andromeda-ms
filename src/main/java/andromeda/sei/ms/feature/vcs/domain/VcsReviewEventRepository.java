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

    VcsReviewEvent save(VcsReviewEvent event);
}
