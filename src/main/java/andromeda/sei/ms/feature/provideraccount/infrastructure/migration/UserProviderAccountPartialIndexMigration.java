package andromeda.sei.ms.feature.provideraccount.infrastructure.migration;

import andromeda.sei.ms.feature.provideraccount.infrastructure.persistence.UserProviderAccountDocument;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * upa-indices-parciais: troca os dois indices unicos de {@code user_provider_accounts} por indices
 * unicos parciais ({@code active:true}), para que o soft delete libere a chave e a conta possa ser
 * vinculada de novo.
 *
 * <p>Order 8: antes do {@code DeclaredIndexInitializer} (9), que entao so confirma os indices novos.
 * O indice antigo e achado pela chave (mesmos campos e ordem, unique, sem filtro parcial), nao pelo
 * nome. Idempotente. Se houver duplicatas ativas, loga ERROR com os ids e nao mexe em nenhum indice;
 * nunca derruba a subida.
 */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
public class UserProviderAccountPartialIndexMigration implements ApplicationRunner {

    static final String COLLECTION = "user_provider_accounts";

    private record KeySpec(String newName, List<String> fields) {
    }

    private static final List<KeySpec> KEYS = List.of(
            new KeySpec(UserProviderAccountDocument.UK_EXTERNAL_ACTIVE,
                    List.of("organizationId", "provider", "externalUserId")),
            new KeySpec(UserProviderAccountDocument.UK_USER_PROVIDER_ACTIVE,
                    List.of("organizationId", "userId", "provider")));

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrate();
        } catch (RuntimeException e) {
            log.error("UserProviderAccountPartialIndexMigration: unexpected failure (startup continues): {}",
                    e.getMessage(), e);
        }
    }

    void migrate() {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            log.debug("UserProviderAccountPartialIndexMigration: {} does not exist yet", COLLECTION);
            return;
        }
        MongoCollection<Document> collection = mongoTemplate.getCollection(COLLECTION);

        long withoutActive = collection.countDocuments(Filters.exists("active", false));
        if (withoutActive > 0) {
            log.warn("UserProviderAccountPartialIndexMigration: {} documents in {} have no 'active' field "
                    + "and are ignored by the partial unique indexes", withoutActive, COLLECTION);
        }

        boolean duplicates = false;
        for (KeySpec key : KEYS) {
            duplicates |= logActiveDuplicates(collection, key);
        }
        if (duplicates) {
            log.error("UserProviderAccountPartialIndexMigration: active duplicates found, indexes left untouched; "
                    + "resolve the rows above and restart");
            return;
        }

        for (KeySpec key : KEYS) {
            try {
                migrateKey(collection, key);
            } catch (RuntimeException e) {
                log.error("UserProviderAccountPartialIndexMigration: failed on {} (startup continues): {}",
                        key.newName(), e.getMessage(), e);
            }
        }
    }

    private boolean logActiveDuplicates(MongoCollection<Document> collection, KeySpec key) {
        Document groupId = new Document();
        key.fields().forEach(f -> groupId.append(f, "$" + f));
        List<Document> pipeline = List.of(
                new Document("$match", new Document("active", true)),
                new Document("$group", new Document("_id", groupId)
                        .append("ids", new Document("$push", "$_id"))
                        .append("n", new Document("$sum", 1))),
                new Document("$match", new Document("n", new Document("$gt", 1))));
        boolean found = false;
        for (Document group : collection.aggregate(pipeline)) {
            found = true;
            log.error("UserProviderAccountPartialIndexMigration: duplicate active accounts for key {} {} -> row ids {}",
                    key.newName(), group.get("_id"), group.get("ids"));
        }
        return found;
    }

    private void migrateKey(MongoCollection<Document> collection, KeySpec key) {
        boolean newPresent = false;
        List<String> oldNames = new ArrayList<>();
        for (Document index : collection.listIndexes()) {
            String name = index.getString("name");
            if (key.newName().equals(name)) {
                newPresent = true;
                continue;
            }
            if (isLegacyIndex(index, key)) {
                oldNames.add(name);
            }
        }
        if (oldNames.isEmpty() && newPresent) {
            log.debug("UserProviderAccountPartialIndexMigration: {} already migrated", key.newName());
            return;
        }
        if (!newPresent) {
            createPartial(collection, key, oldNames);
        }
        for (String old : oldNames) {
            collection.dropIndex(old);
            log.info("UserProviderAccountPartialIndexMigration: dropped legacy index {} on {}", old, COLLECTION);
        }
    }

    private void createPartial(MongoCollection<Document> collection, KeySpec key, List<String> oldNames) {
        Document keys = new Document();
        key.fields().forEach(f -> keys.append(f, 1));
        IndexOptions options = new IndexOptions().name(key.newName()).unique(true)
                .partialFilterExpression(new Document("active", true));
        try {
            collection.createIndexes(List.of(new IndexModel(keys, options)));
            log.info("UserProviderAccountPartialIndexMigration: created {} on {} (before dropping the legacy index)",
                    key.newName(), COLLECTION);
        } catch (RuntimeException e) {
            if (oldNames.isEmpty()) {
                throw e;
            }
            log.info("UserProviderAccountPartialIndexMigration: Mongo refused {} next to the legacy index ({}); "
                    + "dropping the legacy one first", key.newName(), e.getMessage());
            for (String old : oldNames) {
                collection.dropIndex(old);
                log.info("UserProviderAccountPartialIndexMigration: dropped legacy index {} on {}", old, COLLECTION);
            }
            oldNames.clear();
            collection.createIndexes(List.of(new IndexModel(keys, options)));
            log.info("UserProviderAccountPartialIndexMigration: created {} on {}", key.newName(), COLLECTION);
        }
    }

    private static boolean isLegacyIndex(Document index, KeySpec key) {
        Document keyDoc = index.get("key", Document.class);
        if (keyDoc == null || !new ArrayList<>(keyDoc.keySet()).equals(key.fields())) {
            return false;
        }
        return Boolean.TRUE.equals(index.getBoolean("unique"))
                && !index.containsKey("partialFilterExpression");
    }
}
