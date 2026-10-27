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
import java.util.Comparator;
import java.util.List;

/** Models used in the period that the cost calculation cannot price (same matching as the cost). */
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

        return aiUsageRepository.summarizeOutputByModel(organizationId, from, to).stream()
                .filter(row -> calculateAiCostUseCase.findPrice(row.model(), prices).isEmpty())
                .sorted(Comparator.comparingLong(AiUsageModelOutput::outputTokens).reversed())
                .map(row -> new UnpricedModelOutput(row.model(), row.outputTokens(), row.records(),
                        calculateAiCostUseCase.isBillable(row.model())))
                .toList();
    }
}
