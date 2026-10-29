package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.CalculateAiRatiosUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Allocation without review of the agent rows (agente-endpoint-v2, design 5.4): the rows of a
 * {@code (day, workItem)} group become one {@code VALIDATED} allocation with {@code assignedBy = AGENT}
 * and a single split of 100%, and the rows get its {@code allocationId}. A group of the same day and
 * item that already has an AGENT allocation joins it (rows of later batches), keeping one allocation
 * per day and item.
 */
@Component
@RequiredArgsConstructor
public class RegisterAgentAllocationUseCase {

    private static final BigDecimal WHOLE = new BigDecimal("1.0000");

    private final AiUsageAllocationRepository allocationRepository;
    private final CalculateAiRatiosUseCase calculateAiRatiosUseCase;

    /** One group of new rows resolved to the same work item on the same day. */
    public record Registration(String organizationId, String userId, LocalDate day, String workItemId,
                               String workItemTitle, String projectId, List<String> usageIds,
                               AiUsageAllocation.ResolvedBy resolvedBy,
                               List<String> repositories, List<String> branches) {
    }

    /** Returns the id of the AGENT allocation that now holds the rows. */
    public String execute(Registration r) {
        List<AiUsageAllocation> ofTheDay = allocationRepository.findByOrgUserAndDay(r.organizationId(), r.userId(), r.day());
        AiUsageAllocation existing = ofTheDay.stream()
                .filter(AiUsageAllocation::isAgent)
                .filter(a -> a.getSplits() != null && a.getSplits().size() == 1
                        && r.workItemId().equals(a.getSplits().get(0).getWorkItemId()))
                .findFirst().orElse(null);

        Instant now = Instant.now();
        AiUsageAllocation saved;
        if (existing != null) {
            Set<String> ids = new LinkedHashSet<>(existing.getAiUsageIds() == null ? List.of() : existing.getAiUsageIds());
            ids.addAll(r.usageIds());
            existing.setAiUsageIds(new ArrayList<>(ids));
            existing.setAgent(merge(existing.getAgent(), r));
            existing.setUpdatedAt(now);
            saved = allocationRepository.save(existing);
        } else {
            AiUsageAllocation created = new AiUsageAllocation(null, r.organizationId(), r.userId(), r.day(),
                    new ArrayList<>(r.usageIds()), List.of(new AllocationSplit(r.workItemId(), r.workItemTitle(), WHOLE)),
                    AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.AssignedBy.AGENT, true, null, now, now);
            created.setAgent(new AiUsageAllocation.AgentInfo(r.resolvedBy(), distinct(r.repositories()), distinct(r.branches())));
            saved = allocationRepository.save(created);
        }
        allocationRepository.updateAllocationIdOnUsageRecords(r.usageIds(), saved.getId());
        if (r.projectId() != null) {
            calculateAiRatiosUseCase.execute(r.organizationId(), r.projectId());
        }
        return saved.getId();
    }

    private static AiUsageAllocation.AgentInfo merge(AiUsageAllocation.AgentInfo old, Registration r) {
        if (old == null) {
            return new AiUsageAllocation.AgentInfo(r.resolvedBy(), distinct(r.repositories()), distinct(r.branches()));
        }
        List<String> repos = new ArrayList<>(old.repositories() == null ? List.of() : old.repositories());
        repos.addAll(r.repositories());
        List<String> branches = new ArrayList<>(old.branches() == null ? List.of() : old.branches());
        branches.addAll(r.branches());
        return new AiUsageAllocation.AgentInfo(stronger(old.resolvedBy(), r.resolvedBy()), distinct(repos), distinct(branches));
    }

    /** MARK (the person said it) beats BRANCH beats SINGLE_ACTIVE. */
    private static AiUsageAllocation.ResolvedBy stronger(AiUsageAllocation.ResolvedBy a, AiUsageAllocation.ResolvedBy b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.ordinal() <= b.ordinal() ? a : b;
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(v -> v != null && !v.isBlank()).distinct().toList();
    }
}
