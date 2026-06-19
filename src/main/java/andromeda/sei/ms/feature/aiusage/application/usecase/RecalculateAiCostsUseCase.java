package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecalculateAiCostsUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final OrganizationRepository organizationRepository;
    private final CalculateAiCostUseCase calculateAiCostUseCase;

    public int execute(String organizationId) {
        List<AiPriceEntry> prices = organizationRepository.findById(organizationId)
                .map(org -> org.getAiPrices() != null ? org.getAiPrices() : List.<AiPriceEntry>of())
                .orElse(List.of());

        if (prices.isEmpty()) {
            log.warn("recalculate-costs: no AI prices configured for org={}", organizationId);
            return 0;
        }

        List<AIUsage> usages = aiUsageRepository.findAllByOrganizationId(organizationId);
        int updated = 0;
        for (AIUsage usage : usages) {
            BigDecimal cost = calculateAiCostUseCase.execute(usage, prices);
            if (cost != null) {
                aiUsageRepository.updateCost(
                        usage.getOrganizationId(), usage.getUserId(), usage.getDatasetId(),
                        usage.getDay(), usage.getModel(), usage.getWorkspaceId(), usage.getMachineId(),
                        cost);
                updated++;
            }
        }
        log.info("recalculate-costs: org={} updated={} of total={}", organizationId, updated, usages.size());
        return updated;
    }
}
