package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.aiusage.domain.AgentUsageBaseline;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** atribuicao-a-prova-de-esquecimento 1.2: more than 4x the median of the 28 previous days with use, 5 days at least. */
class UsageSpikeDetectorTest {

    static final LocalDate DAY = LocalDate.of(2026, 10, 29);
    final AgentUsageBaseline baseline = mock(AgentUsageBaseline.class);
    final UsageSpikeDetector detector = new UsageSpikeDetector(baseline);

    Map<LocalDate, Long> history(long today, long... previous) {
        Map<LocalDate, Long> totals = new HashMap<>();
        totals.put(DAY, today);
        for (int i = 0; i < previous.length; i++) totals.put(DAY.minusDays(i + 1), previous[i]);
        when(baseline.dailyTokenTotals("org-1", "user-1", DAY.minusDays(28), DAY)).thenReturn(totals);
        return totals;
    }

    @Test
    void should_flagTheDay_whenMoreThanFourTimesTheMedian_withTheRatioRoundedDown() {
        history(4_500, 1_000, 1_000, 1_000, 1_000, 1_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).hasValue(4);
        history(8_500, 2_000, 2_000, 1_000, 2_000, 2_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).hasValue(4);
    }

    @Test
    void should_notFlag_whenExactlyFourTimesTheMedian() {
        history(4_000, 1_000, 1_000, 1_000, 1_000, 1_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).isEmpty();
    }

    @Test
    void should_notFlag_withOnlyFourDaysOfHistory() {
        history(1_000_000, 1_000, 1_000, 1_000, 1_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).isEmpty();
    }

    @Test
    void should_flag_withExactlyFiveDaysOfHistory() {
        history(10_000, 1_000, 1_000, 1_000, 1_000, 1_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).hasValue(10);
    }

    @Test
    void should_useTheMedianOfAnEvenHistory_asTheMeanOfTheMiddleTwo() {
        // sorted 1000,1000,1000,3000,3000,3000 -> median 2000 -> 8000 is exactly 4x: no spike; 8001 is
        history(8_000, 1_000, 1_000, 1_000, 3_000, 3_000, 3_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).isEmpty();
        history(8_001, 1_000, 1_000, 1_000, 3_000, 3_000, 3_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).hasValue(4);
    }

    @Test
    void should_ignoreTheDayItselfAndEmptyDaysInTheHistory() {
        Map<LocalDate, Long> totals = history(50_000, 1_000, 1_000, 1_000, 1_000);
        totals.put(DAY.minusDays(10), 0L); // a day without use does not count
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).isEmpty();
    }

    @Test
    void should_notFlag_whenTheDayHasNoUse() {
        history(0, 1_000, 1_000, 1_000, 1_000, 1_000);
        assertThat(detector.ratioOf("org-1", "user-1", DAY)).isEmpty();
    }
}
