package andromeda.sei.ms.feature.aiusage.infrastructure.migration;

import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
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

import java.time.Instant;
import java.util.List;

/**
 * One-time migration (custo-ia-saida): the cost is now output tokens x output price only, so the
 * stored {@code costUsd} of every organization's usage is recalculated once.
 *
 * <p>A marker in {@code schema_migrations} skips later boots. A failure in one organization is logged
 * and the others still run; the marker is only written when all of them pass, so the next boot
 * retries.
 */
@Slf4j
@Component
@Order(31)
@RequiredArgsConstructor
public class AiCostOutputOnlyMigration implements ApplicationRunner {

    static final String MARKER_COLLECTION = "schema_migrations";
    static final String MARKER_ID = "ai-cost-output-only-2026-10";

    private final MongoTemplate mongoTemplate;
    private final RecalculateAiCostsUseCase recalculateAiCostsUseCase;

    @Override
    public void run(ApplicationArguments args) {
        if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(MARKER_ID)), MARKER_COLLECTION)) {
            log.info("AiCostOutputOnlyMigration: marker present - skipping");
            return;
        }

        List<String> organizationIds = mongoTemplate.findDistinct(
                new Query(), "organizationId", "ai_usages", String.class);

        int failed = 0;
        long changed = 0;
        for (String organizationId : organizationIds) {
            try {
                changed += recalculateAiCostsUseCase.execute(organizationId);
            } catch (RuntimeException e) {
                failed++;
                log.error("AiCostOutputOnlyMigration: recalculation failed for org={}", organizationId, e);
            }
        }

        if (failed > 0) {
            log.warn("AiCostOutputOnlyMigration: {} of {} organizations failed - marker not written, will retry next boot",
                    failed, organizationIds.size());
            return;
        }

        mongoTemplate.save(new Document("_id", MARKER_ID)
                .append("ranAt", Instant.now().toString())
                .append("organizations", organizationIds.size())
                .append("recordsChanged", changed), MARKER_COLLECTION);
        log.info("AiCostOutputOnlyMigration: {} organizations recalculated, {} records changed",
                organizationIds.size(), changed);
    }
}
