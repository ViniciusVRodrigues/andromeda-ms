package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.aiusage.application.AiefEditorTools;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.aiusage.domain.AiefCoexistence;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * "Agent day" of a person (design 6): a day on or after the cutover day of one of their devices that
 * is still active, or was revoked on that day or later. On those days the AIEF upload is ignored.
 */
@Component
public class AgentDayPolicy implements AiefCoexistence {

    private final AgentDeviceRepository devices;
    private final AgentUsageQueries usageQueries;
    private final AiefEditorTools editorTools;

    public AgentDayPolicy(AgentDeviceRepository devices, AgentUsageQueries usageQueries, AiefEditorTools editorTools) {
        this.devices = devices;
        this.usageQueries = usageQueries;
        this.editorTools = editorTools;
    }

    /**
     * Design 6, per tool. Without a device nothing changes. On an agent day the entry is ignored only when its
     * editor maps to a tool and the agent already has an active row of that tool on the day; an entry without
     * a tool is always stored, flagged when it looks like the agent's (editor Claude or Codex, or a model the
     * agent sent that day).
     */
    @Override
    public Verdict verdict(String organizationId, String userId, LocalDate day, String editor, String model) {
        List<AgentDevice> own = devices.findDevicesOfUser(organizationId, userId);
        if (own == null || own.isEmpty()) return Verdict.STORE;
        String tool = editorTools.toolOf(editor);
        if (tool != null) {
            boolean agentDay = own.stream().anyMatch(d -> coversDay(d, day));
            return agentDay && usageQueries.existsActiveAgentRow(organizationId, userId, day, tool)
                    ? Verdict.IGNORE : Verdict.STORE;
        }
        if (AiefEditorTools.looksLikeAgentTool(editor)) return Verdict.STORE_FLAGGED;
        if (model != null && usageQueries.agentModelsOfDay(organizationId, userId, day).contains(model.trim())) {
            return Verdict.STORE_FLAGGED;
        }
        return Verdict.STORE;
    }

    static boolean coversDay(AgentDevice device, LocalDate day) {
        if (device.getCutoverDay() == null || device.getCutoverDay().isAfter(day)) return false;
        if (!device.isRevoked()) return true;
        if (device.getRevokedAt() == null) return false;
        LocalDate revokedDay = device.getRevokedAt().atZone(zoneOf(device)).toLocalDate();
        return !revokedDay.isBefore(day);
    }

    private static ZoneId zoneOf(AgentDevice device) {
        try {
            return device.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(device.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
