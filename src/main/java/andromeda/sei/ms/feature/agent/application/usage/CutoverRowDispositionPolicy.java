package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        LocalDate cutover = device.getCutoverDay();
        List<String> editors = editorTools.editorsOf(tool);
        if (cutover != null && !editors.isEmpty()) {
            String org = device.getOrganizationId();
            boolean superseded = day.isBefore(cutover)
                    ? usageQueries.existsActiveAief(org, userId, day, editors)
                    : usageQueries.existsAiefInValidatedAllocation(org, userId, day, editors);
            if (superseded) return Disposition.SUPERSEDED;
        }
        return pairing.classify(device, userId, day, bucketStart);
    }

    @Override
    public void afterStored(AgentDevice device, String userId, LocalDate day, String tool) {
        LocalDate cutover = device.getCutoverDay();
        List<String> editors = editorTools.editorsOf(tool);
        if (cutover == null || day.isBefore(cutover) || editors.isEmpty()) return;
        usageQueries.supersedeAief(device.getOrganizationId(), userId, day, editors);
    }
}
