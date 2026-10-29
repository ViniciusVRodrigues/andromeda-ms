package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.dto.AgentDtos.ActiveTaskOutput;
import andromeda.sei.ms.feature.agent.application.usage.AgentRowDispositionPolicy.Disposition;
import andromeda.sei.ms.feature.agent.domain.AgentDevice;
import andromeda.sei.ms.feature.agent.domain.AgentDeviceRepository;
import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * "What is being tracked right now" (agente-endpoint-v2, design 13.2): the person's own agent usage of
 * the last 24 hours, grouped by device, repository, branch and task, plus the live rows that still
 * have no task, for today in the time zone of their device.
 */
@Component
public class GetAgentTodayUseCase {

    static final Duration WINDOW = Duration.ofHours(24);

    public record WorkItemRef(String id, String displayId, String title) {
    }

    /** {@code resolvedBy}: MARK, BRANCH or SINGLE_ACTIVE. */
    public record ResolvedGroup(String repo, String branch, boolean branchApproximate, WorkItemRef workItem,
                                String resolvedBy, Instant since, String deviceId) {
    }

    public record UnassignedGroup(String repo, int rows, long outputTokens, String deviceId) {
    }

    /** The task each active device of the person has marked ({@code activeTask} is null when none). */
    public record DeviceTask(String deviceId, String machineName, ActiveTaskOutput activeTask) {
    }

    public record Today(List<ResolvedGroup> resolved, List<UnassignedGroup> unassigned, List<DeviceTask> devices) {
    }

    private record GroupKey(String deviceId, String repo, String branch, String workItemId) {
    }

    private final AgentUsageQueries usageQueries;
    private final AgentDeviceRepository devices;
    private final AgentRowDispositionPolicy dispositionPolicy;
    private final WorkItemRepository workItems;
    private final Clock clock;

    @Autowired
    public GetAgentTodayUseCase(AgentUsageQueries usageQueries, AgentDeviceRepository devices,
                                AgentRowDispositionPolicy dispositionPolicy, WorkItemRepository workItems) {
        this(usageQueries, devices, dispositionPolicy, workItems, Clock.systemUTC());
    }

    GetAgentTodayUseCase(AgentUsageQueries usageQueries, AgentDeviceRepository devices,
                         AgentRowDispositionPolicy dispositionPolicy, WorkItemRepository workItems, Clock clock) {
        this.usageQueries = usageQueries;
        this.devices = devices;
        this.dispositionPolicy = dispositionPolicy;
        this.workItems = workItems;
        this.clock = clock;
    }

    public Today execute(String organizationId, String userId) {
        Instant now = clock.instant();
        Instant since = now.minus(WINDOW).truncatedTo(ChronoUnit.HOURS);
        List<AIUsage> rows = usageQueries.findActiveAgentRowsSince(organizationId, userId, since);

        Map<String, AgentDevice> ownDevices = new HashMap<>();
        List<DeviceTask> deviceTasks = new ArrayList<>();
        for (AgentDevice d : devices.findDevicesOfUser(organizationId, userId)) {
            ownDevices.put(d.getId(), d);
            if (!d.isRevoked()) {
                AgentDevice.ActiveTask t = d.getActiveTask();
                deviceTasks.add(new DeviceTask(d.getId(), d.getMachineName(), t == null ? null
                        : new ActiveTaskOutput(t.workItemId(), t.taskRef(), t.title(), t.since(), t.repository())));
            }
        }
        if (rows.isEmpty()) return new Today(List.of(), List.of(), deviceTasks);

        Map<GroupKey, List<AIUsage>> resolved = new LinkedHashMap<>();
        Map<String, List<AIUsage>> unassigned = new LinkedHashMap<>();
        for (AIUsage row : rows) {
            if (row.getWorkItemId() != null) {
                resolved.computeIfAbsent(new GroupKey(row.getDeviceId(), row.getRepository(), row.getBranch(),
                        row.getWorkItemId()), k -> new ArrayList<>()).add(row);
            } else if (isLiveToday(row, ownDevices.get(row.getDeviceId()), userId, now)) {
                unassigned.computeIfAbsent(row.getDeviceId() + "|" + row.getRepository(), k -> new ArrayList<>())
                        .add(row);
            }
        }

        Map<String, Optional<WorkItem>> items = new HashMap<>();
        List<ResolvedGroup> resolvedOut = resolved.entrySet().stream()
                .map(e -> toResolved(organizationId, e.getKey(), e.getValue(), items))
                .sorted(Comparator.comparing(ResolvedGroup::since).reversed())
                .toList();
        List<UnassignedGroup> unassignedOut = unassigned.values().stream()
                .map(g -> new UnassignedGroup(g.get(0).getRepository(), g.size(),
                        g.stream().mapToLong(AIUsage::getOutputTokens).sum(), g.get(0).getDeviceId()))
                .toList();
        return new Today(resolvedOut, unassignedOut, deviceTasks);
    }

    private ResolvedGroup toResolved(String organizationId, GroupKey key, List<AIUsage> group,
                                     Map<String, Optional<WorkItem>> items) {
        Optional<WorkItem> item = items.computeIfAbsent(key.workItemId(),
                id -> workItems.findByIdAndOrganizationIdAndActiveTrue(id, organizationId));
        Instant since = group.stream().map(AIUsage::getBucketStart).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(Instant.EPOCH);
        String ref = item.map(GetAgentTodayUseCase::displayId).orElse(group.get(0).getTaskRef());
        return new ResolvedGroup(key.repo(), key.branch(),
                group.stream().anyMatch(AIUsage::isBranchApproximate),
                new WorkItemRef(key.workItemId(), ref, item.map(WorkItem::getTitle).orElse(null)),
                resolvedBy(group), since, key.deviceId());
    }

    /** The strongest way the group was resolved: MARK, then BRANCH, then SINGLE_ACTIVE. */
    static String resolvedBy(List<AIUsage> group) {
        int best = 3;
        for (AIUsage row : group) {
            best = Math.min(best, rank(row.getAttributionSource()));
        }
        return switch (best) {
            case 0 -> "MARK";
            case 1 -> "BRANCH";
            default -> "SINGLE_ACTIVE";
        };
    }

    private static int rank(String attributionSource) {
        if (attributionSource == null) return 2;
        return switch (attributionSource) {
            case "TASK_REF", "ACTIVE_TASK", "MANUAL" -> 0;
            case "BRANCH" -> 1;
            default -> 2;
        };
    }

    private boolean isLiveToday(AIUsage row, AgentDevice owner, String userId, Instant now) {
        if (owner == null || row.getBucketStart() == null || row.getDay() == null) return false;
        if (!row.getDay().equals(now.atZone(zoneOf(owner)).toLocalDate())) return false;
        return dispositionPolicy.classify(owner, userId, row.getDay(), row.getBucketStart(), row.getTool()) == Disposition.LIVE;
    }

    private static String displayId(WorkItem item) {
        return item.getSource() == WorkItem.Source.JIRA ? item.getExternalId() : "AB#" + item.getExternalId();
    }

    private static ZoneId zoneOf(AgentDevice device) {
        try {
            return device.getTimezone() == null ? ZoneOffset.UTC : ZoneId.of(device.getTimezone());
        } catch (DateTimeException e) {
            return ZoneOffset.UTC;
        }
    }
}
