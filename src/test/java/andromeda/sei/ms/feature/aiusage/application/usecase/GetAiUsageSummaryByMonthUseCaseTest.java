package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AiUsageSummaryByMonthOutput;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAiUsageSummaryByMonthUseCaseTest {

    @Mock private AIUsageRepository repository;

    private static AIUsage usage(String id, long in, long out, Long cacheRead, Long cacheWrite) {
        AIUsage u = new AIUsage(id, "org-1", "u1", null, "ds", LocalDate.of(2026, 10, 5), "m", "ws", "W", "mc", "M",
                in, out, 1, "claude-code", null, BigDecimal.ONE, Instant.now(), true, null, Instant.now(), Instant.now());
        // Agent rows carry cache; AIEF rows have no fields (0); a null from the source counts as 0.
        if (cacheRead != null) u.setCacheReadTokens(cacheRead);
        if (cacheWrite != null) u.setCacheWriteTokens(cacheWrite);
        return u;
    }

    @Test
    void should_sumCacheOfAgentRows_countingAiefAndNullAsZero() {
        YearMonth month = YearMonth.of(2026, 10);
        when(repository.findByOrganizationIdAndUserIdAndMonth("org-1", "u1", month)).thenReturn(List.of(
                usage("agent", 100, 50, 3000L, 400L),
                usage("aief", 10, 5, null, null),
                usage("agent-null", 1, 1, null, 7L)));

        AiUsageSummaryByMonthOutput out = new GetAiUsageSummaryByMonthUseCase(repository).execute("org-1", "u1", month);

        assertThat(out.totalCacheReadTokens()).isEqualTo(3000L);
        assertThat(out.totalCacheWriteTokens()).isEqualTo(407L);
        assertThat(out.totalInputTokens()).isEqualTo(111L);
        assertThat(out.totalOutputTokens()).isEqualTo(56L);
    }
}
