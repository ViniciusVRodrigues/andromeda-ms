package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.application.dto.UnpricedModelOutput;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.AiUsageModelOutput;
import andromeda.sei.ms.feature.aiusage.domain.exception.AiUsagePeriodInvalidException;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Models used in the period that the cost cannot fully price (same matching as the cost): no price,
 * a price lacking a part the agent used, or not billable. Order: no price, incomplete, non-billable;
 * each group by output tokens descending.
 */
@Service
@RequiredArgsConstructor
public class ListUnpricedModelsUseCase {

    private final AIUsageRepository aiUsageRepository;
    private final OrganizationRepository organizationRepository;
    private final CalculateAiCostUseCase calculateAiCostUseCase;

    public List<UnpricedModelOutput> execute(String organizationId, LocalDate from, LocalDate to) {
        // no from/to: the whole recorded usage; one missing end stays open
        if (from != null && to != null && from.isAfter(to)) {
            throw new AiUsagePeriodInvalidException(from, to);
        }
        List<AiPriceEntry> prices = organizationRepository.findById(organizationId)
                .map(org -> org.getAiPrices() != null ? org.getAiPrices() : List.<AiPriceEntry>of())
                .orElse(List.of());

        List<UnpricedModelOutput> items = new ArrayList<>();
        for (AiUsageModelOutput row : aiUsageRepository.summarizeOutputByModel(organizationId, from, to)) {
            boolean billable = calculateAiCostUseCase.isBillable(row.model());
            Optional<AiPriceEntry> price = calculateAiCostUseCase.findPrice(row.model(), prices);
            List<String> missing;
            if (!billable) {
                missing = List.of();
            } else if (price.isEmpty()) {
                missing = List.of("OUTPUT");
            } else {
                missing = new ArrayList<>();
                AiPriceEntry p = price.get();
                if (p.getPricePerKInput() == null && row.agentInputTokens() > 0) missing.add("INPUT");
                if (p.getPricePerKCacheRead() == null && row.agentCacheReadTokens() > 0) missing.add("CACHE_READ");
                if (p.getPricePerKCacheWrite() == null && row.agentCacheWriteTokens() > 0) missing.add("CACHE_WRITE");
                if (missing.isEmpty()) continue;
            }
            items.add(new UnpricedModelOutput(row.model(), row.outputTokens(), row.records(), billable, missing,
                    row.agentInputTokens(), row.agentCacheReadTokens(), row.agentCacheWriteTokens()));
        }
        return items.stream()
                .sorted(Comparator.comparingInt(ListUnpricedModelsUseCase::group)
                        .thenComparing(Comparator.comparingLong(UnpricedModelOutput::outputTokens).reversed()))
                .toList();
    }

    private static int group(UnpricedModelOutput item) {
        if (!item.billable()) return 2;
        return item.missing().contains("OUTPUT") ? 0 : 1;
    }
}
