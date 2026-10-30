package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * {@code andromeda task --clear} and the agent 0.5.0 automatic end (branch or repository changed, day ended):
 * forgets the active task of the device and remembers why in {@code lastEndedTask}. What was allocated stays.
 */
@Component
public class ClearActiveTaskUseCase {

    /** {@code reason} null means CLEARED. */
    public record Input(String reason, String newBranch, String newRepo) {
    }

    private final AgentDeviceRepository devices;
    private final Clock clock;

    @Autowired
    public ClearActiveTaskUseCase(AgentDeviceRepository devices) {
        this(devices, Clock.systemUTC());
    }

    ClearActiveTaskUseCase(AgentDeviceRepository devices, Clock clock) {
        this.devices = devices;
        this.clock = clock;
    }

    public void execute(AgentPrincipal principal) {
        execute(principal, new Input(null, null, null));
    }

    public void execute(AgentPrincipal principal, Input in) {
        String reason = in == null || in.reason() == null || in.reason().isBlank() ? "CLEARED" : in.reason().trim();
        if (!AgentDevice.END_REASONS.contains(reason)) throw new AgentRequestInvalidException("reason");
        AgentDevice device = devices.findActiveByIdAndOrganizationId(principal.deviceId(), principal.organizationId())
                .orElse(null);
        AgentDevice.ActiveTask t = device == null ? null : device.getActiveTask();
        if (t == null) {
            devices.setActiveTask(principal.deviceId(), principal.organizationId(), null);
            return;
        }
        devices.endActiveTask(principal.deviceId(), principal.organizationId(),
                new AgentDevice.EndedTask(t.workItemId(), t.taskRef(), t.title(), t.repository(), t.branch(), reason,
                        clock.instant(), blankToNull(in == null ? null : in.newBranch()),
                        blankToNull(in == null ? null : in.newRepo())));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
