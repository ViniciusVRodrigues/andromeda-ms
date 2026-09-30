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
 * Migracao {@code V__project_manage}: {@code POST/PUT/DELETE /api/v1/projects} passaram a exigir
 * {@code PROJECT_MANAGE} (antes bastava estar autenticado). Todo cargo (padrao ou customizado, de
 * qualquer organizacao) que tem {@code USER_MANAGE} recebe {@code PROJECT_MANAGE}, preservando a
 * capacidade de quem hoje administra a organizacao. O preset {@code ADMIN} ja recebe o codigo pelo
 * {@code RoleSeeder}.
 *
 * <p>Idempotente: o filtro exige a ausencia de {@code PROJECT_MANAGE} e o {@code $addToSet} nao
 * duplica — um segundo boot nao altera nenhum documento.
 */
@Slf4j
@Component
@Order(28)
@RequiredArgsConstructor
public class ProjectManageMigration implements ApplicationRunner {

    static final String ROLES = "roles";
    private static final String PERMISSION_CODES = "permissionCodes";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query withUserManageOnly = Query.query(Criteria.where(PERMISSION_CODES)
                .all(PermissionCatalog.USER_MANAGE)
                .nin(PermissionCatalog.PROJECT_MANAGE));
        Update update = new Update()
                .addToSet(PERMISSION_CODES, PermissionCatalog.PROJECT_MANAGE)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        long roles = mongoTemplate.updateMulti(withUserManageOnly, update, ROLES).getModifiedCount();
        log.info("ProjectManageMigration: PROJECT_MANAGE added to {} role(s) holding USER_MANAGE", roles);
    }
}
