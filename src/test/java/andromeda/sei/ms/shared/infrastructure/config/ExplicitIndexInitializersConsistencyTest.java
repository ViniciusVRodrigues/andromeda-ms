package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.feature.dashboard.infrastructure.persistence.DashboardQueryIndexInitializer;
import andromeda.sei.ms.feature.dashboard.infrastructure.persistence.DashboardsV2IndexInitializer;
import andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence.DashboardLayoutIndexInitializer;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsIndexInitializer;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * fix/mongo-index-creation — the four explicit index initializers and the declared indexes now both
 * run at startup, so an explicit index with the keys of a declaration must carry the same name and the
 * same options (MongoDB refuses the second with IndexOptionsConflict / IndexKeySpecsConflict), and no
 * explicit index may reuse a declared name with other keys.
 */
class ExplicitIndexInitializersConsistencyTest {

    /** Explicit indexes with no declaration behind them (partial filters / keys no document declares). */
    private static final List<String> EXPLICIT_ONLY = List.of(
            "vcs_repositories.vcs_repository_identity",
            "vcs_pull_requests.idx_pr_org_repo_merged",
            "ai_usage_allocations.idx_allocation_org_split_workitem_day");

    private static final String NAME = "name";

    private MongoMappingContext mappingContext;
    private List<DeclaredIndexCatalog.DeclaredIndex> declared;
    private List<DeclaredIndexCatalog.DeclaredIndex> explicit;

    @BeforeEach
    void setUp() throws Exception {
        mappingContext = new MongoMappingContext();
        // Same simple types as the application context (java.time.* are values, not entities).
        mappingContext.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        declared = DeclaredIndexCatalog.resolve(mappingContext, DeclaredIndexInitializer.BASE_PACKAGE);
        explicit = captureExplicitIndexes();
    }

    @Test
    void should_captureTheIndexesOfAllFourInitializers() {
        assertThat(explicit).hasSize(9 + 5 + 2 + 1); // query + V2 + layout + vcs, see each initializer
    }

    @Test
    void should_useTheSameNameAndOptions_when_anExplicitIndexHasTheKeysOfADeclaration() {
        List<String> conflicts = new ArrayList<>();
        List<String> explicitOnly = new ArrayList<>();
        for (DeclaredIndexCatalog.DeclaredIndex index : explicit) {
            Optional<DeclaredIndexCatalog.DeclaredIndex> twin = declared.stream()
                    .filter(candidate -> candidate.collection().equals(index.collection())
                            && orderedKeys(candidate).equals(orderedKeys(index)))
                    .findFirst();
            if (twin.isEmpty()) {
                explicitOnly.add(index.collection() + "." + index.name());
                continue;
            }
            if (!twin.get().name().equals(index.name())
                    || !optionsWithoutName(twin.get()).equals(optionsWithoutName(index))) {
                conflicts.add(describe(index) + " <> declared " + describe(twin.get()));
            }
        }
        assertThat(conflicts).isEmpty();
        assertThat(explicitOnly).containsExactlyInAnyOrderElementsOf(EXPLICIT_ONLY);
    }

    @Test
    void should_neverReuseADeclaredName_withOtherKeys() {
        List<String> conflicts = new ArrayList<>();
        for (DeclaredIndexCatalog.DeclaredIndex index : explicit) {
            declared.stream()
                    .filter(candidate -> candidate.collection().equals(index.collection())
                            && candidate.name().equals(index.name())
                            && !orderedKeys(candidate).equals(orderedKeys(index)))
                    .forEach(candidate -> conflicts.add(describe(index) + " <> declared " + describe(candidate)));
        }
        assertThat(conflicts).isEmpty();
    }

    @Test
    void should_neverDeclareTwoIndexesWithTheSameNameOnACollection() {
        assertThat(declared).extracting(index -> index.collection() + "." + index.name()).doesNotHaveDuplicates();
    }

    private List<DeclaredIndexCatalog.DeclaredIndex> captureExplicitIndexes() throws Exception {
        List<DeclaredIndexCatalog.DeclaredIndex> captured = new ArrayList<>();
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.indexOps(any(Class.class))).thenAnswer(invocation -> recorder(
                mappingContext.getRequiredPersistentEntity((Class<?>) invocation.getArgument(0)).getCollection(),
                captured));
        when(template.indexOps(anyString())).thenAnswer(invocation -> recorder(invocation.getArgument(0), captured));

        new DashboardQueryIndexInitializer(template).run(null);
        new DashboardsV2IndexInitializer(template).run(null);
        new DashboardLayoutIndexInitializer(template).run(null);
        new VcsIndexInitializer(template).run(null);
        return captured;
    }

    private static IndexOperations recorder(String collection, List<DeclaredIndexCatalog.DeclaredIndex> captured) {
        return mock(IndexOperations.class, invocation -> {
            if ("createIndex".equals(invocation.getMethod().getName())) {
                captured.add(new DeclaredIndexCatalog.DeclaredIndex(collection, invocation.getArgument(0)));
            }
            return null;
        });
    }

    private static List<Map.Entry<String, Object>> orderedKeys(DeclaredIndexCatalog.DeclaredIndex index) {
        return List.copyOf(index.definition().getIndexKeys().entrySet());
    }

    private static Document optionsWithoutName(DeclaredIndexCatalog.DeclaredIndex index) {
        Document options = new Document(index.definition().getIndexOptions());
        options.remove(NAME);
        return options;
    }

    private static String describe(DeclaredIndexCatalog.DeclaredIndex index) {
        IndexDefinition definition = index.definition();
        return index.collection() + " " + definition.getIndexKeys().toJson() + " " + definition.getIndexOptions().toJson();
    }
}
