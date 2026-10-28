package andromeda.sei.ms.feature.azure.infrastructure.vcs;

import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureCredentialRejectedException;
import andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzProjectRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzCommitDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestQuery;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzRepositoryDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzWorkItemRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsAccessProbeAdapter;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureReposApiClient;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.VcsSettings;
import andromeda.sei.ms.feature.vcs.application.port.VcsBindableRepository;
import andromeda.sei.ms.feature.vcs.application.port.VcsBranch;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderClient;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteCommit;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemotePullRequest;
import andromeda.sei.ms.feature.vcs.application.port.VcsRemoteRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.vcs.domain.exception.VcsBranchNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tasks 5.1–5.3: the Azure Repos implementation of the provider client port. */
@ExtendWith(MockitoExtension.class)
class AzureReposVcsProviderClientTest {

    private static final String ORG = "org-1";
    private static final String ORG_URL = "https://dev.azure.com/acme";
    private static final String AUTH = "Basic OnBhdA==";
    private static final VcsCredentials CREDENTIALS =
            new VcsCredentials(VcsProvider.AZURE_REPOS, ORG, AUTH, false, ORG_URL);
    private static final AzProjectRefDto PAYMENTS = new AzProjectRefDto("p-pay", "Payments");
    private static final AzProjectRefDto ATLAS = new AzProjectRefDto("p-atl", "Atlas");

    @Mock private AzureReposApiClient apiClient;
    @Mock private AzureDevOpsAccessProbeAdapter accessProbe;
    @Mock private AzureReposAccessRecorder accessRecorder;
    @Mock private OrganizationRepository organizationRepository;

    private AzureReposVcsProviderClient client;

    @BeforeEach
    void setUp() {
        client = new AzureReposVcsProviderClient(apiClient, accessProbe, accessRecorder, organizationRepository);
    }

    @Test
    void shouldListRepositoriesGroupedByProjectWithBindableFlags() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenReturn(List.of(
                new AzRepositoryDto("r-3", "web", "refs/heads/main", false, PAYMENTS),
                new AzRepositoryDto("r-2", "empty", null, false, ATLAS),
                new AzRepositoryDto("r-1", "legacy", "refs/heads/master", true, ATLAS),
                new AzRepositoryDto("r-4", "api", "refs/heads/main", null, PAYMENTS)));

        List<VcsBindableRepository> repositories = client.listBindableRepositories(CREDENTIALS);

