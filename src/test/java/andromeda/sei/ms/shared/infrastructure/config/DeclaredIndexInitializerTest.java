package andromeda.sei.ms.shared.infrastructure.config;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * fix/mongo-index-creation — every {@code @CompoundIndex}/{@code @Indexed} declared on a document is
 * created at startup, one at a time, and a failing index never stops the others nor the startup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeclaredIndexInitializerTest {

    /* Pinned inventory: 29 @Document classes declaring 64 indexes (usage_events: ix_usage_org_occurred + TTL) (fix/mongo-index-creation report).
     * Adding or removing a declaration changes these numbers on purpose. */
    private static final int DOCUMENT_COLLECTIONS = 29;
    private static final int DECLARED_INDEXES = 64;

    private static final String USERS = "users";
    private static final String WORK_ITEMS = "work_items";
    private static final String AZURE_ID_INDEX = "uq_user_org_azureId";
    private static final String WORK_ITEM_NATURAL_KEY = "organizationId_1_externalId_1_source_1";

    @Mock private MongoTemplate mongoTemplate;
    @Mock private IndexOperations indexOps;

    private MongoMappingContext mappingContext;
    private List<DeclaredIndexCatalog.DeclaredIndex> catalog;

    @BeforeEach
    void setUp() {
        mappingContext = new MongoMappingContext();
        // Same simple types as the application context (java.time.* are values, not entities).
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        catalog = DeclaredIndexCatalog.resolve(mappingContext, DeclaredIndexInitializer.BASE_PACKAGE);
        when(mongoTemplate.indexOps(anyString())).thenReturn(indexOps);
        when(indexOps.getIndexInfo()).thenReturn(List.of());
    }

    @Test
    void should_resolveTheIndexesOfEveryDocument_notOnlyTheOnesAlreadyInTheMappingContext() {
        Set<String> collections = catalog.stream()
                .map(DeclaredIndexCatalog.DeclaredIndex::collection).collect(Collectors.toSet());

        assertThat(collections).hasSize(DOCUMENT_COLLECTIONS)
                .contains(USERS, WORK_ITEMS, "email_verifications", "password_resets", "vcs_review_events", "usage_events");
        assertThat(catalog).hasSize(DECLARED_INDEXES);
    }

    @Test
    void should_createEveryDeclaredIndex_onItsCollection() {
        DeclaredIndexInitializer.Summary summary = initializer().ensureAll();

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOps, times(DECLARED_INDEXES)).createIndex(captor.capture());
        verify(mongoTemplate, times(1)).indexOps(WORK_ITEMS); // one IndexOperations per collection
        assertThat(summary.created()).isEqualTo(DECLARED_INDEXES);
        assertThat(summary.existing()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(captor.getAllValues()).extracting(DeclaredIndexInitializerTest::nameOf)
                .contains(AZURE_ID_INDEX, "uq_user_email_active", "uq_ai_usage_natural_key", WORK_ITEM_NATURAL_KEY);
    }

    @Test
    void should_countAsExisting_theIndexesAlreadyOnTheCollection() {
        IndexOperations usersOps = mock(IndexOperations.class);
        when(mongoTemplate.indexOps(USERS)).thenReturn(usersOps);
        when(usersOps.getIndexInfo()).thenReturn(List.of(indexInfo("_id_"), indexInfo(AZURE_ID_INDEX)));

        DeclaredIndexInitializer.Summary summary = initializer().ensureAll();

        verify(usersOps, times(3)).createIndex(any());
        assertThat(summary.existing()).isEqualTo(1);
        assertThat(summary.created()).isEqualTo(DECLARED_INDEXES - 1);
    }

    @Test
    void should_createTheOtherIndexes_andNotStopTheStartup_when_oneIndexFails() {
        when(indexOps.createIndex(argThat(index -> index != null && WORK_ITEM_NATURAL_KEY.equals(nameOf(index)))))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error"));

        DeclaredIndexInitializer.Summary[] summary = new DeclaredIndexInitializer.Summary[1];
        assertThatCode(() -> summary[0] = initializer().ensureAll()).doesNotThrowAnyException();

        verify(indexOps, times(DECLARED_INDEXES)).createIndex(any());
        assertThat(summary[0].failed()).isEqualTo(1);
        assertThat(summary[0].created()).isEqualTo(DECLARED_INDEXES - 1);
    }

    @Test
    void should_notStopTheStartup_when_theIndexListOfACollectionCannotBeRead() {
        when(indexOps.getIndexInfo()).thenThrow(new IllegalStateException("listIndexes failed"));

        assertThatCode(() -> initializer().run(null)).doesNotThrowAnyException();
        verify(indexOps, times(DECLARED_INDEXES)).createIndex(any());
    }

    @Test
    void should_declareTheAzureIdIndex_asPartialOnStringValues_notSparse() {
        IndexDefinition azureId = declared(USERS, AZURE_ID_INDEX);

        assertThat(azureId.getIndexKeys()).isEqualTo(new Document("organizationId", 1).append("azureId", 1));
        assertThat(azureId.getIndexOptions().getBoolean("unique")).isTrue();
        assertThat(azureId.getIndexOptions().containsKey("sparse")).isFalse();
        assertThat(azureId.getIndexOptions().get("partialFilterExpression"))
                .isEqualTo(new Document("azureId", new Document("$type", "string")).append("active", true));
    }

    @Test
    void should_haveNoUniqueSparseCompoundIndex_becauseSparseIndexesADocumentWhenAnyKeyExists() {
        assertThat(catalog).noneMatch(index -> index.definition().getIndexKeys().size() > 1
                && Boolean.TRUE.equals(index.definition().getIndexOptions().get("unique"))
                && Boolean.TRUE.equals(index.definition().getIndexOptions().get("sparse")));
    }

    private DeclaredIndexInitializer initializer() {
        return new DeclaredIndexInitializer(mongoTemplate, mappingContext);
    }

    private IndexDefinition declared(String collection, String name) {
        return catalog.stream()
                .filter(index -> index.collection().equals(collection) && index.name().equals(name))
                .findFirst().orElseThrow().definition();
    }

    private static String nameOf(IndexDefinition index) {
        return DeclaredIndexCatalog.effectiveName(index);
    }

    private static IndexInfo indexInfo(String name) {
        return new IndexInfo(List.of(), name, false, false, "");
    }
}
