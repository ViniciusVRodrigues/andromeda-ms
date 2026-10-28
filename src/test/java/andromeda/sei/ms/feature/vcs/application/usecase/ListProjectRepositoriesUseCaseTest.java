package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.PerformanceClass;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListProjectRepositoriesUseCaseTest {

    private static final String ORG = "org-1";
    private static final LocalDate START = LocalDate.parse("2026-09-01");
    private static final LocalDate END = LocalDate.parse("2026-09-28");
    private static final Instant WINDOW_FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant WINDOW_TO = Instant.parse("2026-09-29T00:00:00Z");

    @Mock private VcsReadPort vcsReadPort;
    @Mock private DeliveryMetricsService deliveryMetricsService;

    @InjectMocks
    private ListProjectRepositoriesUseCase useCase;

    private final VcsRepository repo = VcsLegacyFixtures.repository("repo-1", ORG, "1", "octo", "hello", "octo/hello",
            WINDOW_FROM, true, WINDOW_FROM, WINDOW_FROM, 0L);

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_reportPercentilesReviewAndDelivery_when_windowHasPullRequests() {
        VcsPullRequest mergedFast = merged("pr-1", 2.0, 1.0);
        VcsPullRequest mergedSlow = merged("pr-2", 50.0, 30.0);
        VcsPullRequest oldMerge = pr("pr-3", PullRequestStatus.MERGED, WINDOW_FROM.minus(Duration.ofDays(40)));
        oldMerge.setMergedAt(WINDOW_FROM.minus(Duration.ofDays(39)));
        oldMerge.setCycleTimeHours(999.0);
        when(vcsReadPort.repositoriesOfProject(ORG, "proj-1")).thenReturn(List.of(repo));
        when(vcsReadPort.pullRequestsOfRepository(ORG, "repo-1"))
                .thenReturn(List.of(mergedFast, mergedSlow, oldMerge));
        DeliveryMetrics delivery = new DeliveryMetrics(DeliveryMethod.VCS_PROXY, 50.0, PerformanceClass.LOW, 0.5,
                PerformanceClass.MEDIUM, new DurationStat(2.0, 50.0, 2), PerformanceClass.HIGH, 2, 1);
        when(deliveryMetricsService.mergedChangesFrom(eq(ORG), eq(List.of(repo)), anyList())).thenReturn(List.of());
        when(deliveryMetricsService.calculate(ORG, List.of(), List.of(), WINDOW_FROM, WINDOW_TO)).thenReturn(delivery);

        List<ProjectRepositoryOutput> result = useCase.execute("proj-1", START, END);

        ProjectRepositoryOutput summary = result.get(0);
        assertThat(summary.prCycleTime()).isEqualTo(new DurationStat(2.0, 50.0, 2));
        assertThat(summary.review().timeToFirstReview().n()).isEqualTo(2);
        assertThat(summary.delivery()).isEqualTo(delivery);
        assertThat(summary.latestCfr()).isEqualTo(50.0);
        assertThat(summary.latestCfrEstimated()).isTrue();
        assertThat(summary.latestCfrPeriodEnd()).isEqualTo(WINDOW_TO);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VcsPullRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(deliveryMetricsService).mergedChangesFrom(eq(ORG), eq(List.of(repo)), captor.capture());
        assertThat(captor.getValue()).extracting(VcsPullRequest::getId).containsExactly("pr-1", "pr-2");
    }

    @Test
    void should_exposeTheBindingBranchSettings_andLegacyDefaults() {
        var binding = new andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding("b-1", ORG, "proj-1", "repo-1",
                andromeda.sei.ms.feature.vcs.domain.VcsProvider.GITHUB, true);
        binding.setBranches(List.of("develop", "main"));
        binding.setProductionBranch("main");
        binding.setFilterPullRequestsByBranch(true);
        binding.setMissingBranches(List.of("old"));
        binding.setSyncState(andromeda.sei.ms.feature.vcs.domain.VcsSyncState.OK);
        binding.setSyncStateAt(Instant.now());
        binding.setLastSyncAt(WINDOW_TO);
        when(vcsReadPort.repositoriesOfProject(ORG, "proj-1")).thenReturn(List.of(repo));
        when(vcsReadPort.bindingsOfProject(ORG, "proj-1")).thenReturn(List.of(binding));
        when(vcsReadPort.pullRequestsOfRepository(ORG, "repo-1")).thenReturn(List.of());
        when(deliveryMetricsService.calculate(any(), anyList(), anyList(), any(), any())).thenReturn(
                new DeliveryMetrics(DeliveryMethod.VCS_PROXY, null, null, null, null, null, null, 0, 0));

        ProjectRepositoryOutput summary = useCase.execute("proj-1", START, END).get(0);

        assertThat(summary.branches()).containsExactly("develop", "main");
        assertThat(summary.productionBranch()).isEqualTo("main");
        assertThat(summary.filterPullRequestsByBranch()).isTrue();
        assertThat(summary.missingBranches()).containsExactly("old");
        assertThat(summary.syncState()).isEqualTo(andromeda.sei.ms.feature.vcs.domain.VcsSyncState.OK);
        assertThat(summary.lastSyncAt()).isEqualTo(WINDOW_TO);
    }

    @Test
    void should_countOpenAndStaleIgnoringDrafts_when_pullRequestsAreOpen() {
        VcsPullRequest staleOpen = pr("pr-4", PullRequestStatus.OPEN, Instant.now().minus(Duration.ofDays(3)));
        VcsPullRequest draft = pr("pr-5", PullRequestStatus.OPEN, Instant.now().minus(Duration.ofDays(3)));
        draft.setDraft(true);
        when(vcsReadPort.repositoriesOfProject(ORG, "proj-1")).thenReturn(List.of(repo));
        when(vcsReadPort.pullRequestsOfRepository(ORG, "repo-1"))
                .thenReturn(List.of(staleOpen, draft));
        when(deliveryMetricsService.calculate(any(), any(), any(), any(), any()))
                .thenReturn(DeliveryMetrics.unavailable());

        ProjectRepositoryOutput summary = useCase.execute("proj-1", null, null).get(0);

        assertThat(summary.openPrCount()).isEqualTo(2);
        assertThat(summary.stalePrCount()).isEqualTo(1);
        assertThat(summary.latestCfr()).isNull();
        assertThat(summary.latestCfrPeriodEnd()).isNull();
    }

    @Test
    void should_notCountPromotionPullRequestAsStale_when_bothBranchesAreChosenOnTheBinding() {
        var binding = new andromeda.sei.ms.feature.vcs.domain.VcsRepoBinding("b-1", ORG, "proj-1", "repo-1",
                andromeda.sei.ms.feature.vcs.domain.VcsProvider.GITHUB, true);
        binding.setBranches(List.of("main", "staging"));
        VcsPullRequest promotion = pr("pr-6", PullRequestStatus.OPEN, Instant.now().minus(Duration.ofDays(3)));
        promotion.setSourceBranch("staging");
        promotion.setBaseBranch("main");
        VcsPullRequest feature = pr("pr-7", PullRequestStatus.OPEN, Instant.now().minus(Duration.ofDays(3)));
        feature.setSourceBranch("feature/x");
        feature.setBaseBranch("main");
        when(vcsReadPort.repositoriesOfProject(ORG, "proj-1")).thenReturn(List.of(repo));
        when(vcsReadPort.bindingsOfProject(ORG, "proj-1")).thenReturn(List.of(binding));
        when(vcsReadPort.pullRequestsOfRepository(ORG, "repo-1")).thenReturn(List.of(promotion, feature));
        when(deliveryMetricsService.calculate(any(), any(), any(), any(), any()))
                .thenReturn(DeliveryMetrics.unavailable());

        ProjectRepositoryOutput summary = useCase.execute("proj-1", null, null).get(0);

        assertThat(summary.openPrCount()).isEqualTo(2);
        assertThat(summary.stalePrCount()).isEqualTo(1);
    }

    private VcsPullRequest merged(String id, double cycle, double ttfr) {
        VcsPullRequest pr = pr(id, PullRequestStatus.MERGED, WINDOW_FROM.plus(Duration.ofDays(2)));
        pr.setMergedAt(WINDOW_FROM.plus(Duration.ofDays(3)));
        pr.setCycleTimeHours(cycle);
        pr.setTimeToFirstReviewHours(ttfr);
        return pr;
    }

    private VcsPullRequest pr(String id, PullRequestStatus status, Instant createdAt) {
        return VcsLegacyFixtures.pullRequest(id, ORG, "repo-1", "ext-" + id, 1, "t", status, "octocat", null,
                createdAt, null, null, createdAt, null, null, true, createdAt, 0L);
    }
}
