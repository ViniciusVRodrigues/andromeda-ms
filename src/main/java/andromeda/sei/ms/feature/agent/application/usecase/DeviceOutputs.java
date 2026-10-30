package andromeda.sei.ms.feature.agent.application.usecase;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ActiveTaskOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.EndedTaskOutput;
import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.TaskConflictOutput;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;

final class DeviceOutputs {

    private DeviceOutputs() {
    }

    static ActiveTaskOutput activeTask(AgentDevice device) {
        AgentDevice.ActiveTask t = device.getActiveTask();
        return t == null ? null : new ActiveTaskOutput(t.workItemId(), t.taskRef(), t.title(), t.since(), t.repository(), t.branch(), t.day());
    }

    static EndedTaskOutput ended(AgentDevice.EndedTask e) {
        return e == null ? null : new EndedTaskOutput(e.workItemId(), e.ref(), e.title(), e.repo(), e.branch(),
                e.reason(), e.endedAt(), e.newBranch(), e.newRepo());
    }

    static TaskConflictOutput conflict(AgentDevice.TaskConflict c) {
        return c == null ? null : new TaskConflictOutput(c.taskRef(), c.branch(), c.branchRef(), c.repo(), c.at());
    }
}
