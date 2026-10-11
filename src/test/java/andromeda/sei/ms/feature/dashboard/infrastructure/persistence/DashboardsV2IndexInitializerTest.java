package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.integration.infrastructure.persistence.IntegrationSyncLogDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * dashboards-customizaveis 1.14 — the indexes behind the organization/integrations reads are created
 * (non-unique, organization first, keys of an existing declaration) and a conflict never stops the startup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardsV2IndexInitializerTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private IndexOperations indexOps;

    @Test
    void should_createEveryIndex_nonUnique_withTheOrganizationFirst() {
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(indexOps);

        new DashboardsV2IndexInitializer(mongoTemplate).run(null);

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOps, times(DashboardsV2IndexInitializer.INDEXES.size())).createIndex(captor.capture());
        for (IndexDefinition index : captor.getAllValues()) {
            assertThat(index.getIndexKeys().keySet().iterator().next()).isEqualTo("organizationId");
            assertThat(index.getIndexOptions().containsKey("unique")).isFalse();
        }
        verify(mongoTemplate, atLeastOnce()).indexOps(WorkItemDocument.class);
        assertThat(captor.getAllValues()).extracting(IndexDefinition::getIndexKeys).contains(
                new Document("organizationId", 1).append("integration", 1).append("startedAt", -1));
    }

    @Test
    void should_keepStarting_when_anIndexConflicts() {
        when(mongoTemplate.indexOps(any(Class.class))).thenReturn(indexOps);
        when(indexOps.createIndex(any())).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatCode(() -> new DashboardsV2IndexInitializer(mongoTemplate).run(null)).doesNotThrowAnyException();
        verify(mongoTemplate, atLeastOnce()).indexOps(IntegrationSyncLogDocument.class);
    }
}
