package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReconcileAgentUsageUseCaseTest {

    final AgentAIUsageRepository repo = mock(AgentAIUsageRepository.class);
    final ReconcileAgentUsageUseCase useCase = new ReconcileAgentUsageUseCase(repo);
    final AgentPrincipal principal = new AgentPrincipal("org-1", "user-1", "dev-1");

    static AIUsage row(String key, LocalDate day, long in, long out) {
        AIUsage u = new AIUsage("id-" + key, "org-1", "user-1", null, "agent", day, "m", "w", null, "dev-1", null,
                in, out, 1, "claude-code", null, null, null, true, 0L, null, null);
        u.setAgentKey(key);
        return u;
    }

    @Test
    void should_sumTokensPerDay_andDigestTheSortedKeys() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        when(repo.findCountersByDeviceAndDayBetween("org-1", "dev-1", d, d))
                .thenReturn(List.of(row("b", d, 1, 2), row("a", d, 10, 20)));

        var r = useCase.reconcile(principal, d, d);

        assertThat(r.days()).hasSize(1);
        assertThat(r.days().get(0).rows()).isEqualTo(2);
        assertThat(r.days().get(0).tokens().input()).isEqualTo(11);
        assertThat(r.days().get(0).tokens().output()).isEqualTo(22);
        assertThat(r.days().get(0).keysDigest()).isEqualTo(ReconcileAgentUsageUseCase.digest(List.of("a", "b")));
    }

    @Test
    void should_rejectPeriodsOverThirtyOneDaysOrReversed() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        assertThatThrownBy(() -> useCase.reconcile(principal, d, d.plusDays(31))).isInstanceOf(AgentRequestInvalidException.class);
        assertThatThrownBy(() -> useCase.reconcile(principal, d, d.minusDays(1))).isInstanceOf(AgentRequestInvalidException.class);
        assertThatThrownBy(() -> useCase.reconcile(principal, null, d)).isInstanceOf(AgentRequestInvalidException.class);
        assertThat(useCase.reconcile(principal, d, d.plusDays(30))).isNotNull();
    }

    @Test
    void should_requireTheDayToListKeys() {
        assertThatThrownBy(() -> useCase.keys(principal, null)).isInstanceOf(AgentRequestInvalidException.class);
        when(repo.findKeysByDeviceAndDay(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of("a"));
        assertThat(useCase.keys(principal, LocalDate.of(2026, 10, 1)).keys()).containsExactly("a");
    }
}
