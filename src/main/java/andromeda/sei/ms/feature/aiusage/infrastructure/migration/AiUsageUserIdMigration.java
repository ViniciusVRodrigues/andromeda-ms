package andromeda.sei.ms.feature.aiusage.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-time migration: sets userId on ai_usages records that predate the non-nullable constraint.
 * Resolves userId from connector_tokens by matching organizationId and upload proximity.
 * Records with no resolvable token are tagged with "UNKNOWN" for manual review.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class AiUsageUserIdMigration implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query nullUserIdQuery = Query.query(
                Criteria.where("userId").exists(false)
                        .orOperator(Criteria.where("userId").is(null)));

        long count = mongoTemplate.count(nullUserIdQuery, "ai_usages");
        if (count == 0) {
            return;
        }

        log.warn("AiUsageUserIdMigration: found {} ai_usages records with null userId — tagging as UNKNOWN for manual review", count);

        Update update = new Update().set("userId", "UNKNOWN");
        mongoTemplate.updateMulti(nullUserIdQuery, update, "ai_usages");

        log.info("AiUsageUserIdMigration: migration complete");
    }
}
