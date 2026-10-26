package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.FieldMappingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.TypeMappingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.WorkflowConfigDocument;
import andromeda.sei.ms.feature.sync.application.ProjectMetricsRecalculator;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-time migration (complexidade-esforco): until now StoryPoints was mapped as Effort, so story-tier
 * items carry their points in {@code effort}. From now on StoryPoints is Complexity and Effort is the
 * hours logged on Tasks. This moves {@code effort -> complexity} on story-tier items of the affected
 * bindings (and rewrites CUSTOM mappings), then recalculates the affected projects.
 *
 * <p>Idempotent twice over: a marker document in {@code schema_migrations} skips the whole run, and
 * even without it the selection is data-based (items that already hold a complexity, bindings that
 * already map COMPLEXITY, manual overrides) so a second run changes nothing.
 */
@Slf4j
@Component
@Order(30)
public class ComplexityFromStoryPointsMigration implements ApplicationRunner {

    static final String MARKER_COLLECTION = "schema_migrations";
    static final String MARKER_ID = "ComplexityFromStoryPointsMigration";
    private static final String STORY_POINTS = "Microsoft.VSTS.Scheduling.StoryPoints";
    private static final String COMPLETED_WORK = "Microsoft.VSTS.Scheduling.CompletedWork";
    private static final List<String> STORY_TIER = List.of(
            WorkItem.ResolvedType.STORY.name(), WorkItem.ResolvedType.FEATURE.name(), WorkItem.ResolvedType.BUG.name());

    private final MongoTemplate mongoTemplate;
    private final ProjectMetricsRecalculator recalculator;

    public ComplexityFromStoryPointsMigration(MongoTemplate mongoTemplate, ProjectMetricsRecalculator recalculator) {
        this.mongoTemplate = mongoTemplate;
        this.recalculator = recalculator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mongoTemplate.exists(Query.query(Criteria.where("_id").is(MARKER_ID)), MARKER_COLLECTION)) {
            log.info("ComplexityFromStoryPointsMigration: marker present - skipping");
            return;
        }

        int bindingsChanged = 0;
        long itemsChanged = 0;
        Set<String> affected = new LinkedHashSet<>();

        List<AzureProjectBindingDocument> bindings = mongoTemplate.find(
                Query.query(Criteria.where("active").is(true)), AzureProjectBindingDocument.class);
        for (AzureProjectBindingDocument binding : bindings) {
            WorkflowConfigDocument config = binding.getWorkflowConfig();
            boolean custom = config != null && "CUSTOM".equals(config.getConfigSource());
            if (custom && !isCustomTarget(config)) {
                continue;
            }
            List<FieldMappingDocument> originalMappings = custom ? config.getFieldMappings() : null;

            boolean mappingChanged = false;
            if (custom) {
                config.setFieldMappings(rewriteMappings(config));
                binding.setUpdatedAt(Instant.now());
                mongoTemplate.save(binding);
                bindingsChanged++;
                mappingChanged = true;
            }

            long moved = migrateItems(binding, originalMappings);
            itemsChanged += moved;
            if (moved > 0 || mappingChanged) {
                affected.add(binding.getOrganizationId() + "\u0000" + binding.getProjectId());
            }
        }

        for (String key : affected) {
            String[] parts = key.split("\u0000", 2);
            try {
                recalculator.recalculate(parts[0], parts[1]);
            } catch (RuntimeException e) {
                log.warn("ComplexityFromStoryPointsMigration: recalculation failed for project {}", parts[1], e);
            }
        }

