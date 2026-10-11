package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code @CompoundIndex} does not create indexes in this application (QA found users, roles and
 * projects with only {@code _id_}; 16 parallel creates left 12 active layouts for one key), so the
 * initializer must create both {@code dashboard_layouts} indexes explicitly.
 */
@ExtendWith(MockitoExtension.class)
class DashboardLayoutIndexInitializerTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private IndexOperations indexOps;

    private Map<String, Index> created;

    @BeforeEach
    void runInitializer() {
        when(mongoTemplate.indexOps(DashboardLayoutDocument.class)).thenReturn(indexOps);

        new DashboardLayoutIndexInitializer(mongoTemplate).run(null);

        ArgumentCaptor<Index> captor = ArgumentCaptor.forClass(Index.class);
        verify(indexOps, times(2)).createIndex(captor.capture());
        verifyNoMoreInteractions(indexOps);
        created = captor.getAllValues().stream()
                .collect(Collectors.toMap(index -> index.getIndexOptions().getString("name"), index -> index));
    }

    @Test
    void should_createPartialUniqueIndex_onOrgUserView() {
        Index unique = created.get(DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX);

        assertThat(unique).isNotNull();
        assertThat(List.copyOf(unique.getIndexKeys().keySet())).containsExactly("organizationId", "userId", "viewKey");
        assertThat(unique.getIndexKeys().values()).containsOnly(1);
        assertThat(unique.getIndexOptions()).containsEntry("unique", true);
        assertThat(unique.getIndexOptions().get("partialFilterExpression", Document.class))
                .isEqualTo(new Document("active", true));
    }

    @Test
    void should_createOrgActiveIndex_notUnique_notPartial() {
        Index orgActive = created.get(DashboardLayoutDocument.ORG_ACTIVE_INDEX);

        assertThat(orgActive).isNotNull();
        assertThat(List.copyOf(orgActive.getIndexKeys().keySet())).containsExactly("organizationId", "active");
        assertThat(orgActive.getIndexKeys().values()).containsOnly(1);
        assertThat(orgActive.getIndexOptions()).doesNotContainKey("unique").doesNotContainKey("partialFilterExpression");
    }

    @Test
    void should_matchTheDeclaredCompoundIndexes_soNoOptionsConflictCanArise() {
        Map<String, CompoundIndex> declared = Arrays.stream(
                        DashboardLayoutDocument.class.getAnnotation(CompoundIndexes.class).value())
                .collect(Collectors.toMap(CompoundIndex::name, index -> index));

        assertThat(created.keySet()).containsExactlyInAnyOrderElementsOf(declared.keySet());
        created.forEach((name, index) -> {
            assertThat(index.getIndexKeys()).isEqualTo(Document.parse(declared.get(name).def()));
            assertThat(index.getIndexOptions().containsKey("unique")).isEqualTo(declared.get(name).unique());
        });
        assertThat(Document.parse(declared.get(DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX).partialFilter()))
                .isEqualTo(created.get(DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX)
                        .getIndexOptions().get("partialFilterExpression", Document.class));
    }

    @Test
    void should_runWithTheOtherIndexInitializers_beforeDataMigrations() {
        int order = DashboardLayoutIndexInitializer.class.getAnnotation(Order.class).value();

        assertThat(order).isEqualTo(11).isLessThan(20);
    }
}
