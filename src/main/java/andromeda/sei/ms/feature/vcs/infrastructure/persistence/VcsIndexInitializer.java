package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

/**
 * Creates the partial unique identity index of {@code vcs_repositories} (design D2):
 * {@code {organizationId, provider, externalRepoId}} unique only where {@code externalRepoId}
 * exists, so any number of repositories pending first sync can coexist. {@code @CompoundIndex}
 * cannot declare a partial filter. Idempotent ({@code createIndex} on an identical index is a
 * no-op). Runs before the data migrations (order 20+).
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class VcsIndexInitializer implements ApplicationRunner {

    static final String IDENTITY_INDEX = "vcs_repository_identity";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Index identity = new Index()
                .on("organizationId", Sort.Direction.ASC)
                .on("provider", Sort.Direction.ASC)
                .on("externalRepoId", Sort.Direction.ASC)
                .unique()
                .named(IDENTITY_INDEX)
                .partial(PartialIndexFilter.of(Criteria.where("externalRepoId").exists(true)));
        mongoTemplate.indexOps(VcsRepositoryDocument.COLLECTION).createIndex(identity);
        log.debug("VcsIndexInitializer: ensured {} on {}", IDENTITY_INDEX, VcsRepositoryDocument.COLLECTION);
    }
}
