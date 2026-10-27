package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.FieldMappingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.TypeMappingDocument;
import andromeda.sei.ms.feature.azure.infrastructure.persistence.AzureProjectBindingDocument.WorkflowConfigDocument;
import andromeda.sei.ms.feature.sync.application.ProjectMetricsRecalculator;
import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@MongoIntegrationTest
class ComplexityFromStoryPointsMigrationTest {

    private static final String ORG = "org-1";
    private static final String STORY_POINTS = "Microsoft.VSTS.Scheduling.StoryPoints";
    private static final String COMPLETED_WORK = "Microsoft.VSTS.Scheduling.CompletedWork";

    @Autowired
    private MongoTemplate mongo;

    private ProjectMetricsRecalculator recalculator;
    private ComplexityFromStoryPointsMigration migration;

    @BeforeEach
    void setUp() {
        mongo.dropCollection(WorkItemDocument.class);
        mongo.dropCollection(AzureProjectBindingDocument.class);
        mongo.dropCollection("schema_migrations");
        recalculator = Mockito.mock(ProjectMetricsRecalculator.class);
        migration = new ComplexityFromStoryPointsMigration(mongo, recalculator);
    }

    // ---- fixtures ----

    private void defaultBinding(String projectId) {
        mongo.save(AzureProjectBindingDocument.builder().id("b-" + projectId).organizationId(ORG)
                .projectId(projectId).active(true)
                .workflowConfig(WorkflowConfigDocument.builder().configSource("DEFAULT")
                        .fieldMappings(List.of()).build())
                .build());
    }

    private void customBinding(String projectId, List<FieldMappingDocument> mappings) {
        mongo.save(AzureProjectBindingDocument.builder().id("b-" + projectId).organizationId(ORG)
                .projectId(projectId).active(true)
                .workflowConfig(WorkflowConfigDocument.builder().configSource("CUSTOM")
                        .typeMappings(List.of(
                                TypeMappingDocument.builder().rawType("User Story").resolvedType(WorkItem.ResolvedType.STORY).build(),
                                TypeMappingDocument.builder().rawType("Task").resolvedType(WorkItem.ResolvedType.TASK).build()))
                        .fieldMappings(mappings).build())
                .build());
    }

    private FieldMappingDocument field(String target, String type, String azureField) {
        return FieldMappingDocument.builder().internalField(target).workItemType(type)
                .azureFieldReferenceName(azureField).azureFieldType("double").build();
    }

