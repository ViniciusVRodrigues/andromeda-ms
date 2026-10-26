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
 * Migracao {@code V__organization_delete}: {@code DELETE /api/v1/organizations/me} exige
 * {@code ORGANIZATION_DELETE}, que e so do cargo {@code ADMIN} global (o que o onboard usa). Cargo de
 * organizacao, mesmo chamado {@code ADMIN} ou clonado antes, nao recebe o codigo.
 *
 * <p>Idempotente: o filtro exige a ausencia do codigo e o {@code $addToSet} nao duplica.
 */
@Slf4j
@Component
@Order(33)
@RequiredArgsConstructor
public class OrganizationDeleteMigration implements ApplicationRunner {

    static final String ROLES = "roles";
    private static final String PERMISSION_CODES = "permissionCodes";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Query adminsWithoutIt = Query.query(Criteria.where("organizationId").is(null).and("name").is("ADMIN")
                .and(PERMISSION_CODES).nin(PermissionCatalog.ORGANIZATION_DELETE));
        Update update = new Update()
                .addToSet(PERMISSION_CODES, PermissionCatalog.ORGANIZATION_DELETE)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        long roles = mongoTemplate.updateMulti(adminsWithoutIt, update, ROLES).getModifiedCount();
        log.info("OrganizationDeleteMigration: ORGANIZATION_DELETE added to {} ADMIN role(s)", roles);
    }
}
