package andromeda.sei.ms.feature.aiusage.infrastructure.migration;

import andromeda.sei.ms.feature.aiusage.application.usecase.RecalculateAiCostsUseCase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCostOutputOnlyMigrationTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private RecalculateAiCostsUseCase recalculate;
    @Mock private ApplicationArguments args;

    private AiCostOutputOnlyMigration migration;

    @BeforeEach
    void setUp() {
        migration = new AiCostOutputOnlyMigration(mongoTemplate, recalculate);
    }

    @Test
    void should_skip_when_markerPresent() {
        when(mongoTemplate.exists(any(Query.class), eq("schema_migrations"))).thenReturn(true);

        migration.run(args);

        verify(recalculate, never()).execute(anyString());
        verify(mongoTemplate, never()).save(any(Document.class), eq("schema_migrations"));
    }

    @Test
    void should_recalculateEveryOrg_andWriteMarker() {
        when(mongoTemplate.exists(any(Query.class), eq("schema_migrations"))).thenReturn(false);
        when(mongoTemplate.findDistinct(any(Query.class), eq("organizationId"), eq("ai_usages"), eq(String.class)))
                .thenReturn(List.of("org-1", "org-2"));
        when(recalculate.execute("org-1")).thenReturn(3);
        when(recalculate.execute("org-2")).thenReturn(4);

        migration.run(args);

        ArgumentCaptor<Document> marker = ArgumentCaptor.forClass(Document.class);
        verify(mongoTemplate).save(marker.capture(), eq("schema_migrations"));
        assertThat(marker.getValue().get("_id")).isEqualTo("ai-cost-output-only-2026-10");
        assertThat(marker.getValue().get("recordsChanged")).isEqualTo(7L);
    }

    @Test
    void should_continueAfterAFailure_andNotWriteMarker() {
        when(mongoTemplate.exists(any(Query.class), eq("schema_migrations"))).thenReturn(false);
        when(mongoTemplate.findDistinct(any(Query.class), eq("organizationId"), eq("ai_usages"), eq(String.class)))
                .thenReturn(List.of("org-1", "org-2"));
        when(recalculate.execute("org-1")).thenThrow(new IllegalStateException("boom"));
        when(recalculate.execute("org-2")).thenReturn(1);

        migration.run(args);

        verify(recalculate).execute("org-2");
        verify(mongoTemplate, never()).save(any(Document.class), eq("schema_migrations"));
    }
}
