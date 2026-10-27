package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkItemDocumentMapperTest {

    private final WorkItemDocumentMapper mapper = new WorkItemDocumentMapper();

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant OVERRIDDEN_AT = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void toDocument_shouldMapComplexityAndEffortFieldsWithAzureSyncSource() {
        WorkItem item = workItem(
                5.0, FieldValueSource.AZURE_SYNC, null, null,
                8.0, FieldValueSource.AZURE_SYNC, null, null);

        WorkItemDocument doc = mapper.toDocument(item);

        assertThat(doc.getComplexity()).isEqualTo(5.0);
        assertThat(doc.getComplexitySource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(doc.getComplexityOverriddenBy()).isNull();
        assertThat(doc.getComplexityOverriddenAt()).isNull();
        assertThat(doc.getEffort()).isEqualTo(8.0);
        assertThat(doc.getEffortSource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(doc.getEffortOverriddenBy()).isNull();
        assertThat(doc.getEffortOverriddenAt()).isNull();
    }

    @Test
    void toDocument_shouldMapManualOverrideAuditFields() {
        WorkItem item = workItem(
                13.0, FieldValueSource.MANUAL_OVERRIDE, "user-1", OVERRIDDEN_AT,
                21.0, FieldValueSource.MANUAL_OVERRIDE, "user-2", OVERRIDDEN_AT);

        WorkItemDocument doc = mapper.toDocument(item);

        assertThat(doc.getComplexity()).isEqualTo(13.0);
        assertThat(doc.getComplexitySource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
        assertThat(doc.getComplexityOverriddenBy()).isEqualTo("user-1");
        assertThat(doc.getComplexityOverriddenAt()).isEqualTo(OVERRIDDEN_AT);
        assertThat(doc.getEffort()).isEqualTo(21.0);
        assertThat(doc.getEffortSource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
        assertThat(doc.getEffortOverriddenBy()).isEqualTo("user-2");
        assertThat(doc.getEffortOverriddenAt()).isEqualTo(OVERRIDDEN_AT);
    }

    @Test
    void toDomain_shouldRoundTripComplexityAndEffortFields() {
        WorkItem original = workItem(
                13.0, FieldValueSource.MANUAL_OVERRIDE, "user-1", OVERRIDDEN_AT,
                8.0, FieldValueSource.AZURE_SYNC, null, null);

        WorkItemDocument doc = mapper.toDocument(original);
        WorkItem roundTripped = mapper.toDomain(doc);

        assertThat(roundTripped.getComplexity()).isEqualTo(13.0);
        assertThat(roundTripped.getComplexitySource()).isEqualTo(FieldValueSource.MANUAL_OVERRIDE);
        assertThat(roundTripped.getComplexityOverriddenBy()).isEqualTo("user-1");
        assertThat(roundTripped.getComplexityOverriddenAt()).isEqualTo(OVERRIDDEN_AT);
        assertThat(roundTripped.getEffort()).isEqualTo(8.0);
        assertThat(roundTripped.getEffortSource()).isEqualTo(FieldValueSource.AZURE_SYNC);
        assertThat(roundTripped.getEffortOverriddenBy()).isNull();
        assertThat(roundTripped.getEffortOverriddenAt()).isNull();
    }

    @Test
    void toDocument_shouldMapNullComplexityAndEffortWhenUnmapped() {
        WorkItem item = workItem(null, null, null, null, null, null, null, null);

        WorkItemDocument doc = mapper.toDocument(item);

        assertThat(doc.getComplexity()).isNull();
        assertThat(doc.getComplexitySource()).isNull();
        assertThat(doc.getEffort()).isNull();
        assertThat(doc.getEffortSource()).isNull();
    }

    private WorkItem workItem(
            Double complexity, FieldValueSource complexitySource, String complexityOverriddenBy, Instant complexityOverriddenAt,
            Double effort, FieldValueSource effortSource, String effortOverriddenBy, Instant effortOverriddenAt) {
        return new WorkItem(
                "id-1", "org-1", "proj-1", WorkItem.Source.AZURE_DEVOPS,
                "ext-1", NOW, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "2", List.of("label"),
                "sprint-1", "assignee-ext", "assignee-user", "assignee@example.com",
                null, NOW, null, null,
                null, null,
                true, 1L, NOW,
                complexity, complexitySource, complexityOverriddenBy, complexityOverriddenAt,
                effort, effortSource, effortOverriddenBy, effortOverriddenAt);
    }
}
