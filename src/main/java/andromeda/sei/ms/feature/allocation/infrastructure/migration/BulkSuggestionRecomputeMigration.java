package andromeda.sei.ms.feature.allocation.infrastructure.migration;

import andromeda.sei.ms.feature.allocation.application.usecase.AutoAssignAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ALO3: recomputes the pending automatic suggestions split over more than ten items (a bulk edit
 * in Azure made one day's AI usage spread over ~230 items at 0.43% each). The recompute applies
 * the item cap of {@link AutoAssignAllocationUseCase}: it falls back to the items in progress, or
 * leaves the day without suggestion.
 *
 * <p>Idempotent: a recomputed suggestion has at most ten items, so a second run finds nothing.
 * A failing day is logged and skipped; the startup never fails.
 */
@Slf4j
@Component
@Order(34)
@RequiredArgsConstructor
public class BulkSuggestionRecomputeMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;
    private final AutoAssignAllocationUseCase autoAssign;

    @Override
    public void run(ApplicationArguments args) {
        // splits.10 exists ⇔ more than ten splits
        Query query = Query.query(Criteria.where("status").is(AiUsageAllocation.Status.PENDING_VALIDATION.name())
                .and("assignedBy").is(AiUsageAllocation.AssignedBy.AUTO.name())
                .and("active").is(true)
                .and("splits.10").exists(true));
        List<AiUsageAllocationDocument> bulk = mongoTemplate.find(query, AiUsageAllocationDocument.class);
        if (bulk.isEmpty()) {
            log.debug("BulkSuggestionRecomputeMigration: no pending suggestion over ten items — skipping");
            return;
        }

        int failed = 0;
        for (AiUsageAllocationDocument doc : bulk) {
            try {
                autoAssign.execute(doc.getOrganizationId(), doc.getUserId(), doc.getDay(), true);
            } catch (Exception e) {
                failed++;
                log.warn("BulkSuggestionRecomputeMigration: recompute failed for org={} user={} day={}",
                        doc.getOrganizationId(), doc.getUserId(), doc.getDay(), e);
            }
        }
        log.info("BulkSuggestionRecomputeMigration: {} pending suggestions over ten items recomputed ({} failed)",
                bulk.size() - failed, failed);
    }
}
