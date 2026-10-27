package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureCredentialRejectedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommitDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestQuery;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzRepositoryDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzWorkItemRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsAccessProbeAdapter;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureReposApiClient;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.vcs.application.port.VcsBindableRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsReviewSignal;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsBranchNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link VcsProviderClient} for Azure Repos (azure-repos-provider D1–D3, D6, D8) over
 * {@link AzureReposApiClient}. {@code credentials.token()} is the Authorization header and
 * {@code credentials.baseUrl()} the organization URL ({@link AzureReposCredentialsProvider}).
 *
 * <p><b>Denials.</b> Any 401/403 from a Git endpoint is disambiguated with one project-list call:
 * projects readable → the credential lacks Code (Read), {@code 403 AZURE_REPOS_SCOPE_MISSING} and
 * {@code reposAccess = false}; projects denied too → the credential itself is dead,
 * {@code AZURE_CREDENTIAL_REJECTED}. Boards is never affected by either.
 *
 * <p><b>Pull requests (D2).</b> There is no "updated since" filter: with a watermark, every
 * {@code active} pull request plus {@code completed}/{@code abandoned} ones closed since the
 * watermark ({@code queryTimeRangeType=closed}); without one, active ones plus everything created
 * since the backfill start; with neither, the whole history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureReposVcsProviderClient implements VcsProviderClient {

    private final AzureReposApiClient apiClient;
    private final AzureDevOpsAccessProbeAdapter accessProbe;
    private final AzureReposAccessRecorder accessRecorder;
    private final OrganizationRepository organizationRepository;

    @Override
    public VcsProvider provider() {
        return VcsProvider.AZURE_REPOS;
    }

    @Override
    public void verifyAccess(VcsCredentials credentials) {
        listRepositories(credentials);
    }

    /** All repositories of the organization, grouped by ADO project (sorted by project, then name). */
    @Override
    public List<VcsBindableRepository> listBindableRepositories(VcsCredentials credentials) {
        return listRepositories(credentials).stream()
                .filter(repo -> repo.id() != null)
                .sorted(Comparator.comparing((AzRepositoryDto repo) -> projectName(repo), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(repo -> Objects.toString(repo.name(), ""), String.CASE_INSENSITIVE_ORDER))
                .map(AzureReposVcsProviderClient::toBindable)
                .toList();
    }

    /** Azure Repos binds by GUID; a bare name is accepted only when it is unique in the organization. */
    @Override
    public VcsRemoteRepository resolveRepository(VcsCredentials credentials, String displayName) {
        List<AzRepositoryDto> matches = listRepositories(credentials).stream()
                .filter(repo -> repo.name() != null && repo.name().equalsIgnoreCase(displayName))
                .toList();
        if (matches.size() != 1) throw new RepoNotFoundException(displayName);
        return toRemote(matches.getFirst());
    }

    @Override
    public VcsRemoteRepository getRepository(VcsCredentials credentials, VcsRepository repository) {
        if (repository.isPendingFirstSync()) return resolveRepository(credentials, repository.getDisplayName());
        try {
            AzRepositoryDto dto = guarded(credentials, () -> apiClient.getRepository(credentials.baseUrl(),
                    repository.getExternalProjectId(), repository.getExternalRepoId(), credentials.token()));
            if (dto == null) throw new RepoNotFoundException(repository.getExternalRepoId());
            return toRemote(dto);
        } catch (WebClientResponseException.NotFound e) {
            throw new RepoNotFoundException(repository.getExternalRepoId());
        }
    }

    /** Branches of the repository: the default first, the rest by name, at most {@code MAX_BRANCHES_LISTED + 1} read. */
    @Override
    public List<VcsBranch> listBranches(VcsCredentials credentials, VcsRepository repository) {
        List<AzRefDto> refs = guarded(credentials, () -> apiClient.listBranchRefs(credentials.baseUrl(),
                repository.getExternalProjectId(), repository.getExternalRepoId(), MAX_BRANCHES_LISTED + 1,
                credentials.token()));
        String defaultBranch = repository.getDefaultBranch();
        return refs.stream()
                .map(ref -> AzureReposApiClient.shortBranch(ref.name()))
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .map(name -> new VcsBranch(name, name.equals(defaultBranch)))
                .sorted(Comparator.comparing(VcsBranch::isDefault).reversed()
                        .thenComparing(VcsBranch::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Commits of one branch authored since {@code since} (design D8); none when no branch is given (empty repository). */
    @Override
    public List<VcsRemoteCommit> commitsSince(VcsCredentials credentials, VcsRepository repository, String branch,
                                              Instant since) {
        if (branch == null || branch.isBlank()) return List.of();
        List<AzCommitDto> commits;
        try {
            commits = guarded(credentials, () -> apiClient.listCommits(credentials.baseUrl(),
                    repository.getExternalProjectId(), repository.getExternalRepoId(), branch, since,
                    credentials.token()));
        } catch (WebClientResponseException.NotFound e) {
            throw new VcsBranchNotFoundException(branch, e);
        }
        return commits.stream()
                .filter(commit -> commit.commitId() != null)
                .map(AzureCommitMapper::map)
                .filter(commit -> since == null || commit.authoredAt() == null || !commit.authoredAt().isBefore(since))
                .toList();
    }

    @Override
    public List<VcsRemotePullRequest> pullRequestsChangedSince(VcsCredentials credentials, VcsRepository repository,
                                                               Instant watermark, Instant backfillFrom) {
        Instant observedAt = Instant.now();
        Map<Integer, AzPullRequestDto> byId = new LinkedHashMap<>();
        for (AzPullRequestQuery query : queries(watermark, backfillFrom)) {
            guarded(credentials, () -> apiClient.listPullRequests(credentials.baseUrl(),
                    repository.getExternalProjectId(), repository.getExternalRepoId(), query, credentials.token()))
                    .stream()
                    .filter(pr -> pr.pullRequestId() != null)
                    .forEach(pr -> byId.putIfAbsent(pr.pullRequestId(), pr));
        }
        return byId.values().stream().map(pr -> AzurePullRequestMapper.map(pr, observedAt)).toList();
    }

    static List<AzPullRequestQuery> queries(Instant watermark, Instant backfillFrom) {
        if (watermark != null) {
            return List.of(AzPullRequestQuery.active(),
                    AzPullRequestQuery.closedSince(AzPullRequestQuery.COMPLETED, watermark),
                    AzPullRequestQuery.closedSince(AzPullRequestQuery.ABANDONED, watermark));
        }
        if (backfillFrom != null) {
            return List.of(AzPullRequestQuery.active(), AzPullRequestQuery.createdSince(backfillFrom));
        }
        return List.of(new AzPullRequestQuery(AzPullRequestQuery.ALL, null, null));
    }

    /** One thread call per pull request the sync considers changed (design D3). */
    @Override
    public List<VcsReviewSignal> reviewSignals(VcsCredentials credentials, VcsRepository repository,
                                               VcsPullRequest pullRequest) {
        int number = requireNumber(pullRequest.getNumber(), pullRequest.getExternalId());
        return AzureThreadReviewSignalMapper.map(
                guarded(credentials, () -> apiClient.listThreads(credentials.baseUrl(),
                        repository.getExternalProjectId(), repository.getExternalRepoId(), number, credentials.token())),
                serviceIdentities(credentials.organizationId()));
    }

    @Override
    public List<VcsRemoteCommit> pullRequestCommits(VcsCredentials credentials, VcsRepository repository,
                                                    VcsPullRequest pullRequest) {
        int number = requireNumber(pullRequest.getNumber(), pullRequest.getExternalId());
        return guarded(credentials, () -> apiClient.listPullRequestCommits(credentials.baseUrl(),
                repository.getExternalProjectId(), repository.getExternalRepoId(), number, credentials.token()))
                .stream()
                .filter(commit -> commit.commitId() != null)
                .map(AzureCommitMapper::map)
                .toList();
    }

    @Override
    public List<String> nativeWorkItemLinks(VcsCredentials credentials, VcsRepository repository,
                                            VcsRemotePullRequest pullRequest) {
        int number = requireNumber(pullRequest.number(), pullRequest.externalId());
        return guarded(credentials, () -> apiClient.listPullRequestWorkItems(credentials.baseUrl(),
                repository.getExternalProjectId(), repository.getExternalRepoId(), number, credentials.token()))
                .stream()
                .map(AzWorkItemRefDto::id)
                .filter(Objects::nonNull)
                .toList();
    }

    // ------------------------------------------------------------------

    /** Org-level repository list; also re-evaluates {@code reposAccess} (design D6). */
    private List<AzRepositoryDto> listRepositories(VcsCredentials credentials) {
        List<AzRepositoryDto> repositories = guarded(credentials,
                () -> apiClient.listRepositories(credentials.baseUrl(), credentials.token()));
        accessRecorder.record(credentials.organizationId(), true);
        return repositories;
    }

    private <T> T guarded(VcsCredentials credentials, Supplier<T> call) {
        try {
            return call.get();
        } catch (WebClientResponseException e) {
            if (!AzureDevOpsAccessProbeAdapter.isDenied(e)) throw e;
            throw denial(credentials);
        }
    }

    private RuntimeException denial(VcsCredentials credentials) {
        if (accessProbe.projects(credentials.baseUrl(), credentials.token()) == AzureProjectsAccess.GRANTED) {
            accessRecorder.record(credentials.organizationId(), false);
            return new AzureReposScopeMissingException();
        }
        return new AzureCredentialRejectedException();
    }

    private List<String> serviceIdentities(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(Organization::getVcsSettings)
                .map(VcsSettings::serviceIdentities)
                .orElse(List.of());
    }

    private static int requireNumber(Integer number, String externalId) {
        if (number != null) return number;
        try {
            return Integer.parseInt(externalId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Azure pull request without a numeric id: " + externalId);
        }
    }

    private static VcsBindableRepository toBindable(AzRepositoryDto repo) {
        String branch = AzureReposApiClient.shortBranch(repo.defaultBranch());
        boolean bindable = !repo.isDisabled() && branch != null && !branch.isBlank();
        return new VcsBindableRepository(repo.id(), repo.name(), repo.project() == null ? null : repo.project().id(),
                projectName(repo), branch, bindable);
    }

    private static VcsRemoteRepository toRemote(AzRepositoryDto repo) {
        return new VcsRemoteRepository(repo.id(), repo.name(), repo.project() == null ? null : repo.project().id(),
                AzureReposApiClient.shortBranch(repo.defaultBranch()), null);
    }

    private static String projectName(AzRepositoryDto repo) {
        return repo.project() == null || repo.project().name() == null ? "" : repo.project().name();
    }
}
