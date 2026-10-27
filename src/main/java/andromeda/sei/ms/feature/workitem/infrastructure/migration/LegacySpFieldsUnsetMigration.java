package andromeda.sei.ms.feature.workitem.infrastructure.migration;

import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Cleanup (limpeza-sp, L4): the work item no longer has {@code storyPoints} and {@code tokensPerSp}, so
 * this unsets them from every document that still carries one. It runs over all organizations because it
 * removes a field, it does not read tenant data. Idempotent: a second run finds nothing to change.
 * The way back is the next Azure resync.
 */
@Slf4j
@Component
@Order(31)
public class LegacySpFieldsUnsetMigration implements ApplicationRunner {

    static final String COLLECTION = "work_items";

    private final MongoTemplate mongoTemplate;

    public LegacySpFieldsUnsetMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        long modified = unsetLegacyFields();
        log.info("LegacySpFieldsUnsetMigration: storyPoints/tokensPerSp removed from {} work item(s)", modified);
    }

    /** @return how many documents were changed. */
    long unsetLegacyFields() {
        Query query = new Query(new Criteria().orOperator(
                Criteria.where("storyPoints").exists(true),
                Criteria.where("tokensPerSp").exists(true)));
        return mongoTemplate.updateMulti(query, new Update().unset("storyPoints").unset("tokensPerSp"),
                Document.class, COLLECTION).getModifiedCount();
    }
}
