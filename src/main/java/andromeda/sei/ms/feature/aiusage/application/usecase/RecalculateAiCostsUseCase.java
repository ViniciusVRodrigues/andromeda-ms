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

    /**
     * Rewrites {@code costUsd} of every record of the organization (the new cost, or {@code null}
     * when the model has no price; an empty price table clears all costs).
     *
     * @return how many records had their cost changed
     */
    public int execute(String organizationId) {
        List<AiPriceEntry> prices = organizationRepository.findById(organizationId)
                .map(org -> org.getAiPrices() != null ? org.getAiPrices() : List.<AiPriceEntry>of())
                .orElse(List.of());

        if (prices.isEmpty()) {
            log.warn("recalculate-costs: no AI prices configured for org={}, clearing costs", organizationId);
        }

        List<AIUsage> usages = aiUsageRepository.findAllByOrganizationId(organizationId);
        int changed = 0;
        for (AIUsage usage : usages) {
            BigDecimal cost = calculateAiCostUseCase.execute(usage, prices);
            if (!sameCost(usage.getCostUsd(), cost)) {
                changed++;
            }
            aiUsageRepository.updateCost(
                    usage.getOrganizationId(), usage.getUserId(), usage.getDatasetId(),
                    usage.getDay(), usage.getModel(), usage.getWorkspaceId(), usage.getMachineId(),
                    cost);
        }
        log.info("recalculate-costs: org={} changed={} of total={}", organizationId, changed, usages.size());
        return changed;
    }

    private static boolean sameCost(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }
}
