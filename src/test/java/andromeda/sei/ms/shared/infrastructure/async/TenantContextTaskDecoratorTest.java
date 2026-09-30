package andromeda.sei.ms.shared.infrastructure.async;

import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TenantContextTaskDecoratorTest {

    private final TenantContextTaskDecorator decorator = new TenantContextTaskDecorator();

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void shouldPropagateSubmittingThreadTenantToWorkerThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TenantContextHolder.set("org-1");
            AtomicReference<String> seen = new AtomicReference<>();

            Runnable task = decorator.decorate(() -> seen.set(TenantContextHolder.get()));
            TenantContextHolder.clear();
            pool.submit(task).get(5, TimeUnit.SECONDS);

            assertThat(seen.get()).isEqualTo("org-1");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldClearTenantFromReusedWorkerThreadAfterTask() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TenantContextHolder.set("org-1");
            pool.submit(decorator.decorate(() -> { })).get(5, TimeUnit.SECONDS);
            TenantContextHolder.clear();

            Future<String> leaked = pool.submit(TenantContextHolder::get);

            assertThat(leaked.get(5, TimeUnit.SECONDS)).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void shouldClearTenantEvenWhenTaskThrows() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            TenantContextHolder.set("org-1");
            Runnable failing = decorator.decorate(() -> { throw new IllegalStateException("boom"); });
            pool.submit(failing);
            TenantContextHolder.clear();

            Future<String> leaked = pool.submit(TenantContextHolder::get);

            assertThat(leaked.get(5, TimeUnit.SECONDS)).isNull();
        } finally {
            pool.shutdownNow();
        }
    }
}
