package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.AiUsageSummaryByMonthOutput;
import andromeda.sei.ms.feature.aiusage.application.dto.ModelBreakdownOutput;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetAiUsageSummaryByMonthUseCase {

    private final AIUsageRepository aiUsageRepository;

    public AiUsageSummaryByMonthOutput execute(String organizationId, String userId, YearMonth month) {
        List<AIUsage> records = aiUsageRepository.findByOrganizationIdAndUserIdAndMonth(
                organizationId, userId, month);

        long totalInputTokens = records.stream().mapToLong(AIUsage::getInputTokens).sum();
        long totalOutputTokens = records.stream().mapToLong(AIUsage::getOutputTokens).sum();
        int totalInteractions = records.stream().mapToInt(AIUsage::getInteractions).sum();
        BigDecimal totalCostUsd = records.stream()
                .map(r -> r.getCostUsd() != null ? r.getCostUsd() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ModelBreakdownOutput> modelBreakdown = buildModelBreakdown(records, totalInteractions);

        return new AiUsageSummaryByMonthOutput(
                month, totalInputTokens, totalOutputTokens, totalInteractions,
                totalCostUsd, modelBreakdown);
    }

    private List<ModelBreakdownOutput> buildModelBreakdown(List<AIUsage> records, int totalInteractions) {
        if (totalInteractions == 0) {
            return List.of();
        }

        Map<String, Long> interactionsByModel = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getModel() != null ? r.getModel() : "unknown",
                        Collectors.summingLong(AIUsage::getInteractions)));

        BigDecimal total = BigDecimal.valueOf(totalInteractions);

        return interactionsByModel.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    BigDecimal percentage = BigDecimal.valueOf(entry.getValue())
                            .divide(total, 4, RoundingMode.HALF_UP);
                    return new ModelBreakdownOutput(entry.getKey(), percentage);
                })
                .toList();
    }
}
