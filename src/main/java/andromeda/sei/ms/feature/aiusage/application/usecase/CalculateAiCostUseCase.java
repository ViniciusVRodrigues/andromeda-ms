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
 * Cost of one usage record.
 *
 * <p>Local agent rows ({@code source == "AGENT"}): input, cache read, cache write and output tokens,
 * each at its own price per 1000 tokens (a missing price counts as zero); thinking tokens are already
 * part of the output and never added.
 *
 * <p>AIEF rows (extension): {@code outputTokens x pricePerKOutput / 1000} only. The extension's
 * {@code inputTokens} sums plain input, cache read and cache write, which cannot be separated, so
 * the input side is not priced.
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
                .map(entry -> "AGENT".equals(usage.getSource()) ? agentCost(usage, entry) : aiefCost(usage, entry))
                .orElse(null);
    }

    private static BigDecimal aiefCost(AIUsage usage, AiPriceEntry entry) {
        return BigDecimal.valueOf(usage.getOutputTokens())
                .multiply(entry.getPricePerKOutput())
                .divide(K, SCALE, RoundingMode.HALF_UP);
    }

    /** input + cache read + cache write + output, each at its own price; a missing price counts as 0. */
    private static BigDecimal agentCost(AIUsage usage, AiPriceEntry entry) {
        return part(usage.getInputTokens(), entry.getPricePerKInput())
                .add(part(usage.getCacheReadTokens(), entry.getPricePerKCacheRead()))
                .add(part(usage.getCacheWriteTokens(), entry.getPricePerKCacheWrite()))
                .add(part(usage.getOutputTokens(), entry.getPricePerKOutput()))
                .divide(K, SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal part(long tokens, BigDecimal pricePerK) {
        return pricePerK == null ? BigDecimal.ZERO : BigDecimal.valueOf(tokens).multiply(pricePerK);
    }
}
