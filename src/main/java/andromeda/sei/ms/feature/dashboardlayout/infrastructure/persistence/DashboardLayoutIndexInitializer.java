package andromeda.sei.ms.feature.dashboardlayout.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code dashboard_layouts} indexes (design.md D6). {@code DeclaredIndexInitializer} also
 * creates the {@code @CompoundIndex} declarations of {@link DashboardLayoutDocument}, but logs and
 * moves on when one fails; this initializer is kept because the
 * partial unique index is what guarantees at most one active layout per (organization, user,
 * viewKey) under concurrent creates. Same names, keys and options as the declarations, so it never
 * conflicts with them. Idempotent; runs with the other index initializers, before the data
 * migrations (order 20+). A failure (e.g. duplicate active layouts already stored) stops the
 * startup, like {@code VcsIndexInitializer}: running without the index would allow duplicates.
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class DashboardLayoutIndexInitializer implements ApplicationRunner {

    private static final String ACTIVE = "active";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        IndexOperations indexOps = mongoTemplate.indexOps(DashboardLayoutDocument.class);
        indexOps.createIndex(new Index()
                .on("organizationId", Sort.Direction.ASC)
                .on("userId", Sort.Direction.ASC)
                .on("viewKey", Sort.Direction.ASC)
                .unique()
                .named(DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX)
                .partial(PartialIndexFilter.of(Criteria.where(ACTIVE).is(true))));
        indexOps.createIndex(new Index()
                .on("organizationId", Sort.Direction.ASC)
                .on(ACTIVE, Sort.Direction.ASC)
                .named(DashboardLayoutDocument.ORG_ACTIVE_INDEX));
        log.debug("DashboardLayoutIndexInitializer: ensured {} and {}",
                DashboardLayoutDocument.UNIQUE_ACTIVE_INDEX, DashboardLayoutDocument.ORG_ACTIVE_INDEX);
    }
}
