package andromeda.sei.ms.feature.aiusage.infrastructure.migration;

import andromeda.sei.ms.feature.aiusage.application.usecase.CalculateAiCostUseCase;
import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import andromeda.sei.ms.feature.organization.domain.AiPriceEntry;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One-time migration (custo-ia-agente): the agent cost now includes input and cache, so for every
 * organization with active agent records the null input, cache-read and cache-write prices of the
 * entries whose model is in the catalog verified on 2026-10-29 are filled (never overwriting a value,
 * never adding a model) and the costs of the organization are recalculated.
 *
 * <p>A marker in {@code schema_migrations} skips later boots. A failure in one organization is logged
 * and the others still run; the marker is only written when all of them pass.
 */
@Slf4j
@Component
@Order(35)
@RequiredArgsConstructor
public class AiCostAgentCacheMigration implements ApplicationRunner {

    static final String MARKER_COLLECTION = "schema_migrations";
    static final String MARKER_ID = "ai-cost-agent-cache-2026-10";

    /** Prices per 1K tokens: {input, cacheRead, cacheWrite}, keyed by the normalized model name. */
    static final Map<String, BigDecimal[]> CATALOG = new HashMap<>();

    private static void put(String model, String perMillionInput, String perMillionCacheRead, String perMillionCacheWrite) {
        CATALOG.put(CalculateAiCostUseCase.normalize(model), new BigDecimal[] {
                perK(perMillionInput), perK(perMillionCacheRead), perK(perMillionCacheWrite)});
    }

    private static BigDecimal perK(String perMillion) {
        return new BigDecimal(perMillion).movePointLeft(3);
    }

    static {
        // docs/ai-price-sources.md, "Verificacao de 2026-10-29" (USD per 1M tokens, divided by 1000 here)
        put("claude-fable-5.1", "10", "0.25", "12.50");
        put("claude-fable-5", "10", "1", "12.50");
        put("claude-opus-5.5", "4", "0.20", "5");
        put("claude-opus-5", "5", "0.50", "6.25");
        put("claude-opus-4.8", "5", "0.50", "6.25");
        put("claude-opus-4.7", "5", "0.50", "6.25");
        put("claude-opus-4.6", "5", "0.50", "6.25");
        put("claude-sonnet-5.5", "2", "0.10", "2.50");
        put("claude-sonnet-5", "2", "0.20", "2.50");
        put("claude-sonnet-4.6", "3", "0.30", "3.75");
        put("claude-haiku-5.5", "0.10", "0.01", "0.125");
        put("claude-haiku-4.5", "1", "0.10", "1.25");
        put("gpt-6-astra", "10", "1", "12.50");
        put("gpt-6.1-sol", "2", "0.10", "2.50");
        put("gpt-6-sol", "2", "0.20", "2.50");
        put("gpt-6.1-terra", "1", "0.05", "1.25");
        put("gpt-6-luna", "0.10", "0.01", "0.125");
        put("gpt-5.6-sol", "4", "0.40", "5");
        put("gpt-5.6-terra", "2", "0.20", "2.50");
        put("gpt-5.6-luna", "0.20", "0.02", "0.25");
        put("gpt-5.5", "5", "0.50", "0");
        put("gpt-5.4", "2.50", "0.25", "0");
        put("gpt-5.4-mini", "0.75", "0.075", "0");
        put("gpt-5.4-nano", "0.20", "0.02", "0");
        put("gpt-5.2", "1.75", "0.175", "0");
        put("gpt-5.1", "1.25", "0.125", "0");
        put("gpt-5", "1.25", "0.125", "0");
        put("gpt-5-mini", "0.25", "0.025", "0");
        put("gpt-5-nano", "0.05", "0.005", "0");
        put("gpt-4.1", "2", "0.50", "0");
        put("gpt-4.1-mini", "0.40", "0.10", "0");
        put("gpt-4.1-nano", "0.10", "0.025", "0");
        put("gpt-4o", "2.50", "1.25", "0");
        put("gpt-4o-mini", "0.15", "0.075", "0");
        put("gemini-4-argon", "2", "0.10", "0");
        put("gemini-3.8-flash", "0.75", "0.075", "0");
        put("gemini-3.6-flash", "0.75", "0.075", "0");
        put("gemini-3.5-flash-lite", "0.30", "0.03", "0");
        put("gemini-3.1-flash-lite", "0.25", "0.025", "0");
        put("gemini-3-flash-preview", "0.50", "0.05", "0");
        put("gemini-3.1-pro-preview", "2", "0.20", "0");
        put("gemini-2.5-pro", "1.25", "0.125", "0");
        put("gemini-2.5-flash", "0.30", "0.03", "0");
        put("gemini-2.5-flash-lite", "0.10", "0.01", "0");
    }

    private final MongoTemplate mongoTemplate;
    private final OrganizationRepository organizationRepository;
    private final RecalculateAiCostsUseCase recalculateAiCostsUseCase;

    @Override
    public void run(ApplicationArguments args) {
        if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(MARKER_ID)), MARKER_COLLECTION)) {
            log.info("AiCostAgentCacheMigration: marker present - skipping");
            return;
        }

        List<String> organizationIds = mongoTemplate.findDistinct(
                Query.query(Criteria.where("source").is("AGENT").and("active").is(true)),
                "organizationId", "ai_usages", String.class);

        int failed = 0;
        long filled = 0;
        long changed = 0;
        for (String organizationId : organizationIds) {
            try {
                filled += fillPrices(organizationId);
                changed += recalculateAiCostsUseCase.execute(organizationId);
            } catch (RuntimeException e) {
                failed++;
                log.error("AiCostAgentCacheMigration: failed for org={}", organizationId, e);
            }
        }

        if (failed > 0) {
            log.warn("AiCostAgentCacheMigration: {} of {} organizations failed - marker not written, will retry next boot",
                    failed, organizationIds.size());
            return;
        }

        mongoTemplate.save(new Document("_id", MARKER_ID)
                .append("ranAt", Instant.now().toString())
                .append("organizations", organizationIds.size())
                .append("priceFieldsFilled", filled)
                .append("recordsChanged", changed), MARKER_COLLECTION);
        log.info("AiCostAgentCacheMigration: {} organizations, {} price fields filled, {} records changed",
                organizationIds.size(), filled, changed);
    }

    /** Fills only null fields of existing entries; saves the organization only if something changed. */
    private int fillPrices(String organizationId) {
        Optional<Organization> found = organizationRepository.findById(organizationId);
        if (found.isEmpty() || found.get().getAiPrices() == null) return 0;
        Organization org = found.get();

        int filled = 0;
        List<AiPriceEntry> updated = new ArrayList<>();
        for (AiPriceEntry e : org.getAiPrices()) {
            BigDecimal[] c = e.getModel() == null ? null : CATALOG.get(CalculateAiCostUseCase.normalize(e.getModel()));
            if (c == null) {
                updated.add(e);
                continue;
            }
            BigDecimal in = e.getPricePerKInput(), cr = e.getPricePerKCacheRead(), cw = e.getPricePerKCacheWrite();
            if (in == null) { in = c[0]; filled++; }
            if (cr == null) { cr = c[1]; filled++; }
            if (cw == null) { cw = c[2]; filled++; }
            updated.add(new AiPriceEntry(e.getModel(), in, e.getPricePerKOutput(), cr, cw));
        }
        if (filled > 0) {
            org.setAiPrices(updated);
            organizationRepository.save(org);
        }
        return filled;
    }
}
