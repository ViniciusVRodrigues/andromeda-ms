package andromeda.sei.ms.feature.aiusage.infrastructure;

import andromeda.sei.ms.feature.aiusage.application.usecase.ReconcileAiefAgentOverlapUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Runs the extension x agent reconciliation: the whole history once at startup (in its own thread, so
 * the health check is not delayed) and the last N days periodically. Off with
 * {@code andromeda.aiusage.aief-overlap.enabled=false}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "andromeda.aiusage.aief-overlap.enabled", havingValue = "true", matchIfMissing = true)
public class AiefOverlapScheduler {

    private final ReconcileAiefAgentOverlapUseCase useCase;
    private final int days;

    public AiefOverlapScheduler(ReconcileAiefAgentOverlapUseCase useCase,
                                @Value("${andromeda.aiusage.aief-overlap.days:7}") int days) {
        this.useCase = useCase;
        this.days = days;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        Thread t = new Thread(() -> run(null), "aief-overlap-history");
        t.setDaemon(true);
        t.start();
    }

    @Scheduled(fixedDelayString = "${andromeda.aiusage.aief-overlap.delay-ms:300000}",
            initialDelayString = "${andromeda.aiusage.aief-overlap.delay-ms:300000}")
    public void recent() {
        run(LocalDate.now(ZoneOffset.UTC).minusDays(days));
    }

    void run(LocalDate fromDay) {
        try {
            useCase.execute(fromDay);
        } catch (Exception e) {
            log.error("Reconciliacao extensao x agente falhou", e);
        }
    }
}
