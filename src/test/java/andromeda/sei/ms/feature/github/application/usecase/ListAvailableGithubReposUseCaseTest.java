package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.application.dto.AvailableGithubRepoOutput;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.GithubRepoBinding;
import andromeda.sei.ms.feature.github.domain.GithubRepoBindingRepository;
import andromeda.sei.ms.feature.github.domain.GithubRepoRepository;
import andromeda.sei.ms.feature.github.domain.exception.GithubConnectionNotFoundException;
import andromeda.sei.ms.feature.github.infrastructure.client.GhRepoDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.feature.integration.domain.Integration;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLog;
import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import andromeda.sei.ms.feature.integration.domain.SyncOperation;
import andromeda.sei.ms.feature.integration.domain.SyncStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAvailableGithubReposUseCaseTest {

    @Mock private GithubConnectionRepository githubConnectionRepository;
    @Mock private GithubRepoRepository githubRepoRepository;
    @Mock private GithubRepoBindingRepository githubRepoBindingRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;
    @Mock private IntegrationSyncLogRepository syncLogRepository;

    @InjectMocks
    private ListAvailableGithubReposUseCase useCase;

    private static final String ORG = "org-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        lenient().when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.of(connection()));
        lenient().when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        lenient().when(githubRepoRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        lenient().when(githubRepoBindingRepository.findAllByOrganizationIdAndActiveTrue(anyString()))
                .thenReturn(List.of());
        lenient().when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldUnionLiveAndCached_deduplicatedByFullName_withLiveWinning() {
        when(githubApiClient.getUserRepos(eq("Bearer plain-pat"), anyInt()))
                .thenReturn(List.of(ghRepoDto("octo/live-only"), ghRepoDto("octo/both")));
        when(githubRepoRepository.findAllByOrganizationId(ORG))
                .thenReturn(List.of(cachedRepo("octo/both"), cachedRepo("octo/cached-only")));

        List<AvailableGithubRepoOutput> repos = useCase.execute(null);

        assertThat(repos).hasSize(3);
        assertThat(repos).extracting(AvailableGithubRepoOutput::fullName)
                .containsExactly("octo/live-only", "octo/both", "octo/cached-only");
        assertThat(repos).extracting(AvailableGithubRepoOutput::source)
                .containsExactly(
                        AvailableGithubRepoOutput.Source.LIVE,
                        AvailableGithubRepoOutput.Source.LIVE,   // live wins over cached
                        AvailableGithubRepoOutput.Source.CACHED);
        assertThat(repos).allSatisfy(r -> assertThat(r.boundToProjectId()).isNull());
    }

    @Test
    void shouldExcludeReposBoundToOtherProjects_andKeepOwnWithBoundToProjectId() {
        when(githubApiClient.getUserRepos(eq("Bearer plain-pat"), anyInt()))
                .thenReturn(List.of(
                        ghRepoDto("octo/bound-elsewhere"),
                        ghRepoDto("octo/own"),
                        ghRepoDto("octo/free")));
        when(githubRepoBindingRepository.findAllByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(List.of(
                        binding("proj-OTHER", "octo/bound-elsewhere"),
                        binding("proj-1", "octo/own")));

        List<AvailableGithubRepoOutput> repos = useCase.execute("proj-1");

        assertThat(repos).extracting(AvailableGithubRepoOutput::fullName)
                .containsExactly("octo/own", "octo/free");
        assertThat(repos.get(0).boundToProjectId()).isEqualTo("proj-1");
        assertThat(repos.get(1).boundToProjectId()).isNull();
    }

    @Test
    void shouldThrowConnectionNotFound_when_noActiveConnection() {
        when(githubConnectionRepository.findByOrganizationIdAndActiveTrue(ORG))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(null))
                .isInstanceOf(GithubConnectionNotFoundException.class);
    }

    @Test
    void shouldWriteListReposLog_onSuccess() {
        when(githubApiClient.getUserRepos(eq("Bearer plain-pat"), anyInt()))
                .thenReturn(List.of(ghRepoDto("octo/one")));

        useCase.execute(null);

        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        IntegrationSyncLog log = captor.getValue();
        assertThat(log.getIntegration()).isEqualTo(Integration.GITHUB);
        assertThat(log.getOperation()).isEqualTo(SyncOperation.LIST_REPOS);
        assertThat(log.getTriggeredBy()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(log.getStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(log.getItemsProcessed()).isEqualTo(1);
    }

    @Test
    void shouldWriteFailedListReposLog_andRethrow_when_liveCallFails() {
        WebClientResponseException unauthorized = WebClientResponseException.create(
                401, "Unauthorized", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
        when(githubApiClient.getUserRepos(eq("Bearer plain-pat"), anyInt()))
                .thenThrow(unauthorized);

        assertThatThrownBy(() -> useCase.execute(null))
                .isSameAs(unauthorized);

        ArgumentCaptor<IntegrationSyncLog> captor = ArgumentCaptor.forClass(IntegrationSyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        IntegrationSyncLog log = captor.getValue();
        assertThat(log.getIntegration()).isEqualTo(Integration.GITHUB);
        assertThat(log.getOperation()).isEqualTo(SyncOperation.LIST_REPOS);
        assertThat(log.getTriggeredBy()).isEqualTo(SyncTrigger.MANUAL);
        assertThat(log.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(log.getHttpStatus()).isEqualTo(401);
    }

    private GithubConnection connection() {
        return new GithubConnection("conn-1", ORG, "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, NOW, NOW, null);
    }

    private GhRepoDto ghRepoDto(String fullName) {
        String name = fullName.substring(fullName.indexOf('/') + 1);
        return new GhRepoDto(Math.abs(fullName.hashCode()), name, fullName,
                new GhRepoDto.OwnerDto(fullName.substring(0, fullName.indexOf('/'))),
                "2024-01-01T00:00:00Z");
    }

    private GithubRepo cachedRepo(String fullName) {
        String name = fullName.substring(fullName.indexOf('/') + 1);
        return new GithubRepo("mongo-" + name, ORG, "ext-" + name, "octo", name, fullName,
                NOW, true, NOW, NOW, 1L);
    }

    private GithubRepoBinding binding(String projectId, String repoId) {
        return new GithubRepoBinding("binding-" + repoId, ORG, projectId, repoId, true, null,
                true, NOW, NOW, 1L);
    }
}
