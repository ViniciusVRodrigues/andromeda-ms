package andromeda.sei.ms.feature.organization.infrastructure.scheduling;

import andromeda.sei.ms.feature.organization.application.usecase.PurgeOrganizationDataUseCase;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Retomada do purge de organizacoes excluidas (excluir-organizacao, fase 2): lapides com
 * {@code deletedAt} e sem {@code purgedAt}. Roda uma vez no startup e periodicamente.
 *
 * <p>O job periodico espera {@link #GRACE} depois da exclusao, para nao disputar com o purge que o
 * proprio pedido de exclusao acabou de disparar; no startup nao ha espera, porque um deploy
 * interrompe o purge em curso. O purge e idempotente, entao uma sobreposicao so repete delete.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingOrganizationPurgeResumer implements ApplicationRunner {

    static final Duration GRACE = Duration.ofMinutes(10);

    private final OrganizationRepository organizationRepository;
    private final PurgeOrganizationDataUseCase purgeUseCase;

    @Override
    public void run(ApplicationArguments args) {
        resume(Instant.now());
    }

    @Scheduled(fixedDelayString = "${andromeda.organization.purge-resume-delay:PT10M}",
            initialDelayString = "${andromeda.organization.purge-resume-delay:PT10M}")
    public void resumePending() {
        resume(Instant.now().minus(GRACE));
    }

    void resume(Instant deletedBefore) {
        try {
            var pending = organizationRepository.findPendingPurge(deletedBefore);
            if (!pending.isEmpty()) {
                log.info("Retomando purge de {} organizacao(oes) excluida(s)", pending.size());
            }
            pending.forEach(org -> purgeUseCase.execute(org.getId()));
        } catch (Exception e) {
            log.error("Retomada do purge falhou", e);
        }
    }
}
