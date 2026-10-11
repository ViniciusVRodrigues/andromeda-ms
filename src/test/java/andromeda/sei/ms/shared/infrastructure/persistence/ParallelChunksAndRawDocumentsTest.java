package andromeda.sei.ms.shared.infrastructure.persistence;

import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * dashboards-customizaveis (B1 performance) — the helpers of the raw hot reads: chunked concurrent
 * reads return every result in chunk order and rethrow a chunk's failure; raw values convert like
 * the mapped path (ObjectId ids, Decimal128 decimals).
 */
class ParallelChunksAndRawDocumentsTest {

    @Test
    void should_readSmallListsInline_inOneCall() {
        List<List<String>> calls = Collections.synchronizedList(new ArrayList<>());
        List<String> ids = ids(ParallelChunks.CHUNK_SIZE);

        List<String> out = ParallelChunks.read(ids, chunk -> { calls.add(chunk); return chunk; });

        assertThat(calls).hasSize(1);
        assertThat(out).containsExactlyElementsOf(ids);
    }

    @Test
    void should_splitLargeLists_intoAtMostMaxParallelChunks_keepingEveryResultInOrder() {
        List<List<String>> calls = Collections.synchronizedList(new ArrayList<>());
        List<String> ids = ids(25_000);

        List<String> out = ParallelChunks.read(ids, chunk -> { calls.add(chunk); return chunk; });

        assertThat(calls).hasSizeBetween(2, ParallelChunks.MAX_PARALLEL);
        assertThat(out).containsExactlyElementsOf(ids);
    }

    @Test
    void should_ignoreNullAndRepeatedIds_withoutCallingTheReaderForNothing() {
        List<List<String>> calls = new ArrayList<>();
        List<String> out = ParallelChunks.read(java.util.Arrays.asList("a", null, "b", "a"), chunk -> {
            calls.add(chunk);
            return chunk;
        });
        assertThat(out).containsExactly("a", "b");

        List<String> none = ParallelChunks.read(java.util.Arrays.asList((String) null), chunk -> {
            calls.add(chunk);
            return chunk;
        });
        assertThat(none).isEmpty();
        assertThat(calls).hasSize(1);
    }

    @Test
    void should_rethrowAChunksFailure() {
        assertThatThrownBy(() -> ParallelChunks.read(ids(5_000), chunk -> {
            if (chunk.contains("id-4999")) throw new IllegalArgumentException("boom");
            return chunk;
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
    }

    @Test
    void should_convertIdsAndDecimals_likeTheMappedPath() {
        String hex = new ObjectId().toHexString();

        assertThat(RawDocuments.storedId(hex)).isEqualTo(new ObjectId(hex));
        assertThat(RawDocuments.storedId("not-hex")).isEqualTo("not-hex");
        assertThat(RawDocuments.decimal(new Decimal128(new BigDecimal("1.25")))).isEqualByComparingTo("1.25");
        assertThat(RawDocuments.decimal(2.5d)).isEqualByComparingTo("2.5");
        assertThat(RawDocuments.decimal(null)).isNull();
        assertThat(RawDocuments.longValue(7)).isEqualTo(7L);
        assertThat(RawDocuments.strings(List.of("a", "b"))).containsExactly("a", "b");
    }

    private static List<String> ids(int n) {
        return IntStream.range(0, n).mapToObj(i -> "id-" + i).toList();
    }
}
