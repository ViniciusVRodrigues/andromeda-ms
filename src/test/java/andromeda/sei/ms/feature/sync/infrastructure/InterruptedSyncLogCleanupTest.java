package andromeda.sei.ms.feature.sync.infrastructure;

import andromeda.sei.ms.feature.integration.domain.IntegrationSyncLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InterruptedSyncLogCleanupTest {

    @Mock private IntegrationSyncLogRepository syncLogRepository;
    @Mock private ApplicationArguments args;

    @InjectMocks
    private InterruptedSyncLogCleanup cleanup;

    @Test
    void shouldFailLogsLeftRunningBeforeStartup() {
        Instant before = Instant.now();

        cleanup.run(args);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(syncLogRepository).failRunningStartedBefore(cutoff.capture(), eq("SYNC_INTERRUPTED"),
                eq(InterruptedSyncLogCleanup.ERROR_MESSAGE));
        assertThat(cutoff.getValue()).isAfterOrEqualTo(before);
    }
}
