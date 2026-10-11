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

    @Test
    void should_returnNearestRankP85_ignoringNulls() {
        List<Double> values = new java.util.ArrayList<>(List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0));
        values.add(null);

        // rank = ceil(0.85 × 10) = 9 → the 9th smallest value
        assertThat(Percentiles.percentile(values, 85)).isEqualTo(9.0);
    }

    @Test
    void should_returnNullPercentile_when_noSample() {
        assertThat(Percentiles.percentile(List.of(), 85)).isNull();
        assertThat(Percentiles.percentile(null, 85)).isNull();
    }
}
