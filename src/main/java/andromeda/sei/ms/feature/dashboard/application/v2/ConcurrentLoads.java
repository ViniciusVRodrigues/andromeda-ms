package andromeda.sei.ms.feature.dashboard.application.v2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Independent reads of one dashboard request, run concurrently on virtual threads: the request
 * then waits for the slowest chain of dependent reads, not for their sum. Every read passes the
 * organization explicitly (no thread-local tenant is needed). {@link #join} rethrows a read's own
 * exception (a {@code DomainException} keeps its type and status); {@link #close} waits for every
 * started read.
 */
public final class ConcurrentLoads implements AutoCloseable {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public <T> CompletableFuture<T> supply(Supplier<T> read) {
        return CompletableFuture.supplyAsync(read, executor);
    }

    /** A read that needs the result of another. */
    public <T, R> CompletableFuture<R> then(CompletableFuture<T> previous, Function<T, R> read) {
        return previous.thenApplyAsync(read, executor);
    }

    public static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            if (e.getCause() instanceof Error error) throw error;
            throw e;
        }
    }

    @Override
    public void close() {
        executor.close();
    }
}
