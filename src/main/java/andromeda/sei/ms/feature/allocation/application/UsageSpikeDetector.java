package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.aiusage.domain.AgentUsageBaseline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Tells a day of unusual agent use: more than 4x the median of the user's totals on the 28 days before it
 * that had use, with at least 5 of those days (atribuicao-a-prova-de-esquecimento, 1.2).
 */
@Component
@RequiredArgsConstructor
public class UsageSpikeDetector {

    static final int WINDOW_DAYS = 28;
    static final int MIN_HISTORY_DAYS = 5;
    static final int FACTOR = 4;

    private final AgentUsageBaseline baseline;

    /** The ratio (day total over the median, rounded down) when the day is a spike. */
    public OptionalLong ratioOf(String organizationId, String userId, LocalDate day) {
        Map<LocalDate, Long> totals = baseline.dailyTokenTotals(organizationId, userId, day.minusDays(WINDOW_DAYS), day);
        long today = totals.getOrDefault(day, 0L);
        List<Long> history = totals.entrySet().stream()
                .filter(e -> e.getKey().isBefore(day) && e.getValue() > 0)
                .map(Map.Entry::getValue).sorted().toList();
        if (today <= 0 || history.size() < MIN_HISTORY_DAYS) return OptionalLong.empty();
        int n = history.size();
        double median = n % 2 == 1 ? history.get(n / 2) : (history.get(n / 2 - 1) + history.get(n / 2)) / 2.0;
        if (today <= FACTOR * median) return OptionalLong.empty();
        return OptionalLong.of((long) Math.floor(today / median));
    }
}
