package andromeda.sei.ms.feature.vcs;

import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsAttribution;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsCommitRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestPage;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepositoryRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * In-memory implementation of the five neutral repository ports with the query semantics of the
 * Mongo adapters (tenant scoping, active-only lists, half-open windows). Used by the parity test to
 * run the real use cases and dashboards over migrated data without a database.
 */
public class InMemoryVcsStore {

    public final List<VcsRepository> repositories = new ArrayList<>();
    public final List<VcsRepoBinding> bindings = new ArrayList<>();
    public final List<VcsPullRequest> pullRequests = new ArrayList<>();
    public final List<VcsCommit> commits = new ArrayList<>();
    public final List<VcsReviewEvent> events = new ArrayList<>();

    public final Repositories repositoryRepository = new Repositories();

    public class Repositories implements VcsRepositoryRepository {

    @Override
    public Optional<VcsRepository> findByIdAndOrganizationId(String id, String organizationId) {
        return first(repositories, r -> r.getId().equals(id) && r.getOrganizationId().equals(organizationId));
    }

    @Override
    public Optional<VcsRepository> findByOrganizationIdAndProviderAndExternalRepoId(String organizationId,
                                                                                    VcsProvider provider,
                                                                                    String externalRepoId) {
        return first(repositories, r -> r.getOrganizationId().equals(organizationId) && r.getProvider() == provider
                && Objects.equals(r.getExternalRepoId(), externalRepoId));
    }

    @Override
    public Optional<VcsRepository> findByOrganizationIdAndProviderAndDisplayName(String organizationId,
                                                                                 VcsProvider provider,
                                                                                 String displayName) {
        return first(repositories, r -> r.getOrganizationId().equals(organizationId) && r.getProvider() == provider
                && Objects.equals(r.getDisplayName(), displayName) && r.isActive());
    }

    @Override
    public List<VcsRepository> findAllByOrganizationIdAndIdIn(String organizationId, Collection<String> ids) {
        return all(repositories, r -> r.getOrganizationId().equals(organizationId) && ids.contains(r.getId()) && r.isActive());
    }

    @Override
    public List<VcsRepository> findAllByOrganizationIdAndProvider(String organizationId, VcsProvider provider) {
        return all(repositories, r -> r.getOrganizationId().equals(organizationId) && r.getProvider() == provider
                && r.isActive());
    }

    @Override
    public VcsRepository save(VcsRepository repository) {
        repositories.removeIf(r -> r.getId().equals(repository.getId()));
        repositories.add(repository);
        return repository;
    }

    }

    public final Bindings bindingRepository = new Bindings();

    public class Bindings implements VcsRepoBindingRepository {

    @Override
    public Optional<VcsRepoBinding> findByIdAndOrganizationIdAndActiveTrue(String id, String organizationId) {
        return first(bindings, b -> b.getId().equals(id) && b.getOrganizationId().equals(organizationId) && b.isActive());
    }

