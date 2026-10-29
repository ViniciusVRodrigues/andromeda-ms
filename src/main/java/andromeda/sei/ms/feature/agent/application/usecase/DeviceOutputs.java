package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ActiveTaskOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;

final class DeviceOutputs {

    private DeviceOutputs() {
    }

    static ActiveTaskOutput activeTask(AgentDevice device) {
        AgentDevice.ActiveTask t = device.getActiveTask();
        return t == null ? null : new ActiveTaskOutput(t.workItemId(), t.taskRef(), t.title(), t.since(), t.repository());
    }
}
