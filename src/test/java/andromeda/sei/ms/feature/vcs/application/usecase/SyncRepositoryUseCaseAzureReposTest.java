package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.azure.domain.AzureProjectsAccess;
import andromeda.sei.ms.feature.azure.domain.exception.AzureReposScopeMissingException;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzPullRequestQuery;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzRepositoryDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzProjectRefDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzThreadDto;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureDevOpsAccessProbeAdapter;
import andromeda.sei.ms.feature.azure.infrastructure.client.AzureReposApiClient;
import andromeda.sei.ms.feature.azure.infrastructure.vcs.AzureReposAccessRecorder;
import andromeda.sei.ms.feature.azure.infrastructure.vcs.AzureReposVcsProviderClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.vcs.InMemoryVcsStore;
import andromeda.sei.ms.feature.vcs.application.port.VcsAuthorResolver;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentials;
import andromeda.sei.ms.feature.vcs.application.port.VcsCredentialsProvider;
import andromeda.sei.ms.feature.vcs.application.port.VcsProviderRegistry;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.ReviewKind;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestPage;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequestRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.VcsReviewEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task 5.6: {@link SyncRepositoryUseCase} end to end through the real Azure Repos adapter, with
 * only the HTTP client mocked. The fake Azure DevOps honors the documented search criteria, so an
 * old completed pull request is filtered out by the server exactly as in production.
 */
@ExtendWith(MockitoExtension.class)
class SyncRepositoryUseCaseAzureReposTest {

    private static final String ORG = "org-1";
    private static final String ORG_URL = "https://dev.azure.com/fabrikam";
    private static final String AUTH = "Basic OnBhdA==";
    private static final String PROJECT = "a7573007-bbb3-4341-b726-0c4148a07853";
    private static final String REPO = "3411ebc1-d5aa-464f-9615-0b527bc66719";
    private static final Instant WATERMARK = Instant.parse("2026-09-20T00:00:00Z");
    private static final VcsCredentials CREDENTIALS =
            new VcsCredentials(VcsProvider.AZURE_REPOS, ORG, AUTH, false, ORG_URL);

    @Mock private AzureReposApiClient apiClient;
    @Mock private AzureDevOpsAccessProbeAdapter accessProbe;
    @Mock private AzureReposAccessRecorder accessRecorder;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private VcsAuthorResolver authors;
    @Mock private VcsCredentialsProvider credentialsProvider;

    private final InMemoryVcsStore store = new InMemoryVcsStore();
    private final List<IntegrationSyncLog> logs = new ArrayList<>();
    private SyncRepositoryUseCase useCase;