        mongoTemplate.save(new Document("_id", MARKER_ID)
                .append("ranAt", Instant.now().toString())
                .append("bindingsChanged", bindingsChanged)
                .append("itemsChanged", itemsChanged), MARKER_COLLECTION);
        log.info("ComplexityFromStoryPointsMigration: {} custom bindings rewritten, {} items moved effort -> complexity, {} projects recalculated",
                bindingsChanged, itemsChanged, affected.size());
    }

    /** CUSTOM binding that maps StoryPoints as EFFORT and has not mapped COMPLEXITY yet. */
    private boolean isCustomTarget(WorkflowConfigDocument config) {
        List<FieldMappingDocument> mappings = config.getFieldMappings();
        if (mappings == null) return false;
        boolean hasComplexity = mappings.stream().anyMatch(m -> "COMPLEXITY".equals(m.getInternalField()));
        boolean effortOnStoryPoints = mappings.stream().anyMatch(m -> "EFFORT".equals(m.getInternalField())
                && STORY_POINTS.equals(m.getAzureFieldReferenceName()));
        return !hasComplexity && effortOnStoryPoints;
    }

    private List<FieldMappingDocument> rewriteMappings(WorkflowConfigDocument config) {
        List<FieldMappingDocument> result = new ArrayList<>();
        for (FieldMappingDocument m : config.getFieldMappings()) {
            if ("EFFORT".equals(m.getInternalField()) && STORY_POINTS.equals(m.getAzureFieldReferenceName())) {
                result.add(FieldMappingDocument.builder()
                        .internalField("COMPLEXITY")
                        .workItemType(m.getWorkItemType())
                        .azureFieldReferenceName(m.getAzureFieldReferenceName())
                        .azureFieldType(m.getAzureFieldType())
                        .build());
            } else {
                result.add(m);
            }
        }
        if (config.getTypeMappings() != null) {
            for (TypeMappingDocument t : config.getTypeMappings()) {
                if (t.getResolvedType() != WorkItem.ResolvedType.TASK
                        && t.getResolvedType() != WorkItem.ResolvedType.SUB_TASK) {
                    continue;
                }
                if (fieldFor(result, "EFFORT", t.getRawType()) == null) {
                    result.add(FieldMappingDocument.builder()
                            .internalField("EFFORT")
                            .workItemType(t.getRawType())
                            .azureFieldReferenceName(COMPLETED_WORK)
                            .azureFieldType("double")
                            .build());
                }
            }
        }
        return result;
    }

    /** Field mapped to {@code internalField} for the raw type: exact type first, then the wildcard (null type). */
    private String fieldFor(List<FieldMappingDocument> mappings, String internalField, String rawType) {
        if (mappings == null) return null;
        for (FieldMappingDocument m : mappings) {
            if (internalField.equals(m.getInternalField()) && m.getWorkItemType() != null
                    && m.getWorkItemType().equalsIgnoreCase(rawType)) {
                return m.getAzureFieldReferenceName();
            }
        }
        for (FieldMappingDocument m : mappings) {
            if (internalField.equals(m.getInternalField()) && m.getWorkItemType() == null) {
                return m.getAzureFieldReferenceName();
            }
        }
        return null;
    }

    private long migrateItems(AzureProjectBindingDocument binding, List<FieldMappingDocument> originalMappings) {
        Query query = Query.query(Criteria.where("organizationId").is(binding.getOrganizationId())
                .and("projectId").is(binding.getProjectId())
                .and("active").is(true)
                .and("resolvedType").in(STORY_TIER)
                .and("complexity").is(null)
                .and("effort").ne(null)
                .and("complexitySource").ne(FieldValueSource.MANUAL_OVERRIDE.name())
                .and("effortSource").ne(FieldValueSource.MANUAL_OVERRIDE.name()));

        long moved = 0;
        for (WorkItemDocument doc : mongoTemplate.find(query, WorkItemDocument.class)) {
            if (originalMappings != null
                    && !STORY_POINTS.equals(fieldFor(originalMappings, "EFFORT", doc.getRawType()))) {
                continue;
            }
            Update update = new Update()
                    .set("complexity", doc.getEffort())
                    .set("complexitySource", FieldValueSource.AZURE_SYNC.name())
                    .unset("effort")
                    .unset("effortSource")
                    .unset("storyPoints")
                    .set("updatedAt", Instant.now());
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(doc.getId())), update, WorkItemDocument.class);
            moved++;
        }
        return moved;
    }
}
