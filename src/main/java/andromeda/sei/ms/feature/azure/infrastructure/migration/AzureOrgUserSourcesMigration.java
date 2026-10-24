package andromeda.sei.ms.feature.azure.infrastructure.migration;

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
import java.util.List;

/**
 * Migracao {@code V__azure_org_user_sources}: (1) linhas de grupo ficam {@code active=false}; (2) linhas sem
 * {@code sources} ganham {@code [TEAM]} (unica origem ate aqui).
 *
 * <p>O documento nao guardava {@code isContainer}; grupos de time aparecem com o nome principal no formato
 * {@code [Projeto]\Time} (colchetes + barra invertida), guardado em {@code azureEmail}. Esse e o criterio.
 *
 * <p>Idempotente: o passo 1 so casa linhas ativas; o passo 2 so casa linhas sem {@code sources}.
 */
@Slf4j
@Component
@Order(31)
@RequiredArgsConstructor
public class AzureOrgUserSourcesMigration implements ApplicationRunner {

    static final String COLLECTION = "azure_org_users";
    static final String GROUP_PATTERN = "^\\[[^\\]]*\\]\\\\";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query activeGroups = Query.query(new Criteria().andOperator(
                Criteria.where("active").is(true),
                Criteria.where("azureEmail").regex(GROUP_PATTERN)));
        long groups = mongoTemplate.updateMulti(activeGroups, touch().set("active", false), COLLECTION)
                .getModifiedCount();

        Query withoutSources = Query.query(Criteria.where("sources").exists(false));
        long users = mongoTemplate.updateMulti(withoutSources, touch().set("sources", List.of("TEAM")), COLLECTION)
                .getModifiedCount();
        log.info("AzureOrgUserSourcesMigration: {} group row(s) deactivated, sources=[TEAM] set on {} row(s)",
                groups, users);
    }

    private static Update touch() {
        return new Update().set("updatedAt", Instant.now()).inc("version", 1);
    }
}
