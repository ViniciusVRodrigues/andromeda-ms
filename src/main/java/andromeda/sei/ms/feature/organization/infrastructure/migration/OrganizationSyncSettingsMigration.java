package andromeda.sei.ms.feature.organization.infrastructure.migration;

import andromeda.sei.ms.feature.organization.domain.ScheduledSyncFrequency;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Migracao {@code V__sync_settings} (on-demand-refresh, design D5): organizacoes existentes antes
 * desta change recebem {@code syncSettings = { scheduledSync: DAILY, backfillDays: 90 }}.
 *
 * <p>Por que {@code DAILY} e nao o default {@code OFF}: essas organizacoes tinham o cron fixo de
 * 30 minutos; desliga-lo sem aviso faria os dashboards envelhecerem silenciosamente. Organizacoes
 * novas nascem {@code OFF} — o mapper sempre grava {@code syncSettings}, entao elas nunca casam
 * com o filtro abaixo.
 *
 * <p>Idempotente: so toca documentos sem o campo, entao re-executar (ou rodar depois que um admin
 * trocou a frequencia) nao sobrescreve nada.
 */
@Slf4j
@Component
@Order(23)
@RequiredArgsConstructor
public class OrganizationSyncSettingsMigration implements ApplicationRunner {

    static final String COLLECTION = "organizations";
    static final String FIELD = "syncSettings";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        Document legacyDefaults = new Document("scheduledSync", ScheduledSyncFrequency.DAILY.name())
                .append("backfillDays", SyncSettings.DEFAULT_BACKFILL_DAYS);

        long updated = mongoTemplate.updateMulti(
                Query.query(Criteria.where(FIELD).exists(false)),
                new Update().set(FIELD, legacyDefaults),
                COLLECTION).getModifiedCount();

        log.info("OrganizationSyncSettingsMigration: {} organization(s) set to DAILY/{} days",
                updated, SyncSettings.DEFAULT_BACKFILL_DAYS);
    }
}
