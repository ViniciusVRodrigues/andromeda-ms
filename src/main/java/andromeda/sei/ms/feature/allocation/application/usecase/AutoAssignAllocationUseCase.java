package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.SuggestionSource;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Suggests, for one (user, day), the work items that day's AI usage belongs to. The suggestion
 * comes from the items the person changed in Azure DevOps that day; with none, from the items
 * in progress in the sprint covering the day. A source with more than {@link #MAX_SUGGESTED_ITEMS}
 * items is no signal (a bulk edit in Azure, a crowded sprint) and is skipped: an even split over
 * hundreds of items gave each a fraction of a percent. The result is a {@code PENDING_VALIDATION}
 * allocation; a {@code VALIDATED} day is never touched.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAssignAllocationUseCase {

    /** Above this many items a day's source is treated as a bulk change, not as the day's work. */
    static final int MAX_SUGGESTED_ITEMS = 10;

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final WorkItemActivityRepository workItemActivityRepository;
    private final EffortBearingLevelResolver effortBearingLevelResolver;
    private final andromeda.sei.ms.feature.allocation.application.AllocationTypeEligibility typeEligibility;

    public void execute(String organizationId, String userId, LocalDate day) {
        execute(organizationId, userId, day, false);
    }

    /**
     * @param force recompute even when the pending suggestion already covers the same usage records
     *              (new Azure activity may have arrived); the suggestion is rewritten only if its
     *              items, percentages or source changed
     */
    public void execute(String organizationId, String userId, LocalDate day, boolean force) {
        List<AIUsage> unallocated = aiUsageRepository.findUnallocatedByOrganizationIdAndUserIdAndDay(
                organizationId, userId, day);
        List<String> unallocatedIds = unallocated.stream().map(AIUsage::getId).toList();

        if (unallocatedIds.isEmpty()) {
            log.debug("AutoAssign: no unallocated records for user={} day={} — skipping", userId, day);
            return;
        }

        List<AiUsageAllocation> existing = allocationRepository.findByOrgUserAndDay(organizationId, userId, day);
        if (existing.stream().anyMatch(a -> a.getStatus() == AiUsageAllocation.Status.VALIDATED)) {
            log.debug("AutoAssign: day={} of user={} is already validated — skipping", day, userId);
            return;
        }
        AiUsageAllocation pending = existing.stream()
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.PENDING_VALIDATION)
                .findFirst().orElse(null);

        boolean samePendingUsage = pending != null
                && Set.copyOf(unallocatedIds).equals(
                        pending.getAiUsageIds() == null ? Set.of() : Set.copyOf(pending.getAiUsageIds()));
        if (samePendingUsage && !force) {
            log.debug("AutoAssign: identical PENDING allocation exists for user={} day={} — skipping", userId, day);
            return;
        }

        Suggestion suggestion = suggest(organizationId, userId, day);

        if (pending != null) {
            if (samePendingUsage && suggestion != null && sameSuggestion(pending, suggestion)) {
                log.debug("AutoAssign: suggestion unchanged for user={} day={} — keeping", userId, day);
                return;
            }
            log.debug("AutoAssign: replacing PENDING allocation for user={} day={} — deleting id={}",
                    userId, day, pending.getId());
            allocationRepository.deleteById(pending.getId());
        }

        if (suggestion == null) {
            log.debug("AutoAssign: no items for user={} day={} — leaving unallocated", userId, day);
            return;
        }

        Instant now = Instant.now();
        AiUsageAllocation allocation = new AiUsageAllocation(
                null, organizationId, userId, day, unallocatedIds, suggestion.splits(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO,
                true, null, now, now, suggestion.source());
        AiUsageAllocation saved = allocationRepository.save(allocation);

        log.debug("AutoAssign: allocation created id={} for user={} day={} covering {} records with {} splits ({})",
                saved == null ? null : saved.getId(), userId, day, unallocatedIds.size(),
                suggestion.splits().size(), suggestion.source());
    }

    private Suggestion suggest(String organizationId, String userId, LocalDate day) {
        List<WorkItem> items = activityItems(organizationId, userId, day);
        SuggestionSource source = SuggestionSource.AZURE_ACTIVITY;
        if (!usable(items, source, userId, day)) {
            items = inProgressItems(organizationId, userId, day);
            source = SuggestionSource.IN_PROGRESS;
        }
        if (!usable(items, source, userId, day)) return null;
        return new Suggestion(weightedSplits(items, hoursByItem(organizationId, items)), source);
    }

    private static boolean usable(List<WorkItem> items, SuggestionSource source, String userId, LocalDate day) {
        if (items.size() <= MAX_SUGGESTED_ITEMS) return !items.isEmpty();
        log.debug("AutoAssign: {} {} items for user={} day={} exceed {} — not a suggestion",
                items.size(), source, userId, day, MAX_SUGGESTED_ITEMS);
        return false;
    }

    /** Distinct active items the user changed that day, filtered to the effort-bearing level. */
    private List<WorkItem> activityItems(String organizationId, String userId, LocalDate day) {
        Set<String> ids = new LinkedHashSet<>();
        workItemActivityRepository.findByOrganizationIdAndActorUserIdAndDay(organizationId, userId, day).stream()
                .filter(WorkItemActivity::isActive)
                .sorted(Comparator.comparing(WorkItemActivity::getChangedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(a -> ids.add(a.getWorkItemId()));
        List<WorkItem> items = new ArrayList<>();
        for (String id : ids) {
            workItemRepository.findByIdAndOrganizationIdAndActiveTrue(id, organizationId).ifPresent(items::add);
        }
        return units(organizationId, items);
    }

    private List<WorkItem> inProgressItems(String organizationId, String userId, LocalDate day) {
        List<Sprint> sprints = new ArrayList<>(sprintRepository.findByOrganizationIdAndDay(organizationId, day));
        if (sprints.isEmpty()) {
            log.debug("AutoAssign: no sprint covers day={} — falling back to closest sprint", day);
            sprintRepository.findClosestSprintByOrganizationIdAndDay(organizationId, day).ifPresent(sprints::add);
        }
        if (sprints.isEmpty()) {
            log.debug("AutoAssign: no sprints at all for org={}", organizationId);
            return List.of();
        }
        return sprints.stream()
                .flatMap(sprint -> units(organizationId, workItemRepository
                        .findByOrganizationIdAndSprintIdAndAssigneeUserIdAndResolvedStatus(
                                organizationId, sprint.getId(), userId, WorkItem.ResolvedStatus.IN_PROGRESS)
                        .stream().filter(WorkItem::isActive).toList()).stream())
                // an item in two sprints of the day must not take two shares of the split
                .filter(distinctIds())
                .toList();
    }

    /**
     * Allocation has to target the same level the team books work at, or teams that track effort on
     * Tasks would never get an automatic suggestion.
     */
    private List<WorkItem> units(String organizationId, List<WorkItem> items) {
        if (items.isEmpty()) return List.of();
        var level = effortBearingLevelResolver.resolve(null, items);
        return typeEligibility.eligibleUnits(organizationId, items,
                units -> effortBearingLevelResolver.deliverableUnits(level, units));
    }

    private static java.util.function.Predicate<WorkItem> distinctIds() {
        Set<String> seen = new java.util.HashSet<>();
        return wi -> seen.add(wi.getId());
    }

    private static final int MAX_DESCENDANT_DEPTH = 4;

    /**
     * Hours of each item (dashboards-numeros D4): a Task's own effort, or for a story-tier item the sum of the
     * effort of its Task descendants (children loaded by parent, always inside the organization). Items without
     * a value are absent from the map.
     */
    private Map<String, Double> hoursByItem(String organizationId, List<WorkItem> items) {
        Map<String, Double> hours = new HashMap<>();
        Map<String, List<WorkItem>> storiesByProject = new HashMap<>();
        for (WorkItem item : items) {
            if (effortBearingLevelResolver.isTaskTier(item.getResolvedType())) {
                if (item.getEffort() != null) hours.put(item.getId(), item.getEffort());
            } else if (item.getExternalId() != null && item.getProjectId() != null) {
                storiesByProject.computeIfAbsent(item.getProjectId(), k -> new ArrayList<>()).add(item);
            }
        }
        storiesByProject.forEach((projectId, stories) -> {
            Map<String, String> rootOf = new HashMap<>();
            for (WorkItem story : stories) rootOf.put(story.getExternalId(), story.getId());
            Set<String> seen = new HashSet<>(rootOf.keySet());
            List<String> frontier = new ArrayList<>(rootOf.keySet());
            for (int depth = 0; depth < MAX_DESCENDANT_DEPTH && !frontier.isEmpty(); depth++) {
                List<String> next = new ArrayList<>();
                for (WorkItem child : workItemRepository
                        .findByOrganizationIdAndProjectIdAndParentExternalIdIn(organizationId, projectId, frontier)) {
                    if (!child.isActive() || child.getExternalId() == null || !seen.add(child.getExternalId())) continue;
                    String root = rootOf.get(child.getParentExternalId());
                    if (root == null) continue;
                    rootOf.put(child.getExternalId(), root);
                    next.add(child.getExternalId());
                    if (effortBearingLevelResolver.isTaskTier(child.getResolvedType()) && child.getEffort() != null) {
                        hours.merge(root, child.getEffort(), Double::sum);
                    }
                }
                frontier = next;
            }
        });
        return hours;
    }

    /**
     * Split weighted by hours (dashboards-numeros D4): weight = max(hours, 1), 1 without hours; the share is
     * weight / sum of weights, scale 4 HALF_UP, and the last split closes the sum at exactly 1.0000. Equal
     * weights give the plain 1/N.
     */
    static List<AllocationSplit> weightedSplits(List<WorkItem> items, Map<String, Double> hours) {
        double[] weights = new double[items.size()];
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            Double h = hours.get(items.get(i).getId());
            weights[i] = h == null ? 1d : Math.max(h, 1d);
            total = total.add(BigDecimal.valueOf(weights[i]));
        }
        List<AllocationSplit> splits = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            WorkItem item = items.get(i);
            BigDecimal pct = i == items.size() - 1
                    ? BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP).subtract(sum)
                    : BigDecimal.valueOf(weights[i]).divide(total, 4, RoundingMode.HALF_UP);
            sum = sum.add(pct);
            splits.add(new AllocationSplit(item.getId(), item.getTitle(), pct));
        }
        return splits;
    }

    private static boolean sameSuggestion(AiUsageAllocation pending, Suggestion suggestion) {
        if (pending.suggestionSourceOrDefault() != suggestion.source()) return false;
        List<AllocationSplit> old = pending.getSplits() == null ? List.of() : pending.getSplits();
        if (old.size() != suggestion.splits().size()) return false;
        for (int i = 0; i < old.size(); i++) {
            AllocationSplit a = old.get(i);
            AllocationSplit b = suggestion.splits().get(i);
            if (!Objects.equals(a.getWorkItemId(), b.getWorkItemId())) return false;
            if (a.getPercentage() == null || a.getPercentage().compareTo(b.getPercentage()) != 0) return false;
        }
        return true;
    }

    private record Suggestion(List<AllocationSplit> splits, SuggestionSource source) {}
}
