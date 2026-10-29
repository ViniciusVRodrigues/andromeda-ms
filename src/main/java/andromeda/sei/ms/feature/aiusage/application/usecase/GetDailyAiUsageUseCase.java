package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import java.time.YearMonth;
import java.util.Set;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetDailyAiUsageUseCase {
    private final AIUsageRepository repository;
    private final AiUsageAllocationRepository allocationRepository;
    public record Day(LocalDate day, long inputTokens, long outputTokens, long interactions, BigDecimal costUsd,
                     boolean hasAgentAllocation) {}
    public List<Day> execute(String organizationId, String userId, YearMonth month) {
        Set<LocalDate> agentDays = allocationRepository.findAgentDays(organizationId, userId, month.atDay(1), month.atEndOfMonth());
        return repository.findDailyUsageByUserIdAndDateRange(organizationId, userId, month.atDay(1), month.atEndOfMonth(), null)
                .stream().map(d -> new Day(d.day(), d.inputTokens(), d.outputTokens(), d.interactions(), d.costUsd(),
                        agentDays.contains(d.day()))).toList();
    }
}
