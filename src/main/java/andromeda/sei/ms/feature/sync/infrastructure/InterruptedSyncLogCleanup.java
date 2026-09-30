package andromeda.sei.ms.feature.sync.infrastructure;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Encerra, no startup, logs de sync que ficaram {@code RUNNING} de uma execucao anterior.
 *
 * <p>O executor de sync e em memoria: um restart (deploy, crash) interrompe o trabalho sem
 * gravar status terminal. Sem esta limpeza o refresh ficaria "rodando" para sempre — a UI faria
 * polling indefinidamente e {@code dataFreshness.refreshRunning} nunca voltaria a {@code false}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterruptedSyncLogCleanup implements ApplicationRunner {

    static final String ERROR_CODE = "SYNC_INTERRUPTED";
    static final String ERROR_MESSAGE = "Sync interrupted by an application restart";

    private final IntegrationSyncLogRepository syncLogRepository;

    @Override
    public void run(ApplicationArguments args) {
        long closed = syncLogRepository.failRunningStartedBefore(Instant.now(), ERROR_CODE, ERROR_MESSAGE);
        if (closed > 0) {
            log.warn("InterruptedSyncLogCleanup: {} sync log(s) left RUNNING by a previous run marked FAILED", closed);
        }
    }
}
