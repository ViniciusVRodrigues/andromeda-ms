package andromeda.sei.ms.shared.domain.metrics;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PercentilesTest {

    @Test
    void should_returnNullPercentilesAndZeroN_when_noValues() {
        assertThat(Percentiles.of(List.of())).isEqualTo(new DurationStat(null, null, 0));
        assertThat(Percentiles.of(null)).isEqualTo(new DurationStat(null, null, 0));
    }

    @Test
    void should_ignoreNulls_when_computingPercentiles() {
        DurationStat stat = Percentiles.of(Arrays.asList(null, 4.0, null));

        assertThat(stat).isEqualTo(new DurationStat(4.0, 4.0, 1));
    }

    @Test
    void should_returnTheValueForBothPercentiles_when_singleValue() {
        assertThat(Percentiles.of(List.of(7.5))).isEqualTo(new DurationStat(7.5, 7.5, 1));
    }

    @Test
    void should_useNearestRank_when_distributionIsSkewed() {
        DurationStat stat = Percentiles.of(List.of(100.0, 3.0, 2.0, 4.0, 3.0));

        assertThat(stat).isEqualTo(new DurationStat(3.0, 100.0, 5));
    }

    @Test
    void should_pickMiddleRank_when_threeValues() {
        DurationStat stat = Percentiles.of(List.of(10.0, 20.0, 15.0));

        assertThat(stat.p50()).isEqualTo(15.0);
        assertThat(stat.p90()).isEqualTo(20.0);
    }

    @Test
    void should_pickLowerMiddle_when_evenCount() {
        DurationStat stat = Percentiles.of(List.of(1.0, 2.0, 3.0, 4.0));

        assertThat(stat.p50()).isEqualTo(2.0);
        assertThat(stat.p90()).isEqualTo(4.0);
    }
}
