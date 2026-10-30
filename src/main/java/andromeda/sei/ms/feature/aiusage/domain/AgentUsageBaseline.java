package andromeda.sei.ms.feature.aiusage.domain;

import java.time.LocalDate;
import java.util.Map;

/**
 * Daily totals of what the local agent sent, to tell an unusual day from a normal one
 * (atribuicao-a-prova-de-esquecimento, 1.2). Separate from the summary ports on purpose.
 */
public interface AgentUsageBaseline {

    /**
     * Input plus output tokens (cache not counted) of the user's active rows with {@code source = AGENT},
     * by day within {@code [from, to]}. Days without rows are absent from the map.
     */
    Map<LocalDate, Long> dailyTokenTotals(String organizationId, String userId, LocalDate from, LocalDate to);
}
