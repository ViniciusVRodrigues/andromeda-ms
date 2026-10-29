package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentAIUsageRepository;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the server holds of this device, for the agent to compare with its local queue (design 8):
 * per day the row count, the token totals and a digest of the sorted keys, and on demand the keys of
 * one day. Only the rows of the calling device, active or not.
 */
@Component
public class ReconcileAgentUsageUseCase {

    static final int MAX_DAYS = 31;
    static final int MAX_KEYS = 5000;

    private final AgentAIUsageRepository usages;

    public ReconcileAgentUsageUseCase(AgentAIUsageRepository usages) {
        this.usages = usages;
    }

    public record Tokens(long input, long output, long cacheRead, long cacheWrite, long thinking) {
    }

    public record DayTotals(LocalDate day, int rows, Tokens tokens, String keysDigest) {
    }

    public record Reconciliation(String deviceId, List<DayTotals> days) {
    }

    public record DayKeys(LocalDate day, List<String> keys) {
    }

    public Reconciliation reconcile(AgentPrincipal principal, LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new AgentRequestInvalidException("from e to são obrigatórios");
        if (to.isBefore(from)) throw new AgentRequestInvalidException("to anterior a from");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new AgentRequestInvalidException("período maior que " + MAX_DAYS + " dias");
        }
        List<AIUsage> rows = usages.findCountersByDeviceAndDayBetween(
                principal.organizationId(), principal.deviceId(), from, to);
        Map<LocalDate, List<AIUsage>> byDay = new TreeMap<>();
        for (AIUsage row : rows) {
            byDay.computeIfAbsent(row.getDay(), d -> new ArrayList<>()).add(row);
        }
        List<DayTotals> days = new ArrayList<>();
        byDay.forEach((day, list) -> {
            long input = 0, output = 0, cacheRead = 0, cacheWrite = 0, thinking = 0;
            for (AIUsage u : list) {
                input += u.getInputTokens();
                output += u.getOutputTokens();
                cacheRead += u.getCacheReadTokens();
                cacheWrite += u.getCacheWriteTokens();
                thinking += u.getThinkingTokens();
            }
            List<String> keys = list.stream().map(AIUsage::getAgentKey).sorted().toList();
            days.add(new DayTotals(day, list.size(), new Tokens(input, output, cacheRead, cacheWrite, thinking),
                    digest(keys)));
        });
        return new Reconciliation(principal.deviceId(), days);
    }

    public DayKeys keys(AgentPrincipal principal, LocalDate day) {
        if (day == null) throw new AgentRequestInvalidException("day é obrigatório");
        return new DayKeys(day, usages.findKeysByDeviceAndDay(
                principal.organizationId(), principal.deviceId(), day, MAX_KEYS));
    }

    /** SHA-256 hex of the keys sorted in binary (UTF-8) order, joined by a line feed. */
    static String digest(List<String> sortedKeys) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(String.join("\n", sortedKeys).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
