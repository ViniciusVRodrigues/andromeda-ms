package andromeda.sei.ms.feature.aiusage.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Cost of one usage record = {@code outputTokens x pricePerKOutput / 1000}.
 *
 * <p>The input price is deliberately NOT part of the cost: the connector's {@code inputTokens} sums
 * plain input, cache read and cache write, which are billed at very different rates, and there is no
 * way to separate them afterwards. Charging the sum at the plain input price would overstate the cost
 * several times over, so only the output side (which is unambiguous) is priced.
 */
@Component
public class CalculateAiCostUseCase {

    private static final BigDecimal K = BigDecimal.valueOf(1000);
    private static final int SCALE = 6;
    private static final Pattern DATE_SUFFIX =
            Pattern.compile("(-\\d{8}|-\\d{4}-\\d{2}-\\d{2}|@\\d{8})$");

    /** Lowercase, trimmed, dots as dashes and without a trailing date suffix. */
    static String normalize(String model) {
        if (model == null) return "";
        String m = model.trim().toLowerCase().replace('.', '-');
        return DATE_SUFFIX.matcher(m).replaceFirst("");
    }

    /** {@code false} for null, blank, {@code auto} and {@code unknown}: these never have a price. */
    public boolean isBillable(String model) {
        if (model == null) return false;
        String m = model.trim();
        return !m.isEmpty() && !m.equalsIgnoreCase("auto") && !m.equalsIgnoreCase("unknown");
    }

    /**
     * Exact match first, then normalized match on both sides. Never matches a non-billable model nor
     * a row without an output price.
     */
    public Optional<AiPriceEntry> findPrice(String model, List<AiPriceEntry> prices) {
        if (prices == null || prices.isEmpty() || !isBillable(model)) {
            return Optional.empty();
        }
        // a row without an output price (saved before the request was validated) counts as no price
        List<AiPriceEntry> priced = prices.stream()
                .filter(p -> p.getModel() != null && p.getPricePerKOutput() != null)
                .toList();
        Optional<AiPriceEntry> exact = priced.stream()
                .filter(p -> p.getModel().equals(model))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        String wanted = normalize(model);
        return priced.stream()
                .filter(p -> normalize(p.getModel()).equals(wanted))
                .findFirst();
    }

    public BigDecimal execute(AIUsage usage, List<AiPriceEntry> prices) {
        return findPrice(usage.getModel(), prices)
                .map(entry -> BigDecimal.valueOf(usage.getOutputTokens())
                        .multiply(entry.getPricePerKOutput())
                        .divide(K, SCALE, RoundingMode.HALF_UP))
                .orElse(null);
    }
}
