package andromeda.sei.ms.feature.vcs.application.usecase;

import andromeda.sei.ms.feature.vcs.VcsLegacyFixtures;
import andromeda.sei.ms.feature.vcs.application.port.VcsReadPort;
import andromeda.sei.ms.feature.vcs.application.dto.DeliveryMetricPointOutput;
import andromeda.sei.ms.feature.vcs.domain.VcsRepository;
import andromeda.sei.ms.feature.vcs.domain.exception.RepoNotFoundException;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.organization.domain.RemediationRules;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetRepositoryDeliveryMetricsUseCaseTest {

    private static final String ORG = "org-1";
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = FROM.plus(Duration.ofDays(17));

    @Mock private VcsReadPort vcsReadPort;
    @Mock private DeliveryMetricsService deliveryMetricsService;

    @InjectMocks
    private GetRepositoryDeliveryMetricsUseCase useCase;

    private final VcsRepository repo = VcsLegacyFixtures.repository("repo-1", ORG, "1", "octo", "hello", "octo/hello",
            FROM, true, FROM, FROM, 0L);

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void should_bucketMergesWeekly_when_windowSpansSeveralWeeks() {
        when(vcsReadPort.repository(ORG, "repo-1")).thenReturn(Optional.of(repo));
        when(deliveryMetricsService.remediationRules(ORG)).thenReturn(RemediationRules.defaults());
        when(deliveryMetricsService.mergedChanges(ORG, List.of(repo), FROM, TO)).thenReturn(List.of(
                change("a", "feature/a", FROM.plus(Duration.ofDays(1))),
                change("b", "hotfix/b", FROM.plus(Duration.ofDays(2))),
                change("c", "feature/c", FROM.plus(Duration.ofDays(3))),
                change("d", "feature/d", FROM.plus(Duration.ofDays(4))),
                change("e", "feature/e", FROM.plus(Duration.ofDays(15)))));

        List<DeliveryMetricPointOutput> series = useCase.execute("repo-1", FROM, TO);

        assertThat(series).hasSize(3);
        assertThat(series.get(0)).isEqualTo(new DeliveryMetricPointOutput(FROM, FROM.plus(Duration.ofDays(7)),
                25.0, 4.0, 4, 1));
        assertThat(series.get(1).mergedPrs()).isZero();
        assertThat(series.get(1).changeFailRatePercent()).isNull();
        assertThat(series.get(1).deploymentsPerWeek()).isEqualTo(0.0);
        assertThat(series.get(2).periodEnd()).isEqualTo(TO);
        assertThat(series.get(2).mergedPrs()).isEqualTo(1);
    }

    @Test
    void should_defaultToLast90Days_when_noWindowGiven() {
        when(vcsReadPort.repository(ORG, "repo-1")).thenReturn(Optional.of(repo));
        when(deliveryMetricsService.mergedChanges(eq(ORG), eq(List.of(repo)), any(), any())).thenReturn(List.of());

        List<DeliveryMetricPointOutput> series = useCase.execute("repo-1", null, null);

        assertThat(series).hasSize(13);
        assertThat(Duration.between(series.get(0).periodStart(), series.get(12).periodEnd()))
                .isEqualTo(Duration.ofDays(90));
    }

    @Test
    void should_ignoreMergesIntoOtherBranches_when_bucketing() {
        when(vcsReadPort.repository(ORG, "repo-1")).thenReturn(Optional.of(repo));
        when(deliveryMetricsService.remediationRules(ORG)).thenReturn(RemediationRules.defaults());
        MergedChange intoDevelop = new MergedChange("x", "t", "hotfix/x", "develop", "main", false,
                FROM, null, FROM.plus(Duration.ofDays(1)));
        when(deliveryMetricsService.mergedChanges(ORG, List.of(repo), FROM, TO)).thenReturn(List.of(intoDevelop));

        List<DeliveryMetricPointOutput> series = useCase.execute("repo-1", FROM, TO);

        assertThat(series).allMatch(p -> p.mergedPrs() == 0);
    }

    @Test
    void should_throwNotFound_when_repoBelongsToAnotherOrganization() {
        when(vcsReadPort.repository(ORG, "repo-x")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute("repo-x", FROM, TO))
                .isInstanceOf(RepoNotFoundException.class);
        verify(vcsReadPort).repository(ORG, "repo-x");
    }

    private MergedChange change(String id, String sourceBranch, Instant mergedAt) {
        return new MergedChange(id, "Change " + id, sourceBranch, "main", "main", false,
                mergedAt.minus(Duration.ofHours(3)), null, mergedAt);
    }
}
