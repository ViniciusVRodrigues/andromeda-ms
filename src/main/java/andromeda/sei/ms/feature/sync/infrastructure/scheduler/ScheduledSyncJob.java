package andromeda.sei.ms.feature.sync.infrastructure.scheduler;

import andromeda.sei.ms.feature.azure.application.usecase.SyncAzureOrgLevelDataUseCase;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionRepository;
import andromeda.sei.ms.feature.azure.domain.AzureConnectionStatus;
import andromeda.sei.ms.feature.integration.domain.SyncTrigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.sync.application.SyncOrchestrator;
import andromeda.sei.ms.feature.sync.application.SyncRequest;
import andromeda.sei.ms.feature.sync.application.SyncTargetResolver;
import andromeda.sei.ms.feature.sync.domain.SyncTarget;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Sync agendado opcional por organizacao (design D5), substituindo os crons fixos de 30 minutos
 * de {@code AzureSyncJob}/{@code GithubSyncJob}.
 *
 * <p>Roda de hora em hora e so alcanca organizacoes cuja frequencia venceu
 * ({@code lastScheduledSyncAt + intervalo <= tick}); organizacoes {@code OFF} nunca geram chamada
 * externa. {@code lastScheduledSyncAt} e gravado no <b>despacho</b>, com o tick truncado na hora,
 * para que um sync longo nao seja redisparado na hora seguinte e o horario nao escorregue.
 *
 * <p>Roda o sync no proprio thread do scheduler (como os jobs antigos), sem ocupar a fila limitada
 * do refresh manual. O tenant e posicionado explicitamente — nao ha request de onde herda-lo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledSyncJob {

    static final java.time.Duration ORG_LEVEL_SYNC_INTERVAL = java.time.Duration.ofHours(6);

    private final OrganizationRepository organizationRepository;
    private final SyncTargetResolver syncTargetResolver;
    private final SyncOrchestrator syncOrchestrator;
    private final AzureConnectionRepository azureConnectionRepository;
    private final SyncAzureOrgLevelDataUseCase syncAzureOrgLevelDataUseCase;

    @Scheduled(cron = "0 0 * * * *")
    public void run() {
        run(Instant.now());
    }

    void run(Instant now) {
        Instant tick = now.truncatedTo(ChronoUnit.HOURS);
        for (Organization organization : organizationRepository.findAllDueForScheduledSync(tick)) {
            if (organization.isScheduledSyncDue(tick)) {
                dispatch(organization, tick, now);
            }
        }
    }

    private void dispatch(Organization organization, Instant tick, Instant now) {
        String orgId = organization.getId();
        if (!markDispatched(organization, tick)) {
            return;
        }
        TenantContextHolder.set(orgId);
        try {
            refreshAzureOrgLevel(orgId, now);
            List<SyncTarget> targets = syncTargetResolver.forScheduled(orgId);
            if (targets.isEmpty()) {
                return;
            }
            syncOrchestrator.runNow(SyncRequest.organization(orgId, null, targets, SyncTrigger.SCHEDULED));
        } catch (Exception e) {
            log.error("Scheduled sync failed for org={}", orgId, e);
        } finally {
            TenantContextHolder.clear();
        }
    }

    /** Org-level Azure sync (directory + teams) before the bindings, at most once per 6 h; a failure only warns. */
    private void refreshAzureOrgLevel(String orgId, Instant now) {
        try {
            azureConnectionRepository.findByOrganizationIdAndActiveTrue(orgId)
                    .filter(c -> c.getStatus() == AzureConnectionStatus.CONNECTED)
                    .filter(c -> c.getOrgLevelSyncedAt() == null
                            || c.getOrgLevelSyncedAt().isBefore(now.minus(ORG_LEVEL_SYNC_INTERVAL)))
                    .ifPresent(syncAzureOrgLevelDataUseCase::execute);
        } catch (Exception e) {
            log.warn("Azure org-level sync failed for org={}; continuing with bindings: {}", orgId, e.getMessage());
        }
    }

    /** @return false quando a gravacao conflitou (ex.: edicao concorrente); a org fica para o proximo tick. */
    private boolean markDispatched(Organization organization, Instant tick) {
        try {
            organization.setLastScheduledSyncAt(tick);
            organizationRepository.save(organization);
            return true;
        } catch (Exception e) {
            log.warn("Could not mark scheduled sync for org={}; skipping this tick", organization.getId(), e);
            return false;
        }
    }
}
