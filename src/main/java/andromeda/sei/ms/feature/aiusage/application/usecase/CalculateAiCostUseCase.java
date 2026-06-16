package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class CalculateAiCostUseCase {

    private static final BigDecimal K = BigDecimal.valueOf(1000);
    private static final int SCALE = 6;

    public BigDecimal execute(AIUsage usage, List<AiPriceEntry> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }

        AiPriceEntry entry = prices.stream()
                .filter(p -> p.getModel().equals(usage.getModel()))
                .findFirst()
                .orElse(null);

        if (entry == null) {
            return null;
        }

        BigDecimal inputCost = BigDecimal.valueOf(usage.getInputTokens())
                .multiply(entry.getPricePerKInput())
                .divide(K, SCALE, RoundingMode.HALF_UP);

        BigDecimal outputCost = BigDecimal.valueOf(usage.getOutputTokens())
                .multiply(entry.getPricePerKOutput())
                .divide(K, SCALE, RoundingMode.HALF_UP);

        return inputCost.add(outputCost);
    }
}
