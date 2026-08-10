package andromeda.sei.ms.feature.azure.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time migration: adds the {@code workItemType} discriminator to existing
 * {@code azure_project_bindings.workflowConfig.fieldMappings} entries.
 *
 * <p>Field mappings used to be global per organization — one Azure field per internal target
 * (COMPLEXITY / EFFORT). That can't express how Azure DevOps actually stores these values, since
 * the field differs per work item type: User Story carries {@code StoryPoints}, Task carries
 * {@code CompletedWork}/{@code OriginalEstimate}, and Feature/Epic carry {@code Effort}. With a
 * single global mapping only one level of the tree ever gets a value, which is exactly how a real
 * project ended up with complexity on its User Stories, effort on its Tasks, and nothing on
 * Features or Epics.
 *
 * <p>This migration is deliberately conservative: it does NOT guess per-type mappings. It only
 * writes an explicit {@code workItemType: null} onto legacy entries that lack the key, making them
 * wildcard entries ("applies to any type") — which is precisely the behaviour they already had.
 * Existing syncs therefore keep producing identical results; the org can then add per-type
 * mappings through the workflow-config wizard without ambiguity about what the legacy row meant.
 *
 * <p>Idempotent: entries that already carry the key (including an explicit null) are left alone,
 * so re-running after a partial run or a fresh deploy is safe.
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class FieldMappingWorkItemTypeMigration implements ApplicationRunner {

    private static final String COLLECTION = "azure_project_bindings";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<Document> bindings = mongoTemplate.getCollection(COLLECTION)
                .find(new Document("workflowConfig.fieldMappings", new Document("$exists", true)))
                .into(new ArrayList<>());

        if (bindings.isEmpty()) {
            log.info("FieldMappingWorkItemTypeMigration: no bindings with fieldMappings — nothing to migrate");
            return;
        }

        int updated = 0;
        for (Document binding : bindings) {
            Document workflowConfig = binding.get("workflowConfig", Document.class);
            if (workflowConfig == null) continue;

            @SuppressWarnings("unchecked")
            List<Document> mappings = (List<Document>) workflowConfig.get("fieldMappings");
            if (mappings == null || mappings.isEmpty()) continue;

            boolean dirty = false;
            for (Document mapping : mappings) {
                if (!mapping.containsKey("workItemType")) {
                    mapping.put("workItemType", null); // wildcard: same semantics as before
                    dirty = true;
                }
            }
            if (!dirty) continue;

            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(binding.get("_id"))),
                    new Update().set("workflowConfig.fieldMappings", mappings),
                    COLLECTION);
            updated++;
        }

        log.info("FieldMappingWorkItemTypeMigration: migration complete, {} binding(s) updated", updated);
    }
}
