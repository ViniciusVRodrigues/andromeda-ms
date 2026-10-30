package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.AttributionSource;
import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.application.usage.AgentRowDispositionPolicy.Disposition;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.agent.domain.AgentPrincipal;
import andromeda.sei.ms.feature.agent.domain.attribution.RemoteRepositoryRef;
import andromeda.sei.ms.feature.agent.domain.exception.AgentRequestInvalidException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTaskNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTokenInvalidException;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.application.usecase.RegisterAgentAllocationUseCase;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code andromeda task X} (agente-endpoint-v2, design 7): resolves the reference, keeps it as the
 * active task of the device and allocates, as AGENT, the rows of that day that are live, active and
 * still have no allocation. A row already allocated (by the agent or corrected by hand) is never touched.
 */
@Component
public class SetActiveTaskUseCase {

    public record Input(String taskRef, Instant since, LocalDate day, String repo, String branch) {

        public Input(String taskRef, Instant since, LocalDate day, String repo) {
            this(taskRef, since, day, repo, null);
        }
    }

    public record Output(String workItemId, String ref, String title, int retroactiveRows) {
    }

    private final AgentDeviceRepository devices;
    private final ResolveAgentTaskUseCase resolveTask;
    private final AgentRowDispositionPolicy dispositionPolicy;
    private final AgentUsageQueries usageQueries;
    private final RegisterAgentAllocationUseCase registerAllocation;
    private final Clock clock;

    @Autowired
    public SetActiveTaskUseCase(AgentDeviceRepository devices, ResolveAgentTaskUseCase resolveTask,
                                AgentRowDispositionPolicy dispositionPolicy, AgentUsageQueries usageQueries,
                                RegisterAgentAllocationUseCase registerAllocation) {
        this(devices, resolveTask, dispositionPolicy, usageQueries, registerAllocation, Clock.systemUTC());
    }

    SetActiveTaskUseCase(AgentDeviceRepository devices, ResolveAgentTaskUseCase resolveTask,
                         AgentRowDispositionPolicy dispositionPolicy, AgentUsageQueries usageQueries,
                         RegisterAgentAllocationUseCase registerAllocation, Clock clock) {
        this.devices = devices;
        this.resolveTask = resolveTask;
        this.dispositionPolicy = dispositionPolicy;
        this.usageQueries = usageQueries;
        this.registerAllocation = registerAllocation;
        this.clock = clock;
    }

    public Output execute(AgentPrincipal principal, Input in) {
        String org = principal.organizationId();
        String user = principal.userId();
        AgentDevice device = devices.findActiveByIdAndOrganizationId(principal.deviceId(), org)
                .orElseThrow(AgentTokenInvalidException::new);

        if (in.taskRef() == null || in.taskRef().isBlank()) throw new AgentRequestInvalidException("taskRef");
        if (in.day() == null) throw new AgentRequestInvalidException("day");
        Instant now = clock.instant();
        LocalDate today = now.atZone(zoneOf(device)).toLocalDate();
        if (!in.day().equals(today) && !in.day().equals(today.minusDays(1))) {
            throw new AgentRequestInvalidException("day deve ser hoje ou ontem no fuso do dispositivo");
        }

        ResolvedTask task = resolveTask.resolveRef(org, user, in.taskRef().trim())
                .orElseThrow(AgentTaskNotFoundException::new);
        String repo = in.repo() == null || in.repo().isBlank() ? null : in.repo().trim();
        String branch = in.branch() == null || in.branch().isBlank() ? null : in.branch().trim();
        Instant since = in.since() == null ? now : in.since();

        devices.setActiveTask(device.getId(), org,
                new AgentDevice.ActiveTask(task.workItemId(), task.ref(), task.title(), since, repo, in.day(), branch));

        int retro = retroact(org, user, in.day(), repo, branch, task);
        return new Output(task.workItemId(), task.ref(), task.title(), retro);
    }

    private int retroact(String org, String user, LocalDate day, String repo, String branch,
                         ResolvedTask task) {
        List<AIUsage> candidates = usageQueries.findUnallocatedAgentRows(org, user, day);
        if (candidates.isEmpty()) return 0;
        Map<String, AgentDevice> ownDevices = new HashMap<>();
        devices.findDevicesOfUser(org, user).forEach(d -> ownDevices.put(d.getId(), d));

        List<AIUsage> rows = candidates.stream()
                .filter(r -> repo == null || sameRepository(repo, r.getRepository()))
                .filter(r -> branch == null || r.getBranch() == null || r.getBranch().isBlank()
                        || branch.equals(r.getBranch().trim()))
                .filter(r -> {
                    AgentDevice owner = ownDevices.get(r.getDeviceId());
                    return owner != null && r.getBucketStart() != null
                            && dispositionPolicy.classify(owner, user, r.getDay(), r.getBucketStart(), r.getTool()) == Disposition.LIVE;
                })
                .toList();
        if (rows.isEmpty()) return 0;

        List<String> ids = rows.stream().map(AIUsage::getId).toList();
        registerAllocation.execute(new RegisterAgentAllocationUseCase.Registration(org, user, day,
                task.workItemId(), task.title(), task.projectId(), ids, AiUsageAllocation.ResolvedBy.MARK,
                rows.stream().map(AIUsage::getRepository).toList(), rows.stream().map(AIUsage::getBranch).toList()));
        usageQueries.stampTask(org, ids, task.workItemId(), task.projectId(), task.ref(),
                AttributionSource.ACTIVE_TASK.name());
        return rows.size();
    }

    private static boolean sameRepository(String wanted, String actual) {
        if (actual == null || actual.isBlank()) return false;
        Optional<RemoteRepositoryRef> a = RemoteRepositoryRef.parse(wanted);
        Optional<RemoteRepositoryRef> b = RemoteRepositoryRef.parse(actual);
        if (a.isPresent() && b.isPresent()) return a.get().equals(b.get());
        return wanted.trim().equalsIgnoreCase(actual.trim());
    }

    private static ZoneId zoneOf(AgentDevice device) {
        try {
            return device.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(device.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
