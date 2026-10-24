package andromeda.sei.ms.feature.user.infrastructure.migration;

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

import java.time.Instant;

/**
 * Migracao {@code V__azure_identity_source}: usuarios que ja tinham {@code azureId} (vinculo feito
 * pelo admin, unico caminho ate aqui) recebem {@code azureIdentitySource = ADMIN}.
 *
 * <p>Idempotente: o filtro exige {@code azureId} preenchido e origem ausente; depois da primeira
 * execucao nenhum documento casa mais.
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class AzureIdentitySourceMigration implements ApplicationRunner {

    static final String USERS = "users";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query linkedWithoutSource = Query.query(new Criteria().andOperator(
                Criteria.where("azureId").ne(null),
                Criteria.where("azureIdentitySource").exists(false)));
        Update update = new Update()
                .set("azureIdentitySource", "ADMIN")
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        long users = mongoTemplate.updateMulti(linkedWithoutSource, update, USERS).getModifiedCount();
        log.info("AzureIdentitySourceMigration: azureIdentitySource=ADMIN set on {} user(s)", users);
    }
}
