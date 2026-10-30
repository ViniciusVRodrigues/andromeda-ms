package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Design 6, per tool (the AIEF editors that stand for the tool of the row):
 * before the cutover day a row is SUPERSEDED only when the person has an active AIEF row of the same tool on the
 * day; from the cutover day on it is SUPERSEDED when an AIEF row of the same tool is in a validated allocation, and
 * the AIEF rows of the tool without allocation go off when the agent row is stored (afterStored); otherwise the
 * pairing rule applies.
 */
@Component
public class CutoverRowDispositionPolicy implements AgentRowDispositionPolicy {

    private final AgentUsageQueries usageQueries;
    private final AiefEditorTools editorTools;
    private final PairingRowDispositionPolicy pairing = new PairingRowDispositionPolicy();

    public CutoverRowDispositionPolicy(AgentUsageQueries usageQueries, AiefEditorTools editorTools) {
        this.usageQueries = usageQueries;
        this.editorTools = editorTools;
    }

    @Override
    public Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart) {
        return classify(device, userId, day, bucketStart, null);
    }

    @Override
    public Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart, String tool) {
        return classify(device, userId, day, bucketStart, tool, this::supersededByAief);
    }

    @Override
    public void afterStored(AgentDevice device, String userId, LocalDate day, String tool) {
        LocalDate cutover = device.getCutoverDay();
        List<String> editors = editorTools.editorsOf(tool);
        if (cutover == null || day.isBefore(cutover) || editors.isEmpty()) return;
        usageQueries.supersedeAief(device.getOrganizationId(), userId, day, editors);
    }

    /**
     * Within a batch the AIEF check runs once per person, day and editors, and so does the supersede: the
     * supersede only turns off AIEF rows without allocation, which neither check reads (agt9).
     */
    @Override
    public AgentRowDispositionPolicy forBatch() {
        Map<String, Boolean> checked = new HashMap<>();
        Set<String> superseded = new HashSet<>();
        return new AgentRowDispositionPolicy() {
            @Override
            public Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart) {
                return classify(device, userId, day, bucketStart, null);
            }

            @Override
            public Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart,
                                        String tool) {
                return CutoverRowDispositionPolicy.this.classify(device, userId, day, bucketStart, tool,
                        (d, u, dd, editors) -> checked.computeIfAbsent(key(u, dd, editors),
                                k -> supersededByAief(d, u, dd, editors)));
            }

            @Override
            public void afterStored(AgentDevice device, String userId, LocalDate day, String tool) {
                String k = key(userId, day, editorTools.editorsOf(tool));
                if (superseded.contains(k)) return;
                CutoverRowDispositionPolicy.this.afterStored(device, userId, day, tool);
                superseded.add(k); // only once it worked: a failure lets the next row of the key try again
            }
        };
    }

    private interface AiefCheck {
        boolean superseded(AgentDevice device, String userId, LocalDate day, List<String> editors);
    }

    private Disposition classify(AgentDevice device, String userId, LocalDate day, Instant bucketStart, String tool,
                                 AiefCheck check) {
        List<String> editors = editorTools.editorsOf(tool);
        if (device.getCutoverDay() != null && !editors.isEmpty() && check.superseded(device, userId, day, editors)) {
            return Disposition.SUPERSEDED;
        }
        return pairing.classify(device, userId, day, bucketStart);
    }

    private boolean supersededByAief(AgentDevice device, String userId, LocalDate day, List<String> editors) {
        String org = device.getOrganizationId();
        return day.isBefore(device.getCutoverDay())
                ? usageQueries.existsActiveAief(org, userId, day, editors)
                : usageQueries.existsAiefInValidatedAllocation(org, userId, day, editors);
    }

    private static String key(String userId, LocalDate day, List<String> editors) {
        return userId + "|" + day + "|" + editors;
    }
}
