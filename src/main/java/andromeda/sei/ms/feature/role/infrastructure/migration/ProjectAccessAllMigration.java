package andromeda.sei.ms.feature.role.infrastructure.migration;

import andromeda.sei.ms.shared.domain.security.PermissionCatalog;
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
 * Migracao {@code V__project_access_all} (project-scoped-permissions D8): todo cargo (padrao ou
 * customizado, de qualquer organizacao) que tem {@code USER_MANAGE} recebe
 * {@code PROJECT_ACCESS_ALL}, porque hoje esses cargos ja veem todos os projetos. Os presets
 * padrao {@code ADMIN} e {@code MANAGER} recebem o codigo pelo {@code RoleSeeder}.
 *
 * <p>Idempotente: o filtro exige a ausencia de {@code PROJECT_ACCESS_ALL} e o {@code $addToSet}
 * nao duplica.
 */
@Slf4j
@Component
@Order(27)
@RequiredArgsConstructor
public class ProjectAccessAllMigration implements ApplicationRunner {

    static final String ROLES = "roles";
    private static final String PERMISSION_CODES = "permissionCodes";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query withUserManageOnly = Query.query(Criteria.where(PERMISSION_CODES)
                .all(PermissionCatalog.USER_MANAGE)
                .nin(PermissionCatalog.PROJECT_ACCESS_ALL));
        Update update = new Update()
                .addToSet(PERMISSION_CODES, PermissionCatalog.PROJECT_ACCESS_ALL)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        long roles = mongoTemplate.updateMulti(withUserManageOnly, update, ROLES).getModifiedCount();
        log.info("ProjectAccessAllMigration: PROJECT_ACCESS_ALL added to {} role(s) holding USER_MANAGE", roles);
    }
}
