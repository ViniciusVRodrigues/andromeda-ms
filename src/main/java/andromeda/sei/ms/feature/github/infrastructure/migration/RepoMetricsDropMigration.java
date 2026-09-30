package andromeda.sei.ms.feature.github.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Migracao {@code V__drop_repo_metrics} (metrics-correctness, migration plan 1): remove a collection
 * {@code repo_metrics}.
 *
 * <p>Ela guardava o CFR antigo (issues / commits como fracao) e ganhava uma linha nova a cada sync,
 * porque a chave do periodo usava {@code Instant.now()}. As metricas de entrega agora sao
 * calculadas na leitura a partir dos PRs, e nenhum codigo le nem escreve a collection. Dado
 * derivado, sem valor historico — por isso drop, e nao soft delete.
 *
 * <p>Idempotente: sem a collection, nao faz nada.
 */
@Slf4j
@Component
@Order(24)
@RequiredArgsConstructor
public class RepoMetricsDropMigration implements ApplicationRunner {

    static final String COLLECTION = "repo_metrics";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!mongoTemplate.collectionExists(COLLECTION)) {
            log.debug("RepoMetricsDropMigration: {} already absent", COLLECTION);
            return;
        }
        mongoTemplate.dropCollection(COLLECTION);
        log.info("RepoMetricsDropMigration: dropped collection {}", COLLECTION);
    }
}