    @Override
    public Optional<VcsRepoBinding> findActiveByOrganizationIdAndRepositoryId(String organizationId, String repositoryId) {
        return first(bindings, b -> b.getOrganizationId().equals(organizationId)
                && b.getRepositoryId().equals(repositoryId) && b.isActive());
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationId(String organizationId) {
        return all(bindings, b -> b.getOrganizationId().equals(organizationId) && b.isActive());
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationIdAndProjectId(String organizationId, String projectId) {
        return all(bindings, b -> b.getOrganizationId().equals(organizationId) && b.getProjectId().equals(projectId)
                && b.isActive());
    }

    @Override
    public List<VcsRepoBinding> findAllActiveByOrganizationIdAndProvider(String organizationId, VcsProvider provider) {
        return all(bindings, b -> b.getOrganizationId().equals(organizationId) && b.getProvider() == provider
                && b.isActive());
    }

    @Override
    public List<VcsRepoBinding> findAllSyncEnabledByOrganizationIdAndProvider(String organizationId,
                                                                              VcsProvider provider) {
        return all(bindings, b -> b.getOrganizationId().equals(organizationId) && b.getProvider() == provider
                && b.isActive() && b.isSyncEnabled());
    }

    @Override
    public VcsRepoBinding save(VcsRepoBinding binding) {
        bindings.removeIf(b -> b.getId().equals(binding.getId()));
        bindings.add(binding);
        return binding;
    }

    @Override
    public void saveAll(List<VcsRepoBinding> toSave) {
        toSave.forEach(this::save);
    }

    }

    public final PullRequests pullRequestRepository = new PullRequests();

    public class PullRequests implements VcsPullRequestRepository {

    @Override
    public Optional<VcsPullRequest> findByOrganizationIdAndRepositoryIdAndExternalId(String organizationId,
                                                                                     String repositoryId,
                                                                                     String externalId) {
        return first(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && pr.getRepositoryId().equals(repositoryId) && pr.getExternalId().equals(externalId));
    }

    @Override
    public Optional<VcsPullRequest> findByIdAndOrganizationId(String id, String organizationId) {
        return first(pullRequests, pr -> pr.getId().equals(id) && pr.getOrganizationId().equals(organizationId));
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryId(String organizationId, String repositoryId) {
        return all(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && pr.getRepositoryId().equals(repositoryId));
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndStatus(String organizationId,
                                                                                  Collection<String> repositoryIds,
                                                                                  PullRequestStatus status) {
        return all(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && repositoryIds.contains(pr.getRepositoryId()) && pr.getStatus() == status && pr.isActive());
    }

    @Override
    public List<VcsPullRequest> findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        return all(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && repositoryIds.contains(pr.getRepositoryId()) && pr.getStatus() == PullRequestStatus.MERGED
                && halfOpen(pr.getMergedAt(), from, to) && pr.isActive());
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        return all(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && repositoryIds.contains(pr.getRepositoryId()) && halfOpen(pr.getCreatedAt(), from, to) && pr.isActive());
    }

    @Override
    public List<VcsPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
            String organizationId, String authorUserId, Instant from, Instant to) {
        return all(pullRequests, pr -> pr.getOrganizationId().equals(organizationId)
                && Objects.equals(pr.getAuthorUserId(), authorUserId) && halfOpen(pr.getCreatedAt(), from, to));
    }

    @Override
    public VcsPullRequestPage findPageByRepository(String organizationId, String repositoryId, PullRequestStatus status,
                                                   String authorLogin, int page, int size) {
        List<VcsPullRequest> matching = pullRequests.stream()
                .filter(pr -> pr.getOrganizationId().equals(organizationId) && pr.getRepositoryId().equals(repositoryId)
                        && pr.isActive())
                .filter(pr -> status == null || pr.getStatus() == status)
                .filter(pr -> authorLogin == null || authorLogin.equals(pr.getAuthorLogin()))
                .sorted(Comparator.comparing(VcsPullRequest::getCreatedAt).reversed())
                .toList();
        List<VcsPullRequest> content = matching.stream().skip((long) page * size).limit(size).toList();
        return new VcsPullRequestPage(content, page, size, matching.size());
    }

    @Override
    public VcsPullRequest save(VcsPullRequest pullRequest) {
        pullRequests.removeIf(pr -> pr.getId().equals(pullRequest.getId()));
        pullRequests.add(pullRequest);
        return pullRequest;
    }

    @Override
    public VcsAttribution attributeByAuthorExternalIds(String organizationId, VcsProvider provider,
                                                       Collection<String> externalIds, String userId) {
        throw new UnsupportedOperationException("covered by the Mongo adapter test");
    }

    @Override
    public void updateMetrics(String organizationId, String id, Double cycleTimeHours, Double timeToFirstReviewHours,
                              Double timeToFirstApprovalHours) {
        findByIdAndOrganizationId(id, organizationId).ifPresent(pr -> {
            pr.setCycleTimeHours(cycleTimeHours);
            pr.setTimeToFirstReviewHours(timeToFirstReviewHours);
            pr.setTimeToFirstApprovalHours(timeToFirstApprovalHours);
        });
    }

    }

    public final Commits commitRepository = new Commits();

    public class Commits implements VcsCommitRepository {

    @Override
    public Optional<VcsCommit> findByOrganizationIdAndRepositoryIdAndSha(String organizationId, String repositoryId,
                                                                        String sha) {
        return first(commits, c -> c.getOrganizationId().equals(organizationId)
                && c.getRepositoryId().equals(repositoryId) && c.getSha().equals(sha));
    }

    @Override
    public long countByOrganizationIdAndRepositoryIdAndAuthoredAtBetween(String organizationId, String repositoryId,
                                                                         Instant from, Instant to) {
        return commits.stream().filter(c -> c.getOrganizationId().equals(organizationId)
                && c.getRepositoryId().equals(repositoryId) && halfOpen(c.getAuthoredAt(), from, to) && c.isActive()).count();
    }

    @Override
    public List<VcsCommit> findAllByOrganizationIdAndRepositoryIdInAndAuthoredAtBetween(
            String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
        return commits.stream().filter(c -> c.getOrganizationId().equals(organizationId)
                && repositoryIds.contains(c.getRepositoryId()) && halfOpen(c.getAuthoredAt(), from, to)
                && c.isActive()).toList();
    }

    @Override
    public VcsCommit save(VcsCommit commit) {
        commits.add(commit);
        return commit;
    }

    @Override
    public long attributeByAuthorExternalIds(String organizationId, VcsProvider provider,
                                             Collection<String> externalIds, String userId) {
        throw new UnsupportedOperationException("covered by the Mongo adapter test");
    }

    @Override
    public long attributeByLegacyAuthorLogin(String organizationId, VcsProvider provider, String authorLogin,
                                             String userId) {
        throw new UnsupportedOperationException("covered by the Mongo adapter test");
    }

    @Override
    public long attributeByAuthorEmails(String organizationId, VcsProvider provider, Collection<String> emails,
                                        String userId) {
        throw new UnsupportedOperationException("covered by the Mongo adapter test");
    }

    }

    public final Events reviewEventRepository = new Events();

    public class Events implements VcsReviewEventRepository {

    @Override
    public Optional<VcsReviewEvent> findByOrganizationIdAndPullRequestIdAndExternalEventId(String organizationId,
                                                                                          String pullRequestId,
                                                                                          String externalEventId) {
        return first(events, e -> e.getOrganizationId().equals(organizationId)
                && e.getPullRequestId().equals(pullRequestId) && e.getExternalEventId().equals(externalEventId));
    }

    @Override
    public List<VcsReviewEvent> findAllByOrganizationIdAndPullRequestIdIn(String organizationId,
                                                                          Collection<String> pullRequestIds) {
        return all(events, e -> e.getOrganizationId().equals(organizationId)
                && pullRequestIds.contains(e.getPullRequestId()) && e.isActive());
    }

    @Override
    public VcsReviewEvent save(VcsReviewEvent event) {
        events.add(event);
        return event;
    }

    @Override
    public VcsAttribution attributeByActorExternalIds(String organizationId, VcsProvider provider,
                                                      Collection<String> externalIds, String userId) {
        throw new UnsupportedOperationException("covered by the Mongo adapter test");
    }

    }

    private static boolean halfOpen(Instant at, Instant from, Instant to) {
        return at != null && !at.isBefore(from) && at.isBefore(to);
    }

    private static <T> Optional<T> first(List<T> items, Predicate<T> filter) {
        return items.stream().filter(filter).findFirst();
    }

    private static <T> List<T> all(List<T> items, Predicate<T> filter) {
        return items.stream().filter(filter).toList();
    }
}
