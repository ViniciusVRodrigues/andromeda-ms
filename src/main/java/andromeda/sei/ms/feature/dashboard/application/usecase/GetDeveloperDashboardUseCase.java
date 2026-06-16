package andromeda.sei.ms.feature.dashboard.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.aiusage.domain.DailyAiUsage;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.dashboard.application.dto.AiSprintSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.BurndownPoint;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetDeveloperDashboardUseCase {

    private final SprintRepository sprintRepository;
    private final WorkItemRepository workItemRepository;
    private final AIUsageRepository aiUsageRepository;
    private final AiUsageAllocationRepository allocationRepository;

    public DeveloperDashboardOutput execute(String orgId, String userId, String projectId, String sprintId,
                                            LocalDate startDate, LocalDate endDate, String model) {
        Optional<Sprint> sprintOpt = resolveCurrentSprint(orgId, projectId, sprintId);

        List<WorkItem> currentSprintItems = sprintOpt
                .map(sprint -> projectId != null
                        ? workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintIdAndProjectId(
                                orgId, userId, sprint.getId(), projectId)
                        : workItemRepository.findByOrganizationIdAndAssigneeUserIdAndSprintId(
                                orgId, userId, sprint.getId()))
                .orElse(List.of());

        List<WorkItem> storyItems = currentSprintItems.stream()
                .filter(wi -> WorkItem.ResolvedType.STORY.equals(wi.getResolvedType()))
                .toList();

        List<WorkItem> doneItems = storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                .toList();

        List<WorkItem> inProgressItems = storyItems.stream()
                .filter(wi -> WorkItem.ResolvedStatus.IN_PROGRESS.equals(wi.getResolvedStatus()))
                .toList();

        Double avgCycleTimeHours = average(doneItems.stream()
                .map(WorkItem::getCycleTimeHours)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        Double avgLeadTimeHours = average(doneItems.stream()
                .map(WorkItem::getLeadTimeHours)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        double[] spValues = doneItems.stream()
                .map(WorkItem::getStoryPoints)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray();
        Double storyPointsDelivered = spValues.length == 0 ? null : Arrays.stream(spValues).sum();

        LocalDate from = startDate != null ? startDate : LocalDate.now().minusDays(30);
        LocalDate to = endDate != null ? endDate : LocalDate.now();

        List<DailyAiUsage> tokensByDay = aiUsageRepository.findDailyUsageByUserIdAndDateRange(
                orgId, userId, from, to, model);

        BigDecimal sprintCostUsd = null;
        LocalDate sprintStart = sprintOpt.map(s -> toLocalDate(s.getStartDate())).orElse(null);
        LocalDate sprintEnd = sprintOpt.map(s -> toLocalDate(s.getEndDate())).orElse(null);

        if (sprintStart != null && sprintEnd != null) {
            List<DailyAiUsage> sprintUsage = aiUsageRepository.findDailyUsageByUserIdAndDateRange(
                    orgId, userId, sprintStart, sprintEnd, model);
            sprintCostUsd = sprintUsage.stream()
                    .map(DailyAiUsage::costUsd)
                    .filter(v -> v != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Double avgTokensPerSp = average(storyItems.stream()
                .map(WorkItem::getTokensPerSp)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .toArray());

        // Burndown series
        List<BurndownPoint> burndownSeries = computeBurndown(storyItems, sprintStart, sprintEnd);

        // AI sprint summary
        AiSprintSummary aiSprintSummary = computeAiSprintSummary(orgId, userId, sprintStart, sprintEnd);

        Map<String, BigDecimal> aiCostByWorkItemId = computeAiCostByWorkItem(orgId, userId, storyItems, sprintStart, sprintEnd);

        return new DeveloperDashboardOutput(
                storyItems,
                avgCycleTimeHours,
                avgLeadTimeHours,
                storyPointsDelivered,
                tokensByDay,
                sprintCostUsd,
                avgTokensPerSp,
                null,
                null,
                inProgressItems,
                storyItems.size(),
                burndownSeries,
                aiSprintSummary,
                aiCostByWorkItemId
        );
    }

    private Map<String, BigDecimal> computeAiCostByWorkItem(String orgId, String userId,
            List<WorkItem> storyItems, LocalDate sprintStart, LocalDate sprintEnd) {
        if (storyItems.isEmpty()) return Map.of();

        Map<LocalDate, BigDecimal> costByDay = Map.of();
        if (sprintStart != null && sprintEnd != null) {
            List<DailyAiUsage> dailyUsage = aiUsageRepository.findDailyUsageByUserIdAndDateRange(
                    orgId, userId, sprintStart, sprintEnd, null);
            costByDay = dailyUsage.stream()
                    .filter(d -> d.costUsd() != null && d.day() != null)
                    .collect(Collectors.toMap(
                            DailyAiUsage::day,
                            DailyAiUsage::costUsd,
                            BigDecimal::add));
        }

        Map<String, BigDecimal> costByWorkItem = new HashMap<>();
        for (WorkItem story : storyItems) {
            List<AiUsageAllocation> allocations = allocationRepository
                    .findValidatedByOrgUserAndWorkItemId(orgId, userId, story.getId());
            BigDecimal totalCost = BigDecimal.ZERO;
            for (AiUsageAllocation alloc : allocations) {
                BigDecimal dayCost = costByDay.getOrDefault(alloc.getDay(), BigDecimal.ZERO);
                if (alloc.getSplits() == null) continue;
                for (AllocationSplit split : alloc.getSplits()) {
                    if (!story.getId().equals(split.getWorkItemId())) continue;
                    if (split.getPercentage() == null) continue;
                    totalCost = totalCost.add(dayCost.multiply(split.getPercentage()));
                }
            }
            if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
                costByWorkItem.put(story.getId(), totalCost);
            }
        }
        return costByWorkItem;
    }

    private List<BurndownPoint> computeBurndown(List<WorkItem> sprintItems,
                                                 LocalDate sprintStart, LocalDate sprintEnd) {
        if (sprintStart == null || sprintEnd == null) {
            return List.of();
        }

        double totalSp = sprintItems.stream()
                .map(WorkItem::getStoryPoints)
                .filter(sp -> sp != null)
                .mapToDouble(Double::doubleValue)
                .sum();

        LocalDate today = LocalDate.now();
        LocalDate seriesEnd = sprintEnd.isBefore(today) ? sprintEnd : today;

        long totalSprintDays = ChronoUnit.DAYS.between(sprintStart, sprintEnd);
        if (totalSprintDays <= 0) {
            return List.of();
        }

        List<BurndownPoint> series = new ArrayList<>();
        LocalDate cursor = sprintStart;

        while (!cursor.isAfter(seriesEnd)) {
            final LocalDate currentDay = cursor;
            double completedSp = sprintItems.stream()
                    .filter(wi -> WorkItem.ResolvedStatus.DONE.equals(wi.getResolvedStatus()))
                    .filter(wi -> wi.getCompletedAt() != null)
                    .filter(wi -> !toLocalDate(wi.getCompletedAt()).isAfter(currentDay))
                    .map(WorkItem::getStoryPoints)
                    .filter(sp -> sp != null)
                    .mapToDouble(Double::doubleValue)
                    .sum();

            double remainingSp = totalSp - completedSp;

            long daysElapsed = ChronoUnit.DAYS.between(sprintStart, currentDay);
            double idealSp = totalSp * (1.0 - (double) daysElapsed / totalSprintDays);

            series.add(new BurndownPoint(currentDay, remainingSp, idealSp));
            cursor = cursor.plusDays(1);
        }

        return series;
    }

    private AiSprintSummary computeAiSprintSummary(String orgId, String userId,
                                                     LocalDate sprintStart, LocalDate sprintEnd) {
        if (sprintStart == null || sprintEnd == null) {
            return null;
        }

        List<AIUsage> sprintUsages = aiUsageRepository
                .findByOrganizationIdAndUserIdAndDayBetween(orgId, userId, sprintStart, sprintEnd);

        if (sprintUsages.isEmpty()) {
            return null;
        }

        long totalTokens = sprintUsages.stream()
                .mapToLong(u -> u.getInputTokens() + u.getOutputTokens())
                .sum();

        BigDecimal totalCostUsd = sprintUsages.stream()
                .map(AIUsage::getCostUsd)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<LocalDate> unallocatedDays = sprintUsages.stream()
                .filter(u -> u.getAllocationId() == null)
                .map(AIUsage::getDay)
                .collect(Collectors.toSet());

        return new AiSprintSummary(totalTokens, totalCostUsd, unallocatedDays.size());
    }

    private Optional<Sprint> resolveCurrentSprint(String orgId, String projectId, String sprintId) {
        if (sprintId != null) {
            return sprintRepository.findById(sprintId);
        }
        if (projectId != null) {
            return sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(orgId, projectId);
        }
        return sprintRepository.findCurrentSprintsByOrganizationId(orgId).stream().findFirst();
    }

    private Double average(double[] values) {
        if (values.length == 0) return null;
        double sum = 0;
        for (double v : values) sum += v;
        return sum / values.length;
    }

    private LocalDate toLocalDate(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}