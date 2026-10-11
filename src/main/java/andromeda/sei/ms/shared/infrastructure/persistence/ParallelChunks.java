package andromeda.sei.ms.shared.infrastructure.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Runs one read per chunk of a large id list concurrently (virtual threads) and concatenates the
 * results in chunk order. For {@code $in} reads of tens of thousands of ids, where the time is the
 * server walking the index and fetching documents: separate cursors let MongoDB use several cores.
 * Small lists run inline on the caller's thread. Callers that can match one document from two
 * chunks must de-duplicate.
 */
public final class ParallelChunks {

    /** Ids per read. */
    public static final int CHUNK_SIZE = 1_000;
    /** At most this many reads in flight per call (well under the driver's connection pool). */
    public static final int MAX_PARALLEL = 8;

    private ParallelChunks() {}

    public static <T> List<T> read(Collection<String> ids, Function<List<String>, List<T>> reader) {
        /* ids nulos ou repetidos (documento malformado) não podem derrubar a leitura nem duplicar resultado */
        List<String> all = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (all.isEmpty()) return List.of();
        if (all.size() <= CHUNK_SIZE) return reader.apply(all);
        int chunkSize = Math.max(CHUNK_SIZE, (all.size() + MAX_PARALLEL - 1) / MAX_PARALLEL);
        List<List<String>> chunks = new ArrayList<>();
        for (int from = 0; from < all.size(); from += chunkSize) {
            chunks.add(all.subList(from, Math.min(all.size(), from + chunkSize)));
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<T>>> futures = chunks.stream().map(chunk -> executor.submit(() -> reader.apply(chunk))).toList();
            List<T> results = new ArrayList<>();
            for (Future<List<T>> future : futures) results.addAll(future.get());
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading chunks", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        }
    }
}
