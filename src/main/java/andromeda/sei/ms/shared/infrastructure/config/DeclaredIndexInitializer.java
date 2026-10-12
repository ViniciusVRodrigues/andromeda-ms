package andromeda.sei.ms.shared.infrastructure.config;

import andromeda.sei.ms.MsApplication;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates, at startup, every index declared on the {@code @Document} classes ({@code @CompoundIndex},
 * {@code @Indexed}). This is how declarations take effect in this application: {@code MongoConfig}
 * extends {@code AbstractMongoClientConfiguration}, whose mapping context has auto index creation
 * off, so {@code spring.data.mongodb.auto-index-creation} is not read.
 *
 * <p>Why a runner instead of turning auto index creation on: Spring Data creates indexes while it
 * builds each repository, so one failing index (a {@code unique} index over data that already has
 * duplicates, an option conflict with an existing index) would abort the startup and fail the
 * blue-green health check. Here each index is created on its own; a failure becomes a WARN with the
 * collection, the keys and the reason, the other indexes are still created and the app starts.
 * Creating an index that already exists is a no-op, so every startup is idempotent.
 *
 * <p>Order 9: before {@code MongoIndexReport} (10), the explicit index initializers (11/12) and the
 * data migrations (20+). The explicit initializers keep creating the indexes no document declares
 * (partial filters on {@code vcs_repositories}, {@code idx_pr_org_repo_merged}, ...); where they repeat
 * a declaration they use its name and options, which {@code ExplicitIndexInitializersConsistencyTest}
 * enforces.
 */
@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
public class DeclaredIndexInitializer implements ApplicationRunner {

    static final String BASE_PACKAGE = MsApplication.class.getPackageName();

    private final MongoTemplate mongoTemplate;
    private final MongoMappingContext mappingContext;

    /** Outcome of one run: indexes created now, already present, and not created. */
    record Summary(int created, int existing, int failed) {
    }

    @Override
    public void run(ApplicationArguments args) {
        Summary summary = ensureAll();
        log.info("DeclaredIndexInitializer: {} declared indexes — {} created, {} already existed, {} failed",
                summary.created() + summary.existing() + summary.failed(),
                summary.created(), summary.existing(), summary.failed());
    }

    Summary ensureAll() {
        Map<String, List<DeclaredIndexCatalog.DeclaredIndex>> byCollection =
                DeclaredIndexCatalog.resolve(mappingContext, BASE_PACKAGE).stream()
                        .collect(Collectors.groupingBy(DeclaredIndexCatalog.DeclaredIndex::collection,
                                LinkedHashMap::new, Collectors.toList()));
        int created = 0;
        int existing = 0;
        int failed = 0;
        for (Map.Entry<String, List<DeclaredIndexCatalog.DeclaredIndex>> entry : byCollection.entrySet()) {
            IndexOperations indexOps = mongoTemplate.indexOps(entry.getKey());
            Set<String> present = presentIndexNames(entry.getKey(), indexOps);
            for (DeclaredIndexCatalog.DeclaredIndex index : entry.getValue()) {
                if (!ensure(indexOps, index)) failed++;
                else if (present.contains(index.name())) existing++;
                else created++;
            }
        }
        return new Summary(created, existing, failed);
    }

    private boolean ensure(IndexOperations indexOps, DeclaredIndexCatalog.DeclaredIndex index) {
        try {
            indexOps.createIndex(index.definition());
            return true;
        } catch (RuntimeException e) {
            log.warn("DeclaredIndexInitializer: index {} {} {} on {} not created (startup continues): {}",
                    index.name(), index.definition().getIndexKeys().toJson(),
                    index.definition().getIndexOptions().toJson(), index.collection(), e.getMessage());
            return false;
        }
    }

    private static Set<String> presentIndexNames(String collection, IndexOperations indexOps) {
        try {
            return indexOps.getIndexInfo().stream().map(IndexInfo::getName).collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn("DeclaredIndexInitializer: could not list the indexes of {}: {}", collection, e.getMessage());
            return Set.of();
        }
    }
}