    @BeforeEach
    void setUp() throws IOException {
        AzureReposVcsProviderClient client =
                new AzureReposVcsProviderClient(apiClient, accessProbe, accessRecorder, organizationRepository);
        when(authors.provider()).thenReturn(VcsProvider.AZURE_REPOS);
        when(credentialsProvider.provider()).thenReturn(VcsProvider.AZURE_REPOS);
        VcsProviderRegistry registry = new VcsProviderRegistry(List.of(client), List.of(authors),
                List.of(credentialsProvider));
        VcsRepositoryContentSync contentSync = new VcsRepositoryContentSync(store.commitRepository,
                new IdAssigningPullRequests(store.pullRequestRepository), store.reviewEventRepository);
        useCase = new SyncRepositoryUseCase(syncLogRepository, store.bindingRepository, store.repositoryRepository,
                registry, organizationRepository, contentSync);

        lenient().when(syncLogRepository.save(any())).thenAnswer(inv -> {
            logs.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        lenient().when(organizationRepository.findById(ORG)).thenReturn(Optional.empty());
        lenient().when(apiClient.getRepository(ORG_URL, PROJECT, REPO, AUTH)).thenReturn(
                new AzRepositoryDto(REPO, "2016_10_31", "refs/heads/main", false, new AzProjectRefDto(PROJECT, "Payments")));
        lenient().when(apiClient.listCommits(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(apiClient.listPullRequestWorkItems(any(), any(), any(), anyInt(), any())).thenReturn(List.of());
        lenient().when(apiClient.listPullRequestCommits(any(), any(), any(), anyInt(), any())).thenReturn(List.of());
        List<AzThreadDto> threads = threads();
        lenient().when(apiClient.listThreads(any(), any(), any(), anyInt(), any())).thenReturn(threads);
        lenient().when(apiClient.listPullRequests(eq(ORG_URL), eq(PROJECT), eq(REPO), any(), eq(AUTH)))
                .thenAnswer(inv -> server(inv.getArgument(3)));

        VcsRepository repository = new VcsRepository("repo-1", ORG, VcsProvider.AZURE_REPOS, REPO, "2016_10_31");
        repository.setExternalProjectId(PROJECT);
        repository.setDefaultBranch("main");
        store.repositoryRepository.save(repository);
    }

    @Test
    void shouldSyncActiveAndRecentlyClosedPullRequestsAndIgnoreOldOnes() {
        VcsRepoBinding binding = binding(WATERMARK);

        useCase.execute(CREDENTIALS, binding, SyncTrigger.MANUAL, WATERMARK.minus(Duration.ofDays(90)));

        assertThat(store.pullRequests).extracting(VcsPullRequest::getExternalId).containsExactlyInAnyOrder("22", "20");
        VcsPullRequest merged = pullRequest("20");
        assertThat(merged.getStatus()).isEqualTo(PullRequestStatus.MERGED);
        assertThat(merged.getMergedAt()).isEqualTo(Instant.parse("2026-09-25T10:00:00Z"));
        assertThat(pullRequest("22").getStatus()).isEqualTo(PullRequestStatus.OPEN);
        verify(apiClient).listThreads(ORG_URL, PROJECT, REPO, 22, AUTH);
        verify(apiClient).listThreads(ORG_URL, PROJECT, REPO, 20, AUTH);
        verify(apiClient, never()).listThreads(ORG_URL, PROJECT, REPO, 5, AUTH);
        verify(apiClient, never()).listRepositories(any(), any());
        assertThat(binding.getLastSyncAt()).isAfter(WATERMARK);
        assertThat(logs.getLast().getIntegration()).isEqualTo(Integration.AZURE_DEVOPS);
        assertThat(logs.getLast().getOperation()).isEqualTo(SyncOperation.SYNC_REPOS);
        assertThat(logs.getLast().getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void shouldStoreVoteAndCommentEventsWithTimestamps() {
        useCase.execute(CREDENTIALS, binding(WATERMARK), SyncTrigger.MANUAL, null);

        String prId = pullRequest("22").getId();
        List<VcsReviewEvent> events = store.events.stream().filter(e -> e.getPullRequestId().equals(prId)).toList();
        assertThat(events).extracting(VcsReviewEvent::getExternalEventId).containsExactly("143", "147:1", "148:1");
        assertThat(events.getFirst().getKind()).isEqualTo(ReviewKind.APPROVED);
        assertThat(events.getFirst().getOccurredAt()).isEqualTo(Instant.parse("2016-11-01T16:30:36.58Z"));
        assertThat(events.getFirst().getProvider()).isEqualTo(VcsProvider.AZURE_REPOS);
    }

    @Test
    void shouldMarkAPreviouslyStoredCommentDeleted() {
        useCase.execute(CREDENTIALS, binding(WATERMARK), SyncTrigger.MANUAL, null);
        String prId = pullRequest("22").getId();
        VcsReviewEvent stored = new VcsReviewEvent("ev-1", ORG, prId, "repo-1", VcsProvider.AZURE_REPOS, "148:2",
                ReviewKind.COMMENTED, Instant.parse("2016-11-01T16:30:51.383Z"));
        store.events.add(stored);

        useCase.execute(CREDENTIALS, binding(WATERMARK), SyncTrigger.MANUAL, null);

        assertThat(stored.isDeleted()).isTrue();
    }

    @Test
    void shouldFailOnlyThisBindingWhenTheFirstSyncLacksCodeScope() {
        when(apiClient.listRepositories(ORG_URL, AUTH)).thenThrow(WebClientResponseException.create(401,
                "Unauthorized", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8));
        when(accessProbe.projects(ORG_URL, AUTH)).thenReturn(AzureProjectsAccess.GRANTED);
        VcsRepoBinding firstSync = binding(null);

        assertThatThrownBy(() -> useCase.execute(CREDENTIALS, firstSync, SyncTrigger.MANUAL, WATERMARK))
                .isInstanceOf(AzureReposScopeMissingException.class);
        assertThat(firstSync.getLastSyncAt()).isNull();
        assertThat(store.pullRequests).isEmpty();
        assertThat(logs.getLast().getStatus()).isEqualTo(SyncStatus.FAILED);
        verify(accessRecorder).record(ORG, false);
    }

    /** The server side of the documented search criteria over a fixed set of pull requests. */
    private static List<AzPullRequestDto> server(AzPullRequestQuery query) {
        List<AzPullRequestDto> all = List.of(
                pr(22, "active", "2026-09-18T09:00:00Z", null),
                pr(20, "completed", "2026-09-15T09:00:00Z", "2026-09-25T10:00:00Z"),
                pr(5, "completed", "2026-08-01T09:00:00Z", "2026-09-10T10:00:00Z"));
        return all.stream()
                .filter(pr -> query.status().equals("all") || query.status().equals(pr.status()))
                .filter(pr -> query.minTime() == null || !Instant.parse(query.timeRangeType().equals("closed")
                        ? pr.closedDate() : pr.creationDate()).isBefore(query.minTime()))
                .toList();
    }

    private static AzPullRequestDto pr(int id, String status, String created, String closed) {
        return new AzPullRequestDto(id, status, null, created, closed, "PR " + id, null, "refs/heads/f" + id,
                "refs/heads/main", false, null);
    }

    private VcsPullRequest pullRequest(String externalId) {
        return store.pullRequests.stream().filter(pr -> pr.getExternalId().equals(externalId)).findFirst().orElseThrow();
    }

    private VcsRepoBinding binding(Instant lastSyncAt) {
        VcsRepoBinding binding = new VcsRepoBinding("bind-1", ORG, "proj-1", "repo-1", VcsProvider.AZURE_REPOS, true);
        binding.setLastSyncAt(lastSyncAt);
        return binding;
    }

    private static List<AzThreadDto> threads() throws IOException {
        try (InputStream in = SyncRepositoryUseCaseAzureReposTest.class
                .getResourceAsStream("/fixtures/azure-repos/threads.json")) {
            return new ObjectMapper().readValue(in, ThreadsPage.class).value();
        }
    }

    record ThreadsPage(List<AzThreadDto> value, Integer count) {}

    /** The in-memory store keys pull requests by id; the real repository assigns it on insert. */
    private record IdAssigningPullRequests(VcsPullRequestRepository delegate) implements VcsPullRequestRepository {

        @Override
        public VcsPullRequest save(VcsPullRequest pullRequest) {
            if (pullRequest.getId() == null) pullRequest.setId(UUID.randomUUID().toString());
            return delegate.save(pullRequest);
        }

        @Override
        public Optional<VcsPullRequest> findByOrganizationIdAndRepositoryIdAndExternalId(String organizationId,
                                                                                         String repositoryId,
                                                                                         String externalId) {
            return delegate.findByOrganizationIdAndRepositoryIdAndExternalId(organizationId, repositoryId, externalId);
        }

        @Override
        public Optional<VcsPullRequest> findByIdAndOrganizationId(String id, String organizationId) {
            return delegate.findByIdAndOrganizationId(id, organizationId);
        }

        @Override
        public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryId(String organizationId, String repositoryId) {
            return delegate.findAllByOrganizationIdAndRepositoryId(organizationId, repositoryId);
        }

        @Override
        public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndStatus(String organizationId,
                                                                                      Collection<String> repositoryIds,
                                                                                      PullRequestStatus status) {
            return delegate.findAllByOrganizationIdAndRepositoryIdInAndStatus(organizationId, repositoryIds, status);
        }

        @Override
        public List<VcsPullRequest> findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(
                String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
            return delegate.findMergedByOrganizationIdAndRepositoryIdInAndMergedAtBetween(organizationId,
                    repositoryIds, from, to);
        }

        @Override
        public List<VcsPullRequest> findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(
                String organizationId, Collection<String> repositoryIds, Instant from, Instant to) {
            return delegate.findAllByOrganizationIdAndRepositoryIdInAndCreatedAtBetween(organizationId,
                    repositoryIds, from, to);
        }

        @Override
        public List<VcsPullRequest> findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(
                String organizationId, String authorUserId, Instant from, Instant to) {
            return delegate.findAllByOrganizationIdAndAuthorUserIdAndCreatedAtBetween(organizationId, authorUserId,
                    from, to);
        }

        @Override
        public VcsPullRequestPage findPageByRepository(String organizationId, String repositoryId,
                                                       PullRequestStatus status, String authorLogin, int page,
                                                       int size) {
            return delegate.findPageByRepository(organizationId, repositoryId, status, authorLogin, page, size);
        }

        @Override
        public void updateMetrics(String organizationId, String id, Double cycleTimeHours,
                                  Double timeToFirstReviewHours, Double timeToFirstApprovalHours) {
            delegate.updateMetrics(organizationId, id, cycleTimeHours, timeToFirstReviewHours, timeToFirstApprovalHours);
        }
    }
}
