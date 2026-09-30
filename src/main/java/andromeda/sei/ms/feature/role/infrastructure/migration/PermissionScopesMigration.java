package andromeda.sei.ms.feature.role.infrastructure.migration;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
import andromeda.sei.ms.shared.domain.security.PermissionScope;
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
import java.util.Map;

/**
 * Migracao {@code V__permission_scopes} (project-scoped-permissions D8): grava o {@code scope}
 * ({@code ORG_ONLY}/{@code PROJECT_SCOPABLE}) de cada codigo do catalogo {@code permissions}, a
 * partir de {@link PermissionCatalog}. O {@code PermissionSeeder} so insere codigos ausentes, entao
 * as entradas ja existentes recebem o escopo aqui.
 *
 * <p>Idempotente: o filtro {@code scope != <esperado>} deixa de casar depois da primeira execucao.
 */
@Slf4j
@Component
@Order(26)
@RequiredArgsConstructor
public class PermissionScopesMigration implements ApplicationRunner {

    static final String PERMISSIONS = "permissions";
    private static final String SCOPE = "scope";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        long updated = 0;
        for (Map.Entry<String, PermissionScope> entry : PermissionCatalog.scopes().entrySet()) {
            Query query = Query.query(Criteria.where("code").is(entry.getKey()).and(SCOPE).ne(entry.getValue().name()));
            Update update = new Update().set(SCOPE, entry.getValue().name()).set("updatedAt", Instant.now());
            updated += mongoTemplate.updateMulti(query, update, PERMISSIONS).getModifiedCount();
        }
        log.info("PermissionScopesMigration: scope set on {} catalog entr(ies)", updated);
    }
}