        assertThat(repositories).extracting(VcsBindableRepository::displayName)
                .containsExactly("empty", "legacy", "api", "web");
        assertThat(repositories).extracting(VcsBindableRepository::bindable).containsExactly(false, false, true, true);
        assertThat(repositories.get(2)).satisfies(api -> {
            assertThat(api.externalRepoId()).isEqualTo("r-4");
            assertThat(api.externalProjectId()).isEqualTo("p-pay");
            assertThat(api.externalProjectName()).isEqualTo("Payments");
            assertThat(api.defaultBranch()).isEqualTo("main");
        });
        verify(accessRecorder).record(ORG, true);
    }

    @Test
    void shouldReportMissingCodeScopeWhenProjectsAreStillReadable() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenThrow(http(401));
        when(accessProbe.projects(ORG_URL, AUTH)).thenReturn(AzureProjectsAccess.GRANTED);

        assertThatThrownBy(() -> client.listBindableRepositories(CREDENTIALS))
                .isInstanceOf(AzureReposScopeMissingException.class)
                .hasFieldOrPropertyWithValue("code", "AZURE_REPOS_SCOPE_MISSING");
        verify(accessRecorder).record(ORG, false);
    }

    @Test
    void shouldReportARejectedCredentialWhenNothingIsReadable() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenThrow(http(403));
        when(accessProbe.projects(ORG_URL, AUTH)).thenReturn(AzureProjectsAccess.DENIED);

        assertThatThrownBy(() -> client.verifyAccess(CREDENTIALS)).isInstanceOf(AzureCredentialRejectedException.class);
        verify(accessRecorder, never()).record(any(), eq(false));
    }

    @Test
    void shouldLetOtherHttpFailuresThrough() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenThrow(http(500));

        assertThatThrownBy(() -> client.verifyAccess(CREDENTIALS)).isInstanceOf(WebClientResponseException.class);
    }

    @Test
    void shouldGetARepositoryByGuidUnderItsProject() {
        when(apiClient.getRepository(ORG_URL, "p-pay", "r-3", AUTH))
                .thenReturn(new AzRepositoryDto("r-3", "web", "refs/heads/main", false, PAYMENTS));

        VcsRemoteRepository remote = client.getRepository(CREDENTIALS, repository());

        assertThat(remote.externalRepoId()).isEqualTo("r-3");
        assertThat(remote.externalProjectId()).isEqualTo("p-pay");
        assertThat(remote.defaultBranch()).isEqualTo("main");
    }

    @Test
    void shouldTranslateAnUnknownRepository() {
        when(apiClient.getRepository(ORG_URL, "p-pay", "r-3", AUTH)).thenThrow(http(404));

        assertThatThrownBy(() -> client.getRepository(CREDENTIALS, repository()))
                .isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void shouldResolveABareNameOnlyWhenUnique() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenReturn(List.of(
                new AzRepositoryDto("r-1", "web", "refs/heads/main", false, PAYMENTS),
                new AzRepositoryDto("r-2", "web", "refs/heads/main", false, ATLAS),
                new AzRepositoryDto("r-3", "api", "refs/heads/main", false, ATLAS)));

        assertThat(client.resolveRepository(CREDENTIALS, "API").externalRepoId()).isEqualTo("r-3");
        assertThatThrownBy(() -> client.resolveRepository(CREDENTIALS, "web")).isInstanceOf(RepoNotFoundException.class);
    }

    @Test
    void shouldQueryActiveAndClosedSinceTheWatermark() {
        Instant watermark = Instant.parse("2026-09-29T00:00:00Z");

        assertThat(AzureReposVcsProviderClient.queries(watermark, Instant.parse("2026-01-01T00:00:00Z")))
                .containsExactly(AzPullRequestQuery.active(),
                        AzPullRequestQuery.closedSince("completed", watermark),
                        AzPullRequestQuery.closedSince("abandoned", watermark));
    }

    @Test
    void shouldQueryActiveAndCreatedWithinTheBackfillOnFirstSync() {
        Instant backfill = Instant.parse("2026-07-01T00:00:00Z");

        assertThat(AzureReposVcsProviderClient.queries(null, backfill))
                .containsExactly(AzPullRequestQuery.active(), AzPullRequestQuery.createdSince(backfill));
        assertThat(AzureReposVcsProviderClient.queries(null, null))
                .containsExactly(new AzPullRequestQuery("all", null, null));
    }

    @Test
    void shouldMergeThePullRequestListingsWithoutDuplicates() {
        Instant backfill = Instant.parse("2026-07-01T00:00:00Z");
        AzPullRequestDto active = pr(22, "active", null);
        AzPullRequestDto merged = pr(20, "completed", "2026-09-10T10:00:00Z");
        when(apiClient.listPullRequests(eq(ORG_URL), eq("p-pay"), eq("r-3"), eq(AzPullRequestQuery.active()), eq(AUTH)))
                .thenReturn(List.of(active));
        when(apiClient.listPullRequests(eq(ORG_URL), eq("p-pay"), eq("r-3"),
                eq(AzPullRequestQuery.createdSince(backfill)), eq(AUTH))).thenReturn(List.of(active, merged));

        List<VcsRemotePullRequest> pullRequests = client.pullRequestsChangedSince(CREDENTIALS, repository(), null, backfill);

        assertThat(pullRequests).extracting(VcsRemotePullRequest::externalId).containsExactly("22", "20");
        assertThat(pullRequests.get(1).status()).isEqualTo(PullRequestStatus.MERGED);
    }

    @Test
    void shouldNotReadCommitsOfAnEmptyRepository() {
        VcsRepository empty = repository();
        empty.setDefaultBranch(null);

        assertThat(client.commitsSince(CREDENTIALS, empty, null, null)).isEmpty();
        verify(apiClient, never()).listCommits(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReadCommitsOfTheRequestedBranch() {
        Instant since = Instant.parse("2026-09-01T00:00:00Z");
        when(apiClient.listCommits(ORG_URL, "p-pay", "r-3", "release/1", since, AUTH)).thenReturn(List.of(
                new AzCommitDto("abc", new AzCommitDto.GitUser("n", "e@x", "2026-09-02T00:00:00Z"), "msg")));

        assertThat(client.commitsSince(CREDENTIALS, repository(), "release/1", since)).extracting(VcsRemoteCommit::sha)
                .containsExactly("abc");
    }

    @Test
    void shouldRaiseBranchNotFoundWhenTheProviderAnswers404() {
        when(apiClient.listCommits(any(), any(), any(), eq("gone"), any(), any())).thenThrow(
                WebClientResponseException.create(404, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8));

        assertThatThrownBy(() -> client.commitsSince(CREDENTIALS, repository(), "gone", null))
                .isInstanceOf(VcsBranchNotFoundException.class)
                .extracting("branch").isEqualTo("gone");
    }

    @Test
    void shouldListBranchesWithTheDefaultFirstAndShortNames() {
        when(apiClient.listBranchRefs(ORG_URL, "p-pay", "r-3", VcsProviderClient.MAX_BRANCHES_LISTED + 1, AUTH))
                .thenReturn(List.of(new AzRefDto("refs/heads/feature/x", "1"), new AzRefDto("refs/heads/main", "2"),
                        new AzRefDto("refs/heads/develop", "3"), new AzRefDto(null, "4")));

        List<VcsBranch> branches = client.listBranches(CREDENTIALS, repository());

        assertThat(branches).extracting(VcsBranch::name).containsExactly("main", "develop", "feature/x");
        assertThat(branches).extracting(VcsBranch::isDefault).containsExactly(true, false, false);
    }

    @Test
    void shouldReturnNativeWorkItemLinks() {
        when(apiClient.listPullRequestWorkItems(ORG_URL, "p-pay", "r-3", 22, AUTH))
                .thenReturn(List.of(new AzWorkItemRefDto("1234", "u"), new AzWorkItemRefDto(null, "u")));
        VcsRemotePullRequest remote = AzurePullRequestMapper.map(pr(22, "active", null), Instant.now());

        assertThat(client.nativeWorkItemLinks(CREDENTIALS, repository(), remote)).containsExactly("1234");
    }

    @Test
    void shouldFlagSignalsOfTheOrganizationServiceIdentities() throws Exception {
        Organization organization = mock(Organization.class);
        when(organization.getVcsSettings()).thenReturn(new VcsSettings(null, List.of("fabrikamfiber16@hotmail.com")));
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(organization));
        when(apiClient.listThreads(ORG_URL, "p-pay", "r-3", 22, AUTH))
                .thenReturn(AzureThreadReviewSignalMapperTest.officialThreads());
        VcsPullRequest pullRequest = new VcsPullRequest("pr-1", ORG, "repo-1", VcsProvider.AZURE_REPOS, "22", 22,
                "t", PullRequestStatus.OPEN);

        assertThat(client.reviewSignals(CREDENTIALS, repository(), pullRequest))
                .filteredOn(signal -> signal.externalEventId().contains(":"))
                .allSatisfy(signal -> assertThat(signal.bot()).isTrue());
    }

    private static VcsRepository repository() {
        VcsRepository repository = new VcsRepository("repo-1", ORG, VcsProvider.AZURE_REPOS, "r-3", "web");
        repository.setExternalProjectId("p-pay");
        repository.setDefaultBranch("main");
        return repository;
    }

    private static AzPullRequestDto pr(int id, String status, String closedDate) {
        return new AzPullRequestDto(id, status, null, "2026-09-01T10:00:00Z", closedDate, "t" + id, null,
                "refs/heads/f" + id, "refs/heads/main", false, null);
    }

    private static WebClientResponseException http(int status) {
        return WebClientResponseException.create(status, "status " + status, new HttpHeaders(), new byte[0],
                StandardCharsets.UTF_8);
    }
}
