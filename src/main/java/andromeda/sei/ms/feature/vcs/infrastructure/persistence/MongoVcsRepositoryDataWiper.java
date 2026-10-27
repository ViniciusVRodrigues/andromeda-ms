package andromeda.sei.ms.feature.vcs.infrastructure.persistence;

import andromeda.sei.ms.feature.vcs.application.port.VcsRepositoryDataWiper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Removes the derived data of ONE repository of ONE organization. Every query carries both
 * {@code organizationId} and {@code repositoryId}; nothing else is touched. Order: review events →
 * pull requests → commits (the "Limpar dados" wiper's order, minus the repository document).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoVcsRepositoryDataWiper implements VcsRepositoryDataWiper {

    private final MongoTemplate mongoTemplate;

    @Override
    public Map<String, Long> wipeRepository(String organizationId, String repositoryId) {
        if (organizationId == null || repositoryId == null) {
            throw new IllegalArgumentException("organizationId and repositoryId are required");
        }
        Map<String, Long> removed = new LinkedHashMap<>();
        for (String collection : new String[]{VcsReviewEventDocument.COLLECTION, VcsPullRequestDocument.COLLECTION,
                VcsCommitDocument.COLLECTION}) {
            removed.put(collection, mongoTemplate.remove(Query.query(Criteria.where("organizationId").is(organizationId)
                    .and("repositoryId").is(repositoryId)), collection).getDeletedCount());
        }
        log.info("VCS repository wipe: org={} repo={} removed={}", organizationId, repositoryId, removed);
        return removed;
    }
}
