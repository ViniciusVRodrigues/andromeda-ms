package andromeda.sei.ms.feature.allocation.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time migration: nulls out allocationId on ai_usages records where the referenced
 * AiUsageAllocation has status = PENDING_VALIDATION.
 *
 * After the allocation pipeline fix (allocationId is now only stamped on VALIDATED allocations),
 * pre-existing records may have allocationId pointing to a PENDING_VALIDATION allocation.
 * This migration restores the correct semantic: allocationId == null means "not yet validated".
 *
 * Guard: skips if no pending allocation IDs exist in ai_usage_allocations (idempotent).
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class NullifyPendingAllocationIdMigration implements ApplicationRunner {

    private static final String AI_USAGES_COLLECTION = "ai_usages";
    private static final String ALLOCATIONS_COLLECTION = "ai_usage_allocations";
    private static final String STATUS_PENDING = "PENDING_VALIDATION";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        // Find all allocation IDs that are in PENDING_VALIDATION status
        Query pendingQuery = Query.query(Criteria.where("status").is(STATUS_PENDING).and("active").is(true));
        pendingQuery.fields().include("_id");

        List<org.bson.Document> pendingDocs = mongoTemplate.find(pendingQuery, org.bson.Document.class, ALLOCATIONS_COLLECTION);

        if (pendingDocs.isEmpty()) {
            log.debug("NullifyPendingAllocationIdMigration: no PENDING_VALIDATION allocations found — skipping");
            return;
        }

        List<String> pendingAllocationIds = pendingDocs.stream()
                .map(doc -> doc.getObjectId("_id").toHexString())
                .toList();

        // Count ai_usages records that reference any of these pending allocations
        Query affectedQuery = Query.query(Criteria.where("allocationId").in(pendingAllocationIds));
        long countBefore = mongoTemplate.count(affectedQuery, AI_USAGES_COLLECTION);

        if (countBefore == 0) {
            log.debug("NullifyPendingAllocationIdMigration: no ai_usages records reference PENDING_VALIDATION allocations — skipping");
            return;
        }

        log.warn("NullifyPendingAllocationIdMigration: found {} ai_usages records with allocationId referencing PENDING_VALIDATION allocations — nullifying",
                countBefore);

        Update nullifyUpdate = new Update().unset("allocationId");
        mongoTemplate.updateMulti(affectedQuery, nullifyUpdate, AI_USAGES_COLLECTION);

        long countAfter = mongoTemplate.count(
                Query.query(Criteria.where("allocationId").in(pendingAllocationIds)),
                AI_USAGES_COLLECTION);

        log.info("NullifyPendingAllocationIdMigration: complete — nullified {} records (before={}, after={})",
                countBefore - countAfter, countBefore, countAfter);
    }
}