    private WorkItemDocument item(String id, String projectId, String rawType, WorkItem.ResolvedType type,
                                  Double effort, Double complexity) {
        WorkItemDocument saved = mongo.save(WorkItemDocument.builder().id(id).organizationId(ORG).projectId(projectId)
                .externalId("ext-" + id).rawType(rawType).resolvedType(type).active(true)
                .effort(effort).effortSource(effort == null ? null : FieldValueSource.AZURE_SYNC)
                .complexity(complexity).complexitySource(complexity == null ? null : FieldValueSource.AZURE_SYNC)
                .build());
        // the removed storyPoints field only exists in old documents: write it raw
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)), new Update().set("storyPoints", effort),
                "work_items");
        return saved;
    }

    private org.bson.Document rawItem(String id) {
        return mongo.findById(id, org.bson.Document.class, "work_items");
    }

    private WorkItemDocument reload(String id) {
        return mongo.findById(id, WorkItemDocument.class);
    }

    private AzureProjectBindingDocument binding(String projectId) {
        return mongo.findById("b-" + projectId, AzureProjectBindingDocument.class);
    }

    // ---- items ----

    @Test
    void should_moveEffortToComplexityOnStoryItems_ofADefaultBinding() {
        defaultBinding("p1");
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        item("b1", "p1", "Bug", WorkItem.ResolvedType.BUG, 2.0, null);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isEqualTo(5.0);
        assertThat(reload("s1").getComplexitySource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(reload("s1").getEffort()).isNull();
        assertThat(rawItem("s1")).doesNotContainKey("storyPoints");
        assertThat(reload("b1").getComplexity()).isEqualTo(2.0);
        assertThat(reload("b1").getEffort()).isNull();
    }

    @Test
    void should_leaveTaskItemsUntouched() {
        defaultBinding("p1");
        item("t1", "p1", "Task", WorkItem.ResolvedType.TASK, 6.0, null);

        migration.run(null);

        assertThat(reload("t1").getEffort()).isEqualTo(6.0);
        assertThat(reload("t1").getComplexity()).isNull();
    }

    @Test
    void should_keepManualOverrides() {
        defaultBinding("p1");
        WorkItemDocument manualComplexity = item("m1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        manualComplexity.setComplexitySource(FieldValueSource.MANUAL_OVERRIDE);
        mongo.save(manualComplexity);
        WorkItemDocument manualEffort = item("m2", "p1", "User Story", WorkItem.ResolvedType.STORY, 8.0, null);
        manualEffort.setEffortSource(FieldValueSource.MANUAL_OVERRIDE);
        mongo.save(manualEffort);

        migration.run(null);

        assertThat(reload("m1").getComplexity()).isNull();
        assertThat(reload("m1").getEffort()).isEqualTo(5.0);
        assertThat(reload("m2").getComplexity()).isNull();
        assertThat(reload("m2").getEffort()).isEqualTo(8.0);
    }

    @Test
    void should_notOverwriteAnExistingComplexity() {
        defaultBinding("p1");
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, 3.0);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isEqualTo(3.0);
        assertThat(reload("s1").getEffort()).isEqualTo(5.0);
    }

    @Test
    void should_ignoreInactiveItems() {
        defaultBinding("p1");
        WorkItemDocument inactive = item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        inactive.setActive(false);
        mongo.save(inactive);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isNull();
    }

    @Test
    void should_notTouchItemsOfOtherProjects_orOrganizations() {
        defaultBinding("p1");
        item("other-project", "p2", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        WorkItemDocument otherOrg = item("other-org", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        otherOrg.setOrganizationId("org-2");
        mongo.save(otherOrg);

        migration.run(null);

        assertThat(reload("other-project").getComplexity()).isNull();
        assertThat(reload("other-org").getComplexity()).isNull();
    }

    // ---- custom bindings ----

    @Test
    void should_rewriteTheMapping_andMigrateItems_ofACustomBindingThatMapsStoryPointsAsEffort() {
        customBinding("p1", List.of(field("EFFORT", null, STORY_POINTS)));
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);

        migration.run(null);

        List<FieldMappingDocument> mappings = binding("p1").getWorkflowConfig().getFieldMappings();
        assertThat(mappings).anySatisfy(m -> {
            assertThat(m.getInternalField()).isEqualTo("COMPLEXITY");
            assertThat(m.getAzureFieldReferenceName()).isEqualTo(STORY_POINTS);
            assertThat(m.getWorkItemType()).isNull();
        });
        assertThat(mappings).anySatisfy(m -> {
            assertThat(m.getInternalField()).isEqualTo("EFFORT");
            assertThat(m.getAzureFieldReferenceName()).isEqualTo(COMPLETED_WORK);
            assertThat(m.getWorkItemType()).isEqualTo("Task");
        });
        assertThat(mappings).noneMatch(m -> "EFFORT".equals(m.getInternalField())
                && STORY_POINTS.equals(m.getAzureFieldReferenceName()));
        assertThat(reload("s1").getComplexity()).isEqualTo(5.0);
        assertThat(reload("s1").getEffort()).isNull();
    }

    @Test
    void should_skipACustomBindingThatAlreadyMapsComplexity() {
        List<FieldMappingDocument> mappings = List.of(
                field("COMPLEXITY", "User Story", STORY_POINTS),
                field("EFFORT", "Task", COMPLETED_WORK));
        customBinding("p1", mappings);
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);

        migration.run(null);

        assertThat(binding("p1").getWorkflowConfig().getFieldMappings()).hasSize(2);
        assertThat(reload("s1").getEffort()).isEqualTo(5.0);
        assertThat(reload("s1").getComplexity()).isNull();
        verify(recalculator, never()).recalculate(anyString(), anyString());
    }

    @Test
    void should_skipACustomBindingWhoseEffortIsAnotherField() {
        customBinding("p1", List.of(field("EFFORT", null, COMPLETED_WORK)));
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isNull();
        assertThat(reload("s1").getEffort()).isEqualTo(5.0);
    }

    @Test
    void should_migrateOnlyTheTypesWhoseEffortWasStoryPoints_inACustomBinding() {
        customBinding("p1", List.of(
                field("EFFORT", "User Story", STORY_POINTS),
                field("EFFORT", "Bug", COMPLETED_WORK)));
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        item("b1", "p1", "Bug", WorkItem.ResolvedType.BUG, 4.0, null);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isEqualTo(5.0);
        assertThat(reload("b1").getComplexity()).isNull();
        assertThat(reload("b1").getEffort()).isEqualTo(4.0);
    }

    // ---- idempotence and recalculation ----

    @Test
    void should_changeNothing_onTheSecondRun() {
        defaultBinding("p1");
        customBinding("p2", List.of(field("EFFORT", null, STORY_POINTS)));
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        item("s2", "p2", "User Story", WorkItem.ResolvedType.STORY, 3.0, null);
        migration.run(null);
        List<WorkItemDocument> itemsAfterFirst = mongo.findAll(WorkItemDocument.class);
        AzureProjectBindingDocument bindingAfterFirst = binding("p2");

        migration.run(null);

        assertThat(mongo.findAll(WorkItemDocument.class)).containsExactlyInAnyOrderElementsOf(itemsAfterFirst);
        assertThat(binding("p2")).isEqualTo(bindingAfterFirst);
        verify(recalculator, times(2)).recalculate(anyString(), anyString());
    }

    @Test
    void should_changeNothing_evenWithoutTheMarker() {
        defaultBinding("p1");
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        migration.run(null);
        mongo.dropCollection("schema_migrations");
        Mockito.clearInvocations(recalculator);

        migration.run(null);

        assertThat(reload("s1").getComplexity()).isEqualTo(5.0);
        assertThat(reload("s1").getEffort()).isNull();
        verify(recalculator, never()).recalculate(anyString(), anyString());
    }

    @Test
    void should_recalculateOnlyTheAffectedProjects() {
        defaultBinding("p1");
        defaultBinding("p2");
        defaultBinding("p3");
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);
        item("s2", "p2", "User Story", WorkItem.ResolvedType.STORY, null, 3.0);
        item("t3", "p3", "Task", WorkItem.ResolvedType.TASK, 4.0, null);

        migration.run(null);

        verify(recalculator).recalculate(ORG, "p1");
        verify(recalculator, never()).recalculate(ORG, "p2");
        verify(recalculator, never()).recalculate(ORG, "p3");
    }

    @Test
    void should_recordTheMarker() {
        defaultBinding("p1");
        item("s1", "p1", "User Story", WorkItem.ResolvedType.STORY, 5.0, null);

        migration.run(null);

        assertThat(mongo.count(new Query(Criteria.where("_id").is("ComplexityFromStoryPointsMigration")),
                "schema_migrations")).isEqualTo(1);
    }
}
