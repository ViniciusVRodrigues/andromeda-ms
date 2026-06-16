package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummary;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageSummaryByModel;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetAiUsageSummaryUseCase {

    private final AIUsageRepository aiUsageRepository;

    public GetAiUsageSummaryOutput execute(String requestingUserId, String targetUserId,
                                           boolean hasReadAll, String organizationId,
                                           LocalDate startDate, LocalDate endDate,
                                           String model, String groupBy) {
        if (targetUserId != null && !targetUserId.equals(requestingUserId) && !hasReadAll) {
            throw new BusinessRuleException("AI_READ_ALL_REQUIRED",
                    "Permission AI_READ_ALL is required to view another user's AI usage");
        }

        String effectiveUserId = targetUserId != null ? targetUserId : requestingUserId;

        if ("model".equals(groupBy)) {
            List<AiUsageSummaryByModel> grouped = aiUsageRepository.findSummaryGroupedByModel(
                    organizationId, effectiveUserId, startDate, endDate, model);
            return new GetAiUsageSummaryOutput(null, grouped);
        }

        AiUsageSummary summary = aiUsageRepository.findSummaryByUserIdAndFilters(
                organizationId, effectiveUserId, startDate, endDate, model);
        return new GetAiUsageSummaryOutput(summary, null);
    }

    public record GetAiUsageSummaryOutput(AiUsageSummary summary, List<AiUsageSummaryByModel> groupedByModel) {}
}
