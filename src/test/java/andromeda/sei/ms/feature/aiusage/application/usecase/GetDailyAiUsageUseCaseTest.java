package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDailyAiUsageUseCaseTest {

    @Test
    void should_flagOnlyTheDaysThatHaveAnAgentAllocation() {
        AIUsageRepository usages = mock(AIUsageRepository.class);
        AiUsageAllocationRepository allocations = mock(AiUsageAllocationRepository.class);
        LocalDate d1 = LocalDate.of(2026, 10, 28);
        LocalDate d2 = LocalDate.of(2026, 10, 29);
        when(usages.findDailyUsageByUserIdAndDateRange("org-1", "u1", LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31), null)).thenReturn(List.of(
                new DailyAiUsage(d1, 1, 2, 0, 0, 1, BigDecimal.ONE), new DailyAiUsage(d2, 1, 2, 0, 0, 1, BigDecimal.ONE)));
        when(allocations.findAgentDays("org-1", "u1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .thenReturn(Set.of(d2));

        var days = new GetDailyAiUsageUseCase(usages, allocations).execute("org-1", "u1", YearMonth.of(2026, 10));

        assertThat(days).extracting(GetDailyAiUsageUseCase.Day::hasAgentAllocation).containsExactly(false, true);
    }
}
