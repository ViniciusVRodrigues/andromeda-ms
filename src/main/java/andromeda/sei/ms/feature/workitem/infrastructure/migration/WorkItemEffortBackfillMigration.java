package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time migration: copies legacy storyPoints -> effort (with effortSource = AZURE_SYNC) for all
 * active work_items documents that predate the complexity/effort field split. Idempotent by design:
 * only documents where effort is not yet set are selected, so re-running after a partial run or a
 * fresh deploy is always safe and skips already-migrated documents.
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class WorkItemEffortBackfillMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query pendingQuery = Query.query(
                Criteria.where("active").is(true)
                        .and("effort").exists(false)
                        .and("storyPoints").ne(null));

        List<WorkItemDocument> pending = mongoTemplate.find(pendingQuery, WorkItemDocument.class);
        if (pending.isEmpty()) {
            log.info("WorkItemEffortBackfillMigration: no pending documents — nothing to migrate");
            return;
        }

        log.info("WorkItemEffortBackfillMigration: backfilling effort for {} work_items documents", pending.size());

        for (WorkItemDocument doc : pending) {
            Query byId = Query.query(Criteria.where("_id").is(doc.getId()));
            Update update = new Update()
                    .set("effort", doc.getStoryPoints())
                    .set("effortSource", FieldValueSource.AZURE_SYNC.name());
            mongoTemplate.updateFirst(byId, update, WorkItemDocument.class);
        }

        log.info("WorkItemEffortBackfillMigration: migration complete");
    }
}
