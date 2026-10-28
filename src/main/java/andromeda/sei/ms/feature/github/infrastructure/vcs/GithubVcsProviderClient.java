package andromeda.sei.ms.feature.github.infrastructure.vcs;

import andromeda.sei.ms.feature.github.domain.GithubRepoFullName;
import andromeda.sei.ms.feature.github.infrastructure.client.GhCommitDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhPullRequestDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GhRepoDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.github.infrastructure.client.GhBranchDto;
import andromeda.sei.ms.feature.vcs.application.port.VcsBindableRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoIdInvalidException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsBranchNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * GitHub implementation of {@link VcsProviderClient} over the existing {@link GithubApiClient}
 * (retry, rate limits and conditional-request cache included). A GitHub repository is addressed by
 * its numeric id when known ({@code GET /repositories/{id}}, stable across renames) and by its
 * {@code owner/name} display name for every other endpoint.
 */
@Component
@RequiredArgsConstructor
public class GithubVcsProviderClient implements VcsProviderClient {

    /** Page cap of the bindable listing: a UI affordance, not a sync. */
    static final int MAX_BINDABLE_PAGES = 5;
    private static final String BEARER = "Bearer ";
    private static final String CLOSED_STATE = "closed";

    private final GithubApiClient githubApiClient;

    @Override
    public VcsProvider provider() {
        return VcsProvider.GITHUB;
    }

    @Override
    public List<VcsBindableRepository> listBindableRepositories(VcsCredentials credentials) {
        return githubApiClient.getUserRepos(auth(credentials), MAX_BINDABLE_PAGES).stream()
                .filter(dto -> dto.fullName() != null)
                .map(dto -> new VcsBindableRepository(String.valueOf(dto.id()), dto.fullName(), null, null,
                        dto.defaultBranch()))
                .toList();
    }

    @Override
    public VcsRemoteRepository resolveRepository(VcsCredentials credentials, String displayName) {
        GithubRepoFullName fullName = GithubRepoFullName.parseOrNull(displayName);
        if (fullName == null) throw new RepoIdInvalidException(displayName);
        return toRemote(githubApiClient.getRepo(fullName.owner(), fullName.name(), auth(credentials)));
    }

    @Override
    public VcsRemoteRepository getRepository(VcsCredentials credentials, VcsRepository repository) {
        if (repository.isPendingFirstSync()) {
            return resolveRepository(credentials, repository.getDisplayName());
        }
        return toRemote(githubApiClient.getRepoById(repository.getExternalRepoId(), auth(credentials)));
    }

