package andromeda.sei.ms.shared.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncExecutorConfigTest {

    @Test
    void shouldRejectSubmissionOnceTwoThreadsAreBusyAndQueueIsFull() throws Exception {
        ThreadPoolTaskExecutor executor = new AsyncExecutorConfig().syncExecutor();
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        try {
            int capacity = AsyncExecutorConfig.SYNC_POOL_SIZE + AsyncExecutorConfig.SYNC_QUEUE_CAPACITY;
            for (int i = 0; i < capacity; i++) {
                executor.execute(() -> awaitQuietly(release));
            }

            assertThatThrownBy(() -> executor.execute(() -> { }))
                    .isInstanceOf(TaskRejectedException.class)
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.getMaxPoolSize()).isEqualTo(AsyncExecutorConfig.SYNC_POOL_SIZE);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
