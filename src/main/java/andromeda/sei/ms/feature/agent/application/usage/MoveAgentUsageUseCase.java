package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.agent.application.attribution.ResolveAgentTaskUseCase;
import andromeda.sei.ms.feature.agent.application.attribution.ResolvedTask;
import andromeda.sei.ms.feature.agent.domain.exception.AgentTaskNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotAgentException;
import andromeda.sei.ms.feature.agent.domain.exception.AiAllocationNotFoundException;
import andromeda.sei.ms.feature.agent.domain.exception.AiUsageNotInAllocationException;
import andromeda.sei.ms.feature.aiusage.domain.AgentUsageQueries;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The person moves rows of an AGENT allocation to another task (agente-endpoint-v2, design 15). The rows
 * go to a new {@code MANUAL} {@code VALIDATED} allocation ({@code movedFromAgent}) of the same day; the
 * AGENT one is deactivated when it runs empty. From then on the rows belong to the person: no resend,
 * reconciliation or {@code andromeda task} takes them back, because those flows only raise the counters
 * and only allocate rows without an allocation.
 */
@Component
public class MoveAgentUsageUseCase {

    public record Split(String workItemId, String workItemTitle, BigDecimal percentage) {
    }

    public record AllocationView(String id, java.time.LocalDate day, AiUsageAllocation.Status status,
                                 AiUsageAllocation.AssignedBy assignedBy, boolean active, boolean movedFromAgent,
                                 List<String> aiUsageIds, List<Split> splits, AiUsageAllocation.AgentInfo agent) {
    }

    public record MoveResult(AllocationView source, AllocationView target) {
    }

    private static final BigDecimal WHOLE = new BigDecimal("1.0000");
    /** Value of {@code attributionSource} stamped on rows the person moved by hand. */
    static final String MANUAL_SOURCE = "MANUAL";

    private final AiUsageAllocationRepository allocations;
    private final ResolveAgentTaskUseCase resolveTask;
    private final AgentUsageQueries usageQueries;
    private final CalculateAiRatiosUseCase calculateRatios;
    private final WorkItemRepository workItems;

    public MoveAgentUsageUseCase(AiUsageAllocationRepository allocations, ResolveAgentTaskUseCase resolveTask,
                                 AgentUsageQueries usageQueries, CalculateAiRatiosUseCase calculateRatios,
                                 WorkItemRepository workItems) {
        this.allocations = allocations;
        this.resolveTask = resolveTask;
        this.usageQueries = usageQueries;
        this.calculateRatios = calculateRatios;
        this.workItems = workItems;
    }

    public MoveResult execute(String organizationId, String userId, String allocationId,
                              List<String> aiUsageIds, String workItemId) {
        AiUsageAllocation source = allocations.findById(allocationId, organizationId)
                .filter(a -> userId.equals(a.getUserId()))
                .orElseThrow(AiAllocationNotFoundException::new);
        if (!source.isAgent()) throw new AiAllocationNotAgentException();

        Set<String> ids = new LinkedHashSet<>(aiUsageIds == null ? List.of() : aiUsageIds);
        Set<String> inSource = new LinkedHashSet<>(source.getAiUsageIds() == null ? List.of() : source.getAiUsageIds());
        if (ids.isEmpty() || !inSource.containsAll(ids)) throw new AiUsageNotInAllocationException();

        ResolvedTask target = resolveTask.resolveItem(organizationId, userId, workItemId)
                .orElseThrow(AgentTaskNotFoundException::new);

        Instant now = Instant.now();
        AiUsageAllocation created = new AiUsageAllocation(null, organizationId, userId, source.getDay(),
                new ArrayList<>(ids), List.of(new AllocationSplit(target.workItemId(), target.title(), WHOLE)),
                AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.MANUAL, true, null, now, now);
        created.setMovedFromAgent(true);
        AiUsageAllocation savedTarget = allocations.save(created);
        allocations.updateAllocationIdOnUsageRecords(new ArrayList<>(ids), savedTarget.getId());
        usageQueries.stampTask(organizationId, ids, target.workItemId(), target.projectId(), target.ref(),
                MANUAL_SOURCE);

        inSource.removeAll(ids);
        source.setAiUsageIds(new ArrayList<>(inSource));
        if (inSource.isEmpty()) source.setActive(false);
        source.setUpdatedAt(now);
        AiUsageAllocation savedSource = allocations.save(source);

        recalculateRatios(organizationId, source, target);
        return new MoveResult(view(savedSource), view(savedTarget));
    }

    private void recalculateRatios(String organizationId, AiUsageAllocation source, ResolvedTask target) {
        Set<String> projects = new LinkedHashSet<>();
        if (target.projectId() != null) projects.add(target.projectId());
        if (source.getSplits() != null) {
            source.getSplits().forEach(s -> workItems.findByIdAndOrganizationIdAndActiveTrue(s.getWorkItemId(), organizationId)
                    .map(i -> i.getProjectId()).ifPresent(projects::add));
        }
        projects.forEach(p -> calculateRatios.execute(organizationId, p));
    }

    private static AllocationView view(AiUsageAllocation a) {
        return new AllocationView(a.getId(), a.getDay(), a.getStatus(), a.getAssignedBy(), a.isActive(),
                a.isMovedFromAgent(), a.getAiUsageIds() == null ? List.of() : a.getAiUsageIds(),
                a.getSplits() == null ? List.of() : a.getSplits().stream()
                        .map(s -> new Split(s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage())).toList(),
                a.getAgent());
    }
}
