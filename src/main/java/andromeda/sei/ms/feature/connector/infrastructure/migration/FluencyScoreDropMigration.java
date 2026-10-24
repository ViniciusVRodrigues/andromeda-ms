package andromeda.sei.ms.feature.connector.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * remover-fluency-score 3.2: remove a collection {@code fluency_scores}.
 *
 * <p>Dado derivado dos envios do conector; nenhum codigo le nem escreve mais a collection (o
 * endpoint do conector descarta o corpo). Drop, e nao soft delete. Idempotente: sem a collection,
 * nao faz nada.
 */
@Slf4j
@Component
@Order(32)
@RequiredArgsConstructor
public class FluencyScoreDropMigration implements ApplicationRunner {

    static final String COLLECTION = "fluency_scores";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            log.debug("FluencyScoreDropMigration: {} already absent", COLLECTION);
            return;
        }
        long count = mongoTemplate.getCollection(COLLECTION).countDocuments();
        mongoTemplate.dropCollection(COLLECTION);
        log.info("FluencyScoreDropMigration: dropped collection {} ({} documents)", COLLECTION, count);
    }
}
