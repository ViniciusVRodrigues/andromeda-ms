package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.CapContext;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.Decision;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.ItemRef;
import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.UsageRow;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.organization.domain.AllocationPolicy;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.project.domain.Project;
import andromeda.sei.ms.feature.project.domain.ProjectRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Gathers what {@link AllocationApprovalPolicy} needs (rows and their repository projects, the projects
 * of the split items, the month budget) and asks it. Every path that would validate an allocation goes
 * through {@link #evaluate} (uso-ia-fora-de-entrega, design T4).
 */
@Service
@RequiredArgsConstructor
public class AllocationApprovalService {

    private static final java.util.List<AiUsageAllocation.Status> BUDGET_STATUSES = java.util.List.of(
            AiUsageAllocation.Status.VALIDATED, AiUsageAllocation.Status.AWAITING_APPROVAL);

    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;
    private final WorkItemRepository workItemRepository;
    private final ProjectRepository projectRepository;
    private final OrganizationRepository organizationRepository;
    private final UsageRepositoryProjectPort repositoryProjectPort;

    /** The person's month of "Fora de entrega": cap, base and what is already used (validated / awaiting). */
    public record Budget(int capPercent, BigDecimal baseUsd, String baseMonth,
                         BigDecimal validatedUsd, BigDecimal awaitingUsd) {
        public BigDecimal usedUsd() {
            return validatedUsd.add(awaitingUsd);
        }

        public BigDecimal capUsd() {
            return baseUsd.multiply(BigDecimal.valueOf(capPercent)).divide(BigDecimal.valueOf(100));
        }
    }

    /**
     * @param aiUsageIds the rows the allocation will cover
     * @param replacedAllocationIds allocations this one replaces: not counted in the month's "used"
     */
    public Decision evaluate(String organizationId, String userId, LocalDate day, List<String> aiUsageIds,
                             List<AllocationSplit> splits, Collection<String> replacedAllocationIds) {
        List<AIUsage> usages = aiUsageIds == null || aiUsageIds.isEmpty() ? List.of()
                : aiUsageRepository.findByOrganizationIdAndIdIn(organizationId, aiUsageIds);
        Map<String, Optional<String>> projectOfRepository = new HashMap<>();
        List<UsageRow> rows = new ArrayList<>();
        Set<String> repoProjects = new LinkedHashSet<>();
        for (AIUsage u : usages) {
            String projectId = projectOfRepository.computeIfAbsent(String.valueOf(u.getRepository()),
                    r -> repositoryProjectPort.projectIdOf(organizationId, u.getRepository())).orElse(null);
            if (projectId != null) repoProjects.add(projectId);
            rows.add(new UsageRow(u.getRepository(), projectId, u.getCostUsd(),
                    u.getInputTokens() + u.getOutputTokens()));
        }
        boolean hasOut = splits.stream().anyMatch(AllocationSplit::isOutOfDelivery);
        if (repoProjects.isEmpty() && !hasOut) return Decision.free();

        Map<String, ItemRef> items = new HashMap<>();
        for (AllocationSplit s : splits) {
            if (s.isOutOfDelivery() || s.getWorkItemId() == null || items.containsKey(s.getWorkItemId())) continue;
            workItemRepository.findByIdAndOrganizationIdAndActiveTrue(s.getWorkItemId(), organizationId)
                    .ifPresent(w -> items.put(w.getId(),
                            new ItemRef(w.getProjectId(), AgentReviewReasonCalculator.displayId(w))));
        }
        Map<String, String> names = new HashMap<>();
        for (String projectId : repoProjects) {
            projectRepository.findActiveByIdAndOrganizationId(projectId, organizationId)
                    .ifPresent(p -> names.put(projectId, p.getName()));
        }

        CapContext cap = null;
        List<String> ownerProjects = List.of();
        if (hasOut) {
            Budget budget = budget(organizationId, userId, YearMonth.from(day), day, replacedAllocationIds);
            cap = new CapContext(budget.capPercent(), budget.baseUsd(), budget.baseMonth(), budget.usedUsd());
            ownerProjects = projectRepository.findAllActiveByOrganizationIdAndMemberId(organizationId, userId)
                    .stream().map(Project::getId).filter(Objects::nonNull).toList();
        }
        return AllocationApprovalPolicy.evaluate(
                new AllocationApprovalPolicy.Input(rows, splits, items, names, ownerProjects, cap));
    }

    /**
     * Budget of the month. Base: the person's AI cost in the previous month; zero falls back to the cost of
     * the month itself from the 1st to {@code upToDay}. Used: "Fora de entrega" cost (rows x percentage of
     * the bucket split) of the allocations of the month with status VALIDATED or AWAITING_APPROVAL, without
     * the {@code excludedAllocationIds}.
     */
    public Budget budget(String organizationId, String userId, YearMonth month, LocalDate upToDay,
                         Collection<String> excludedAllocationIds) {
        AllocationPolicy policy = organizationRepository.findById(organizationId)
                .map(o -> o.getAllocationPolicy()).orElse(null);
        if (policy == null) policy = AllocationPolicy.defaults();

        YearMonth previous = month.minusMonths(1);
        BigDecimal base = cost(organizationId, userId, previous.atDay(1), previous.atEndOfMonth());
        YearMonth baseMonth = previous;
        if (base.signum() == 0) {
            LocalDate end = upToDay == null || upToDay.isAfter(month.atEndOfMonth()) ? month.atEndOfMonth() : upToDay;
            base = cost(organizationId, userId, month.atDay(1), end);
            baseMonth = month;
        }

        Set<String> excluded = excludedAllocationIds == null ? Set.of() : new LinkedHashSet<>(excludedAllocationIds);
        List<AiUsageAllocation> inMonth = allocationRepository.findByOrgUserAndDayBetweenAndStatusIn(
                        organizationId, userId, month.atDay(1), month.atEndOfMonth(), BUDGET_STATUSES).stream()
                .filter(a -> a.getId() == null || !excluded.contains(a.getId()))
                .filter(a -> a.getSplits() != null && a.getSplits().stream().anyMatch(AllocationSplit::isOutOfDelivery))
                .toList();
        Set<String> usageIds = new LinkedHashSet<>();
        inMonth.forEach(a -> { if (a.getAiUsageIds() != null) usageIds.addAll(a.getAiUsageIds()); });
        Map<String, BigDecimal> costById = new HashMap<>();
        if (!usageIds.isEmpty()) {
            aiUsageRepository.findCostFieldsByOrganizationIdAndIdIn(organizationId, usageIds)
                    .forEach(u -> costById.put(u.getId(), u.getCostUsd() == null ? BigDecimal.ZERO : u.getCostUsd()));
        }
        BigDecimal validated = BigDecimal.ZERO;
        BigDecimal awaiting = BigDecimal.ZERO;
        for (AiUsageAllocation a : inMonth) {
            BigDecimal rowsCost = a.getAiUsageIds() == null ? BigDecimal.ZERO : new LinkedHashSet<>(a.getAiUsageIds())
                    .stream().map(id -> costById.getOrDefault(id, BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal pct = a.getSplits().stream().filter(AllocationSplit::isOutOfDelivery)
                    .map(AllocationSplit::getPercentage).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal out = rowsCost.multiply(pct);
            if (a.getStatus() == AiUsageAllocation.Status.VALIDATED) validated = validated.add(out);
            else awaiting = awaiting.add(out);
        }
        return new Budget(policy.outOfDeliveryCapPercent(), base, baseMonth.toString(), validated, awaiting);
    }

    private BigDecimal cost(String organizationId, String userId, LocalDate from, LocalDate to) {
        BigDecimal total = BigDecimal.ZERO;
        for (AIUsage u : aiUsageRepository.findByOrganizationIdAndUserIdAndDayBetween(organizationId, userId, from, to)) {
            if (u.getCostUsd() != null) total = total.add(u.getCostUsd());
        }
        return total;
    }
}
