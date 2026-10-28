package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Version-control activity of a project (any provider), read once per dashboard request: the
 * repositories bound to the project, all their active pull requests (windows are applied in
 * memory, so the previous sprint and the history reuse the same list), and the commits and human
 * reviews of the selected window. Every query is scoped by {@code organizationId}.
 */
@Component
@RequiredArgsConstructor
public class ProjectVcsActivity {

    private final VcsReadPort vcsReadPort;
    private final VcsCommitRepository commitRepository;

    public Snapshot load(String organizationId, String projectId, Instant from, Instant to) {
        List<VcsRepository> repositories = vcsReadPort.repositoriesOfProject(organizationId, projectId);
        if (repositories.isEmpty()) return Snapshot.EMPTY;
        List<String> repositoryIds = repositories.stream().map(VcsRepository::getId).toList();
        List<VcsPullRequest> pullRequests = repositoryIds.stream()
                .flatMap(id -> vcsReadPort.pullRequestsOfRepository(organizationId, id).stream())
                .toList();
        List<VcsCommit> commits = commitRepository.findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
                organizationId, repositoryIds, from, to);
        return new Snapshot(repositories, pullRequests, commits, reviews(organizationId, pullRequests, from, to),
                chosenBranches(organizationId, projectId, repositories));
    }

    /**
     * Repositories, the pull requests a window starting at {@code from} reads (open now, or created
     * or merged at or after {@code from} — not the whole history) and the commits of
     * {@code [from, to)}, without the review events: what a period view needs (time to first review
     * is stored on each pull request).
     */
    public Snapshot loadWithoutReviews(String organizationId, String projectId, Instant from, Instant to) {
        List<VcsRepository> repositories = vcsReadPort.repositoriesOfProject(organizationId, projectId);
        if (repositories.isEmpty()) return Snapshot.EMPTY;
        List<String> repositoryIds = repositories.stream().map(VcsRepository::getId).toList();
        List<VcsPullRequest> pullRequests = vcsReadPort.pullRequestsTouchedSince(organizationId, repositoryIds, from);
        return new Snapshot(repositories, pullRequests, commitRepository
                .findMessagesByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(organizationId, repositoryIds, from, to),
                List.of(), chosenBranches(organizationId, projectId, repositories));
    }

    /**
     * Repositories and the pull requests a window starting at {@code since} reads, without commits
     * nor reviews — for a view that only counts commits ({@link #commitCounts}).
     */
    public Snapshot loadPullRequests(String organizationId, String projectId, Instant since) {
        List<VcsRepository> repositories = vcsReadPort.repositoriesOfProject(organizationId, projectId);
        if (repositories.isEmpty()) return Snapshot.EMPTY;
        List<String> repositoryIds = repositories.stream().map(VcsRepository::getId).toList();
        return new Snapshot(repositories, vcsReadPort.pullRequestsTouchedSince(organizationId, repositoryIds, since),
                List.of(), List.of(), chosenBranches(organizationId, projectId, repositories));
    }

    /** Repository id to the branches chosen on its binding: what tells a promotion pull request from a stalled one. */
    private Map<String, Set<String>> chosenBranches(String organizationId, String projectId,
                                                    List<VcsRepository> repositories) {
        return StalePullRequestPolicy.chosenBranchesByRepository(
                vcsReadPort.bindingsOfProject(organizationId, projectId), repositories);
    }

    /** Active commits authored in {@code [from, to)} per repository, counted in the database (indexed). */
    public Map<String, Long> commitCounts(String organizationId, List<VcsRepository> repositories,
                                          Instant from, Instant to) {
        return repositories.stream().filter(r -> r.getId() != null).collect(Collectors.toMap(VcsRepository::getId,
                r -> commitRepository.countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(
                        organizationId, r.getId(), from, to), Long::sum));
    }

    /**
     * Human, non-deleted reviews submitted in {@code [from, to)} on pull requests that were open at
     * some point of the window (created before its end, not closed before its start).
     */
    private List<VcsReviewEvent> reviews(String organizationId, List<VcsPullRequest> pullRequests,
                                         Instant from, Instant to) {
        List<String> candidates = pullRequests.stream()
                .filter(pr -> pr.getCreatedAt() != null && pr.getCreatedAt().isBefore(to))
                .filter(pr -> closedAt(pr) == null || !closedAt(pr).isBefore(from))
                .map(VcsPullRequest::getId)
                .toList();
        if (candidates.isEmpty()) return List.of();
        return vcsReadPort.reviewEvents(organizationId, candidates).stream()
                .filter(e -> !e.isBot() && !e.isDeleted())
                .filter(e -> e.getOccurredAt() != null && !e.getOccurredAt().isBefore(from) && e.getOccurredAt().isBefore(to))
                .toList();
    }

    private static Instant closedAt(VcsPullRequest pr) {
        return pr.getMergedAt() != null ? pr.getMergedAt() : pr.getClosedAt();
    }

    public record Snapshot(List<VcsRepository> repositories, List<VcsPullRequest> pullRequests,
                           List<VcsCommit> commits, List<VcsReviewEvent> reviews,
                           Map<String, Set<String>> chosenBranches) {

        static final Snapshot EMPTY = new Snapshot(List.of(), List.of(), List.of(), List.of(), Map.of());

        public Map<String, String> repositoryNames() {
            return repositories.stream().filter(r -> r.getId() != null)
                    .collect(Collectors.toMap(VcsRepository::getId,
                            r -> r.getDisplayName() != null ? r.getDisplayName() : r.getId(), (a, b) -> a));
        }
    }
}