    /** Branches of the repository: the default first, the rest by name, at most {@code MAX_BRANCHES_LISTED + 1} read. */
    @Override
    public List<VcsBranch> listBranches(VcsCredentials credentials, VcsRepository repository) {
        GithubRepoFullName name = nameOf(repository);
        String defaultBranch = repository.getDefaultBranch();
        return githubApiClient.getBranches(name.owner(), name.name(), auth(credentials), MAX_BRANCHES_LISTED + 1)
                .stream()
                .map(GhBranchDto::name)
                .filter(branch -> branch != null && !branch.isBlank())
                .distinct()
                .map(branch -> new VcsBranch(branch, branch.equals(defaultBranch)))
                .sorted(Comparator.comparing(VcsBranch::isDefault).reversed()
                        .thenComparing(VcsBranch::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** {@code GET commits?sha=branch}; GitHub answers 404 or 422 for a branch that no longer exists. */
    @Override
    public List<VcsRemoteCommit> commitsSince(VcsCredentials credentials, VcsRepository repository, String branch,
                                              Instant since) {
        GithubRepoFullName name = nameOf(repository);
        List<GhCommitDto> commits;
        try {
            commits = githubApiClient.getCommits(name.owner(), name.name(), auth(credentials), branch, since);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (branch != null && (status == 404 || status == 422)) throw new VcsBranchNotFoundException(branch, e);
            throw e;
        }
        return commits.stream().map(GithubVcsProviderClient::toRemote).toList();
    }

    @Override
    public List<VcsRemotePullRequest> pullRequestsChangedSince(VcsCredentials credentials, VcsRepository repository,
                                                               Instant watermark, Instant backfillFrom) {
        GithubRepoFullName name = nameOf(repository);
        List<GhPullRequestDto> prs = watermark == null && backfillFrom != null
                ? githubApiClient.getPullRequestsCreatedSince(name.owner(), name.name(), auth(credentials), backfillFrom)
                : githubApiClient.getPullRequests(name.owner(), name.name(), auth(credentials), watermark);
        return prs.stream().map(GithubVcsProviderClient::toRemote).toList();
    }

    @Override
    public List<VcsReviewSignal> reviewSignals(VcsCredentials credentials, VcsRepository repository,
                                               VcsPullRequest pullRequest) {
        GithubRepoFullName name = nameOf(repository);
        return githubApiClient.getReviews(name.owner(), name.name(), pullRequest.getNumber(), auth(credentials))
                .stream()
                .map(GithubReviewEventMapper::toSignal)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public List<VcsRemoteCommit> pullRequestCommits(VcsCredentials credentials, VcsRepository repository,
                                                    VcsPullRequest pullRequest) {
        GithubRepoFullName name = nameOf(repository);
        return githubApiClient.getPullRequestCommits(name.owner(), name.name(), pullRequest.getNumber(),
                        auth(credentials))
                .stream()
                .map(GithubVcsProviderClient::toRemote)
                .toList();
    }

    /** GitHub has no native pull request ↔ work item link: text extraction is the only source. */
    @Override
    public List<String> nativeWorkItemLinks(VcsCredentials credentials, VcsRepository repository,
                                            VcsRemotePullRequest pullRequest) {
        return List.of();
    }

    private static String auth(VcsCredentials credentials) {
        return BEARER + credentials.token();
    }

    private static GithubRepoFullName nameOf(VcsRepository repository) {
        GithubRepoFullName name = GithubRepoFullName.parseOrNull(repository.getDisplayName());
        if (name == null) throw new RepoIdInvalidException(repository.getDisplayName());
        return name;
    }

    private static VcsRemoteRepository toRemote(GhRepoDto dto) {
        return new VcsRemoteRepository(String.valueOf(dto.id()), dto.fullName(), null, dto.defaultBranch(),
                GithubInstants.parse(dto.updatedAt()));
    }

    static VcsRemoteCommit toRemote(GhCommitDto dto) {
        GhCommitDto.CommitDetailDto detail = dto.commit();
        return new VcsRemoteCommit(
                dto.sha(),
                detail != null ? detail.message() : null,
                dto.author() != null ? dto.author().login() : null,
                dto.author() != null && dto.author().id() != null ? String.valueOf(dto.author().id()) : null,
                detail != null && detail.author() != null ? GithubInstants.parse(detail.author().date()) : null);
    }

    static VcsRemotePullRequest toRemote(GhPullRequestDto dto) {
        Instant mergedAt = GithubInstants.parse(dto.mergedAt());
        return new VcsRemotePullRequest(
                String.valueOf(dto.id()),
                dto.number(),
                dto.title(),
                dto.body(),
                statusOf(dto.state(), mergedAt),
                dto.user() != null ? dto.user().login() : null,
                dto.user() != null && dto.user().id() != null ? String.valueOf(dto.user().id()) : null,
                GithubInstants.parse(dto.createdAt()),
                GithubInstants.parse(dto.updatedAt()),
                mergedAt,
                GithubInstants.parse(dto.closedAt()),
                dto.head() != null ? dto.head().ref() : null,
                dto.base() != null ? dto.base().ref() : null,
                Boolean.TRUE.equals(dto.draft()),
                dto.labels() == null ? List.of() : dto.labels().stream()
                        .map(GhPullRequestDto.LabelDto::name).filter(Objects::nonNull).toList());
    }

    private static PullRequestStatus statusOf(String state, Instant mergedAt) {
        if (mergedAt != null) return PullRequestStatus.MERGED;
        if (CLOSED_STATE.equalsIgnoreCase(state)) return PullRequestStatus.CLOSED;
        return PullRequestStatus.OPEN;
    }
}
