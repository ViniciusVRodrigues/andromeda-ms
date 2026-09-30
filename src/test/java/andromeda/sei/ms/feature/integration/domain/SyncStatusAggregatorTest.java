package andromeda.sei.ms.feature.integration.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static andromeda.sei.ms.feature.integration.domain.SyncStatus.FAILED;
import static andromeda.sei.ms.feature.integration.domain.SyncStatus.PARTIAL;
import static andromeda.sei.ms.feature.integration.domain.SyncStatus.RUNNING;
import static andromeda.sei.ms.feature.integration.domain.SyncStatus.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;

class SyncStatusAggregatorTest {

    @Test
    void shouldBeRunningWhileAnyChildRuns() {
        assertThat(SyncStatusAggregator.aggregate(List.of(SUCCESS, RUNNING, FAILED))).isEqualTo(RUNNING);
    }

    @Test
    void shouldBeSuccessWhenEveryChildSucceeded() {
        assertThat(SyncStatusAggregator.aggregate(List.of(SUCCESS, SUCCESS))).isEqualTo(SUCCESS);
    }

    @Test
    void shouldBeFailedWhenEveryChildFailed() {
        assertThat(SyncStatusAggregator.aggregate(List.of(FAILED, FAILED))).isEqualTo(FAILED);
    }

    @Test
    void shouldBePartialWhenSuccessAndFailureMix() {
        assertThat(SyncStatusAggregator.aggregate(List.of(SUCCESS, FAILED))).isEqualTo(PARTIAL);
    }

    @Test
    void shouldNotReportSuccessWhenAChildHadItemFailures() {
        assertThat(SyncStatusAggregator.aggregate(List.of(SUCCESS, PARTIAL))).isEqualTo(PARTIAL);
    }

    @Test
    void shouldBePartialWhenOnlyChildIsPartial() {
        assertThat(SyncStatusAggregator.aggregate(List.of(PARTIAL))).isEqualTo(PARTIAL);
    }

    @Test
    void shouldBeSuccessWhenThereWasNothingToSync() {
        assertThat(SyncStatusAggregator.aggregate(List.of())).isEqualTo(SUCCESS);
    }
}
