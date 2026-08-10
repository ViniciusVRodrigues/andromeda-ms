package andromeda.sei.ms.feature.github.application.usecase;

import andromeda.sei.ms.feature.github.domain.GithubCommitRepository;
import andromeda.sei.ms.feature.github.domain.GithubConnection;
import andromeda.sei.ms.feature.github.domain.GithubConnectionStatus;
import andromeda.sei.ms.feature.github.domain.GithubRepo;
import andromeda.sei.ms.feature.github.domain.RepoMetrics;
import andromeda.sei.ms.feature.github.domain.RepoMetricsRepository;
import andromeda.sei.ms.feature.github.infrastructure.client.GhIssueDto;
import andromeda.sei.ms.feature.github.infrastructure.client.GithubApiClient;
import andromeda.sei.ms.shared.infrastructure.security.EncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateRepoMetricsUseCaseTest {

    @Mock private GithubCommitRepository githubCommitRepository;
    @Mock private RepoMetricsRepository repoMetricsRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private EncryptionService encryptionService;

    @InjectMocks
    private CalculateRepoMetricsUseCase useCase;

    private GithubConnection connection;
    private GithubRepo repo;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        connection = new GithubConnection("conn-1", "org-1", "enc-pat", "user-1",
                GithubConnectionStatus.CONNECTED, null, 0, null, null, true, now, now, null);
        repo = new GithubRepo("repo-1", "org-1", "999", "octo", "hello", "octo/hello", now, true, now, now, null);
    }

    @Test
    void shouldSkipPersistence_whenZeroCommitsInPeriod() {
        when(githubCommitRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(eq("org-1"), eq("repo-1"), any(), any()))
                .thenReturn(0L);

        useCase.execute(connection, repo);

        verify(repoMetricsRepository, never()).save(any());
        verify(githubApiClient, never()).getIssues(any(), any(), any(), any(), any());
    }

    @Test
    void shouldComputeCfrRatio_correctIssuesOverCommits() {
        when(githubCommitRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(eq("org-1"), eq("repo-1"), any(), any()))
                .thenReturn(10L);
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        when(repoMetricsRepository.findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repoMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant createdInPeriod = Instant.now().minusSeconds(3600);
        GhIssueDto bugIssue = new GhIssueDto(1L, 1, createdInPeriod.toString(), createdInPeriod.toString(), null, null);
        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("bug"), any()))
                .thenReturn(List.of(bugIssue));
        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("hotfix"), any()))
                .thenReturn(List.of());

        useCase.execute(connection, repo);

        ArgumentCaptor<RepoMetrics> captor = ArgumentCaptor.forClass(RepoMetrics.class);
        verify(repoMetricsRepository).save(captor.capture());
        assertThat(captor.getValue().getCfr()).isEqualTo(0.1); // 1 issue / 10 commits
        assertThat(captor.getValue().isEstimated()).isFalse();
    }

    @Test
    void shouldUnionAndDeduplicateBugAndHotfixIssuesById() {
        when(githubCommitRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(eq("org-1"), eq("repo-1"), any(), any()))
                .thenReturn(4L);
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        when(repoMetricsRepository.findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repoMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant createdInPeriod = Instant.now().minusSeconds(3600);
        // Same issue id returned from both label queries (labeled both bug AND hotfix) -- must count once.
        GhIssueDto sharedIssue = new GhIssueDto(42L, 5, createdInPeriod.toString(), createdInPeriod.toString(), null, null);
        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("bug"), any()))
                .thenReturn(List.of(sharedIssue));
        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("hotfix"), any()))
                .thenReturn(List.of(sharedIssue));

        useCase.execute(connection, repo);

        ArgumentCaptor<RepoMetrics> captor = ArgumentCaptor.forClass(RepoMetrics.class);
        verify(repoMetricsRepository).save(captor.capture());
        assertThat(captor.getValue().getCfr()).isEqualTo(0.25); // 1 unique issue / 4 commits
    }

    @Test
    void shouldExcludeNotPlannedDuplicateAndPullRequestFlaggedEntries() {
        when(githubCommitRepository.countByOrganizationIdAndRepoIdAndAuthoredAtBetween(eq("org-1"), eq("repo-1"), any(), any()))
                .thenReturn(5L);
        when(encryptionService.decrypt("enc-pat")).thenReturn("plain-pat");
        when(repoMetricsRepository.findByOrganizationIdAndRepoIdAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(repoMetricsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Instant createdInPeriod = Instant.now().minusSeconds(3600);
        GhIssueDto notPlanned = new GhIssueDto(1L, 1, createdInPeriod.toString(), createdInPeriod.toString(), "not_planned", null);
        GhIssueDto duplicate = new GhIssueDto(2L, 2, createdInPeriod.toString(), createdInPeriod.toString(), "duplicate", null);
        GhIssueDto isPullRequest = new GhIssueDto(3L, 3, createdInPeriod.toString(), createdInPeriod.toString(), null, new Object());
        GhIssueDto valid = new GhIssueDto(4L, 4, createdInPeriod.toString(), createdInPeriod.toString(), null, null);

        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("bug"), any()))
                .thenReturn(List.of(notPlanned, duplicate, isPullRequest, valid));
        when(githubApiClient.getIssues(eq("octo"), eq("hello"), eq("Bearer plain-pat"), eq("hotfix"), any()))
                .thenReturn(List.of());

        useCase.execute(connection, repo);

        ArgumentCaptor<RepoMetrics> captor = ArgumentCaptor.forClass(RepoMetrics.class);
        verify(repoMetricsRepository).save(captor.capture());
        assertThat(captor.getValue().getCfr()).isEqualTo(0.2); // only "valid" counts: 1 / 5
    }
}
