package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.application.AgentReviewReasonCalculator;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
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
 * The agent's rows become a suggestion, never a confirmed allocation (atribuicao-a-prova-de-esquecimento,
 * 1.1): the rows of a {@code (day, workItem)} group go to one {@code PENDING_VALIDATION} allocation with
 * {@code assignedBy = AGENT} and a single split of 100%, and the rows get its {@code allocationId}. Rows of
 * later batches join the pending one of the same day and item; once the person confirmed it, new rows start
 * another pending allocation. The review reasons are recalculated on every create and join. Ratios are only
 * recalculated when the person confirms.
 */
@Component
@RequiredArgsConstructor
public class RegisterAgentAllocationUseCase {

    private static final BigDecimal WHOLE = new BigDecimal("1.0000");

    private final AiUsageAllocationRepository allocationRepository;
    private final AgentReviewReasonCalculator reviewReasons;

    /** One group of new rows resolved to the same work item on the same day. */
    public record Registration(String organizationId, String userId, LocalDate day, String workItemId,
                               String workItemTitle, String projectId, List<String> usageIds,
                               AiUsageAllocation.ResolvedBy resolvedBy,
                               List<String> repositories, List<String> branches) {
    }

    /** Returns the id of the pending AGENT allocation that now holds the rows. */
    public String execute(Registration r) {
        List<AiUsageAllocation> ofTheDay = allocationRepository.findByOrgUserAndDay(r.organizationId(), r.userId(), r.day());
        AiUsageAllocation existing = ofTheDay.stream()
                .filter(AiUsageAllocation::isAgent)
                .filter(AiUsageAllocation::isActive)
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION)
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
            existing.setReviewReasons(reviewReasons.reasonsFor(r.organizationId(), r.userId(), r.day(), r.workItemId()));
            existing.setUpdatedAt(now);
            saved = allocationRepository.save(existing);
        } else {
            AiUsageAllocation created = new AiUsageAllocation(null, r.organizationId(), r.userId(), r.day(),
                    new ArrayList<>(r.usageIds()), List.of(new AllocationSplit(r.workItemId(), r.workItemTitle(), WHOLE)),
                    AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AGENT, true, null, now, now);
            created.setAgent(new AiUsageAllocation.AgentInfo(r.resolvedBy(), distinct(r.repositories()), distinct(r.branches())));
            created.setReviewReasons(reviewReasons.reasonsFor(r.organizationId(), r.userId(), r.day(), r.workItemId()));
            saved = allocationRepository.save(created);
        }
        allocationRepository.updateAllocationIdOnUsageRecords(r.usageIds(), saved.getId());
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
        return new AiUsageAllocation.AgentInfo(stronger(old.resolvedBy(), r.resolvedBy()), distinct(repos), distinct(branches),
                old.overriddenTaskRef());
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
