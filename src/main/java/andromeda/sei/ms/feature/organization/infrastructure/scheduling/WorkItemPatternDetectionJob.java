package andromeda.sei.ms.feature.organization.infrastructure.scheduling;

import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase;
import andromeda.sei.ms.feature.organization.application.usecase.DetectWorkItemPatternsUseCase.Trigger;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.WorkItemPatterns;
import andromeda.sei.ms.feature.vcs.domain.VcsRepoBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily pattern detection (agente-endpoint-v2, design 14.5): organizations in {@code DEFAULT} or
 * {@code DETECTED} with a linked repository. One organization failing is logged and the others go on.
 * The cron is {@code andromeda.work-item-patterns.detection.cron} (default 03:30 UTC); the job can be
 * turned off with {@code andromeda.work-item-patterns.detection.enabled=false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "andromeda.work-item-patterns.detection.enabled", havingValue = "true",
        matchIfMissing = true)
public class WorkItemPatternDetectionJob {

    private final OrganizationRepository organizationRepository;
    private final VcsRepoBindingRepository bindingRepository;
    private final DetectWorkItemPatternsUseCase detectUseCase;

    @Scheduled(cron = "${andromeda.work-item-patterns.detection.cron:0 30 3 * * *}", zone = "UTC")
    public void run() {
        int detected = 0;
        int failed = 0;
        try {
            for (Organization organization : organizationRepository.findAllActive()) {
                if (!eligible(organization)) continue;
                try {
                    detectUseCase.execute(organization.getId(), Trigger.SCHEDULED);
                    detected++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Detecção de padrões falhou para a organização {}", organization.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Detecção diária de padrões de branch falhou", e);
            return;
        }
        log.info("Detecção diária de padrões de branch: {} organização(ões) ok, {} com falha", detected, failed);
    }

    private boolean eligible(Organization organization) {
        try {
            WorkItemPatterns.Mode mode = organization.getWorkItemPatterns().mode();
            if (mode == WorkItemPatterns.Mode.CUSTOM) return false;
            return !bindingRepository.findAllActiveByOrganizationId(organization.getId()).isEmpty();
        } catch (Exception e) {
            log.warn("Não foi possível avaliar a organização {} para a detecção de padrões", organization.getId(), e);
            return false;
        }
    }
}
