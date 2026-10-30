package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatInput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.HeartbeatOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.EndedTaskOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Objects;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Sign of life about every minute: records contact, version and backlog; never touches {@code lastUploadAt}. */
@Component
public class RecordHeartbeatUseCase {

    /** An ISO-8601 instant fits easily; anything longer is ignored, never a 400. */
    static final int MAX_SYNC_SERVED = 40;

    private final AgentDeviceRepository devices;
    /** The ended task is echoed to the agent for this long. */
    static final Duration ENDED_TASK_TTL = Duration.ofHours(24);

    private final AgentVersionPolicy versionPolicy;
    private final WorkItemRepository workItems;
    private final SprintRepository sprints;
    private final Clock clock;

    @Autowired
    public RecordHeartbeatUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy,
                                  WorkItemRepository workItems, SprintRepository sprints) {
        this(devices, versionPolicy, workItems, sprints, Clock.systemUTC());
    }

    RecordHeartbeatUseCase(AgentDeviceRepository devices, AgentVersionPolicy versionPolicy,
                           WorkItemRepository workItems, SprintRepository sprints, Clock clock) {
        this.devices = devices;
        this.versionPolicy = versionPolicy;
        this.workItems = workItems;
        this.sprints = sprints;
        this.clock = clock;
    }

    public HeartbeatOutput execute(AgentPrincipal principal, HeartbeatInput input) {
        Instant now = clock.instant();
        Integer pending = input.pendingRows() == null ? null : Math.max(0, input.pendingRows());
        String version = input.agentVersion() == null || input.agentVersion().isBlank() ? null
                : input.agentVersion().trim();
        String timezone = input.timezone() != null && ZoneId.getAvailableZoneIds().contains(input.timezone())
                ? input.timezone() : null; // invalid or absent: keeps the one of the pairing
        devices.recordHeartbeat(principal.deviceId(), principal.organizationId(), now, version, pending, timezone);

        Instant served = parseInstant(input.syncServed());
        if (served != null) {
            devices.findActiveByIdAndOrganizationId(principal.deviceId(), principal.organizationId())
                    .map(AgentDevice::getSyncRequestedAt)
                    .filter(req -> req.truncatedTo(ChronoUnit.MILLIS).equals(served.truncatedTo(ChronoUnit.MILLIS)))
                    .ifPresent(req -> devices.clearServedSync(principal.deviceId(), principal.organizationId(),
                            req, now));
        }

        if (input.taskConflict() != null) {
            devices.setTaskConflict(principal.deviceId(), principal.organizationId(), input.taskConflict().value());
        }

        AgentDevice device = devices.findActiveByIdAndOrganizationId(principal.deviceId(), principal.organizationId())
                .orElseThrow(AgentTokenInvalidException::new);
        endStaleTask(device, now);
        AgentDevice.EndedTask ended = device.getLastEndedTask();
        EndedTaskOutput endedOut = ended != null && ended.endedAt() != null
                && ended.endedAt().isAfter(now.minus(ENDED_TASK_TTL)) ? DeviceOutputs.ended(ended) : null;
        return new HeartbeatOutput(now, versionPolicy.minVersion(), DeviceOutputs.activeTask(device),
                device.getSyncRequestedAt(), endedOut);
    }

    /** Design 2.3: with an active task, ends it when the day turned, the item closed or left the sprint. */
    private void endStaleTask(AgentDevice device, Instant now) {
        AgentDevice.ActiveTask t = device.getActiveTask();
        if (t == null) return;
        String reason = endReason(device, t, now);
        if (reason == null) return;
        AgentDevice.EndedTask ended = new AgentDevice.EndedTask(t.workItemId(), t.taskRef(), t.title(), t.repository(),
                t.branch(), reason, now, null, null);
        devices.endActiveTask(device.getId(), device.getOrganizationId(), ended);
        device.setActiveTask(null);
        device.setTaskConflict(null);
        device.setLastEndedTask(ended);
    }

    private String endReason(AgentDevice device, AgentDevice.ActiveTask t, Instant now) {
        if (t.day() != null && now.atZone(zoneOf(device)).toLocalDate().isAfter(t.day())) return "DAY_ENDED";
        if (t.workItemId() == null) return null;
        // One read per heartbeat, only with an active task.
        WorkItem item = workItems.findByIdAndOrganizationIdAndActiveTrue(t.workItemId(), device.getOrganizationId())
                .orElse(null);
        if (item == null || item.getResolvedStatus() == WorkItem.ResolvedStatus.DONE) return "ITEM_CLOSED";
        if (item.getProjectId() == null) return null;
        Sprint current = sprints.findCurrentSprintByOrganizationIdAndProjectId(device.getOrganizationId(),
                item.getProjectId()).orElse(null);
        if (current != null && !Objects.equals(current.getId(), item.getSprintId())) return "LEFT_SPRINT";
        return null;
    }

    private static ZoneId zoneOf(AgentDevice device) {
        try {
            return device.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(device.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank() || text.length() > MAX_SYNC_SERVED) return null;
        try {
            return Instant.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null; // never fails the heartbeat
        }
    }
}
