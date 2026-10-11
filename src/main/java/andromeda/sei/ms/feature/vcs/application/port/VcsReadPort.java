package andromeda.sei.ms.feature.vcs.application.port;

import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Provider-neutral read side of version-control data (vcs-core D6). Dashboards, delivery metrics
 * and the Repositórios screen depend on this port, never on a provider type. Every query is scoped
 * by {@code organizationId}; pull request lists contain active rows only; windows are half-open
 * {@code [from, to)}.
 */
public interface VcsReadPort {

    /** Active bindings of a project (any provider). */
    List<VcsRepoBinding> bindingsOfProject(String organizationId, String projectId);

    /** Active repositories bound to the project through active bindings (pending ones included). */
    List<VcsRepository> repositoriesOfProject(String organizationId, String projectId);

    /** The repository of this organization, or empty when it belongs to another one. */
    Optional<VcsRepository> repository(String organizationId, String repositoryId);

    List<VcsPullRequest> pullRequestsOfRepository(String organizationId, String repositoryId);

    List<VcsPullRequest> openPullRequests(String organizationId, Collection<String> repositoryIds);

    List<VcsPullRequest> pullRequestsCreatedIn(String organizationId, Collection<String> repositoryIds,
                                               Instant from, Instant to);

    List<VcsPullRequest> mergedPullRequests(String organizationId, Collection<String> repositoryIds,
                                            Instant from, Instant to);

    /** Active pull requests of the repositories open now or created or merged at or after {@code since}. */
    List<VcsPullRequest> pullRequestsTouchedSince(String organizationId, Collection<String> repositoryIds, Instant since);

    /** Pull requests authored by an Andromeda user, created within the window, any repository. */
    List<VcsPullRequest> pullRequestsByAuthor(String organizationId, String userId, Instant from, Instant to);

    List<VcsReviewEvent> reviewEvents(String organizationId, Collection<String> pullRequestIds);
}
