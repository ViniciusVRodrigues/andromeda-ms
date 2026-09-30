package andromeda.sei.ms.feature.role.infrastructure.migration;

import andromeda.sei.ms.shared.domain.security.PermissionAliasResolver;
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
 * Migracao {@code V__repos_permissions} (vcs-core-refactor, repository-permissions), <b>aditiva e
 * segura para rollback</b>: em todo cargo (padrao e customizado, de todas as organizacoes) mantem o
 * par {@code GITHUB_READ}/{@code REPOS_READ} e {@code GITHUB_CONFIGURE}/{@code REPOS_CONFIGURE}
 * sincronizado — adiciona o codigo novo onde so ha o antigo e, em bancos ja migrados pela versao
 * anterior desta migracao (que fazia {@code $pull} do antigo), readiciona o antigo onde so ha o novo.
 * Um rollback do deploy para o codigo anterior (que checa {@code GITHUB_*}) nao tira o acesso de
 * ninguem. Os codigos antigos continuam desativados (soft delete) no catalogo {@code permissions}:
 * la eles so alimentavam a UI.
 *
 * <p>O {@code $pull} dos espelhos antigos fica para uma change de limpeza posterior, junto com a
 * remocao de {@link PermissionAliasResolver}.
 *
 * <p>Roda depois dos seeders (ordem 1 e 2). Idempotente: cada filtro exige a ausencia do codigo que
 * adiciona ({@code $nin}), entao uma segunda execucao nao altera documento algum (sem churn de
 * {@code version}/{@code updatedAt} a cada boot).
 */
@Slf4j
@Component
@Order(25)
@RequiredArgsConstructor
public class ReposPermissionsMigration implements ApplicationRunner {

    static final String ROLES = "roles";
    static final String PERMISSIONS = "permissions";
    private static final String PERMISSION_CODES = "permissionCodes";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        for (Map.Entry<String, String> rename : PermissionAliasResolver.LEGACY_TO_CURRENT.entrySet()) {
            String legacy = rename.getKey();
            String current = rename.getValue();
            long added = addWhereMissing(legacy, current);
            long mirrored = addWhereMissing(current, legacy);
            long catalog = retireCatalogEntry(legacy);
            log.info("ReposPermissionsMigration: {} added to {} role(s) holding {}; {} mirror re-added to {} role(s); "
                    + "{} catalog entr(ies) deactivated", current, added, legacy, legacy, mirrored, catalog);
        }
    }

    /** {@code $addToSet missing} in every role that holds {@code present} but not {@code missing}. */
    private long addWhereMissing(String present, String missing) {
        Query query = Query.query(Criteria.where(PERMISSION_CODES).all(present).nin(missing));
        Update update = new Update()
                .addToSet(PERMISSION_CODES, missing)
                .set("updatedAt", Instant.now())
                .inc("version", 1);
        return mongoTemplate.updateMulti(query, update, ROLES).getModifiedCount();
    }

    private long retireCatalogEntry(String legacy) {
        return mongoTemplate.updateMulti(
                Query.query(Criteria.where("code").is(legacy).and("active").is(true)),
                new Update().set("active", false).set("updatedAt", Instant.now()),
                PERMISSIONS).getModifiedCount();
    }
}
