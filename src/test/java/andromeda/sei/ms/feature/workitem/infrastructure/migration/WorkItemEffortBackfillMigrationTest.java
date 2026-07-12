package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import andromeda.sei.ms.feature.workitem.domain.FieldValueSource;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkItemEffortBackfillMigrationTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private WorkItemEffortBackfillMigration migration;

    @Test
    void shouldQueryOnlyActiveDocumentsMissingEffortWithNonNullStoryPoints() {
        when(mongoTemplate.find(any(Query.class), eq(WorkItemDocument.class))).thenReturn(List.of());

        migration.run(applicationArguments);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(queryCaptor.capture(), eq(WorkItemDocument.class));

        String queryJson = queryCaptor.getValue().getQueryObject().toJson();
        assertThat(queryJson).contains("\"active\"");
        assertThat(queryJson).contains("\"effort\"");
        assertThat(queryJson).contains("\"$exists\": false");
        assertThat(queryJson).contains("\"storyPoints\"");
    }

    @Test
    void shouldSkipUpdatesWhenNoPendingDocuments() {
        when(mongoTemplate.find(any(Query.class), eq(WorkItemDocument.class))).thenReturn(List.of());

        migration.run(applicationArguments);

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(WorkItemDocument.class));
    }

    @Test
    void shouldBackfillEffortFromStoryPointsForEachPendingDocument() {
        WorkItemDocument doc1 = WorkItemDocument.builder().id("wi-1").storyPoints(5.0).build();
        WorkItemDocument doc2 = WorkItemDocument.builder().id("wi-2").storyPoints(8.0).build();
        when(mongoTemplate.find(any(Query.class), eq(WorkItemDocument.class))).thenReturn(List.of(doc1, doc2));

        migration.run(applicationArguments);

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(2)).updateFirst(any(Query.class), updateCaptor.capture(), eq(WorkItemDocument.class));

        List<Update> updates = updateCaptor.getAllValues();
        assertThat(updates).hasSize(2);
        assertThat(updates.get(0).getUpdateObject().toJson()).contains("\"effort\": 5.0");
        assertThat(updates.get(0).getUpdateObject().toJson()).contains(FieldValueSource.AZURE_SYNC.name());
        assertThat(updates.get(1).getUpdateObject().toJson()).contains("\"effort\": 8.0");
    }

    @Test
    void shouldBeIdempotentBecauseQueryExcludesDocumentsThatAlreadyHaveEffort() {
        // Simulates a second run: the query itself (asserted above) filters out documents
        // where "effort" already exists, so a re-run naturally returns an empty pending list
        // and performs no further updates — this is the idempotency guarantee by construction.
        when(mongoTemplate.find(any(Query.class), eq(WorkItemDocument.class))).thenReturn(List.of());

        migration.run(applicationArguments);
        migration.run(applicationArguments);

        verify(mongoTemplate, times(2)).find(any(Query.class), eq(WorkItemDocument.class));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(WorkItemDocument.class));
    }
}
