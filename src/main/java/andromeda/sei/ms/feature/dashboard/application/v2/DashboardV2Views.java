package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.AllocationSummary;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.Assignee;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.CommitDay;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemPullRequest;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.ItemView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.PullRequestView;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.SprintMeta;
import andromeda.sei.ms.feature.dashboard.application.dto.v2.DashboardV2Parts.WorkItemRef;
import andromeda.sei.ms.feature.dashboard.application.v2.DashboardPeopleDirectory.PersonInfo;
import andromeda.sei.ms.feature.vcs.domain.PullRequestStatus;
import andromeda.sei.ms.feature.vcs.domain.StalePullRequestPolicy;
import andromeda.sei.ms.feature.vcs.domain.VcsCommit;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.workitem.domain.Sprint;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.domain.metrics.Ratios;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Pure mappings from stored entities to the v2 payload shapes, shared by the individual and team
 * dashboards so a pull request or a work item reads the same in both.
 */
public final class DashboardV2Views {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");
    private static final double HOURS_PER_DAY = 24.0;
    private static final String SPRINT_PREFIX = "Sprint ";
    private static final String SHORT_SPRINT_PREFIX = "S";
    private static final String CURRENT_MARK = "*";
    private static final String LOGIN_PREFIX = "@";
    private static final String KEY_SEPARATOR = "|";

    private DashboardV2Views() {}

    public static String label(LocalDate day) {
        return day.format(DAY_LABEL);
    }

    /** Calendar days {@code [first, last]}; empty when {@code last} is before {@code first}. */
    public static List<LocalDate> days(LocalDate first, LocalDate last) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) days.add(day);
        return days;
    }

    public static SprintMeta sprintMeta(Sprint sprint, SprintWindow window, LocalDate today) {
        LocalDate lastElapsed = window.lastElapsedDay(today);
        return new SprintMeta(sprint.getId(), sprint.getName(), sprint.getTeamName(),
                window.startDay().toString(), window.endDay().toString(),
                sprint.getTimeFrame() != null ? sprint.getTimeFrame().name() : null,
                workingDays(window.startDay(), lastElapsed), workingDays(window.startDay(), window.endDay()));
    }

    /** {@code S12} for {@code Sprint 12}, with {@code *} on the current sprint. */
    public static String historyLabel(Sprint sprint) {
        String name = sprint.getName() == null ? "" : sprint.getName().replace(SPRINT_PREFIX, SHORT_SPRINT_PREFIX);
        return isCurrent(sprint) ? name + CURRENT_MARK : name;
    }

    public static boolean isCurrent(Sprint sprint) {
        return sprint.getTimeFrame() == Sprint.TimeFrame.CURRENT;
    }

    public static ItemView itemView(WorkItem item, Map<String, PersonInfo> people, BigDecimal aiCostUsd,
                                    VcsPullRequest linkedPr, Map<String, String> repositoryNames, Instant now) {
        return new ItemView(item.getId(), item.getExternalId(), item.getTitle(),
                enumName(item.getResolvedType()), enumName(item.getResolvedStatus()), item.getRawStatus(),
                item.getPriority(), item.getEffort(), assignee(item, people), item.getStartedAt(),
                item.getCompletedAt(), item.getCycleTimeHours(), item.getLeadTimeHours(),
                daysInProgress(item, now), aiCostUsd, item.getTokensPerSp(),
                linkedPr == null ? null : new ItemPullRequest(linkedPr.getNumber(),
                        repositoryNames.get(linkedPr.getRepositoryId()), enumName(linkedPr.getStatus()),
                        StalePullRequestPolicy.isStale(linkedPr, now)));
    }

    /** Days since the item started, only while it is in progress. */
    public static Double daysInProgress(WorkItem item, Instant now) {
        if (item.getResolvedStatus() != WorkItem.ResolvedStatus.IN_PROGRESS || item.getStartedAt() == null) return null;
        return Ratios.hours(item.getStartedAt(), now) / HOURS_PER_DAY;
    }

    public static PullRequestView pullRequestView(VcsPullRequest pr, Map<String, PersonInfo> people,
                                                  Map<String, String> repositoryNames,
                                                  Map<String, String> titleByExternalId, Instant now) {
        PersonInfo author = pr.getAuthorUserId() == null ? null : people.get(pr.getAuthorUserId());
        String linked = pr.getLinkedWorkItemExternalId();
        return new PullRequestView(pr.getId(), pr.getNumber(), pr.getTitle(), repositoryNames.get(pr.getRepositoryId()),
                enumName(pr.getProvider()), enumName(pr.getStatus()),
                author != null ? author.name() : LOGIN_PREFIX + pr.getAuthorLogin(), author != null,
                pr.isDraft(), pr.getBaseBranch(), pr.getLabels() == null ? List.of() : pr.getLabels(),
                pr.getCreatedAt(), pr.getMergedAt(),
                pr.getStatus() == PullRequestStatus.OPEN && pr.getCreatedAt() != null
                        ? Ratios.hours(pr.getCreatedAt(), now) : null,
                pr.getTimeToFirstReviewHours(), pr.getCycleTimeHours(), StalePullRequestPolicy.isStale(pr, now),
                linked == null ? null : new WorkItemRef(linked, titleByExternalId.get(linked)));
    }

    /** The most recent pull request linked to each work item external id. */
    public static Map<String, VcsPullRequest> latestPullRequestByWorkItem(Collection<VcsPullRequest> prs) {
        Map<String, VcsPullRequest> byExternalId = new HashMap<>();
        prs.stream().filter(pr -> pr.getLinkedWorkItemExternalId() != null)
                .sorted(Comparator.comparing(VcsPullRequest::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(pr -> byExternalId.put(pr.getLinkedWorkItemExternalId(), pr));
        return byExternalId;
    }

    public static Map<String, String> titlesByExternalId(Collection<WorkItem> items) {
        return items.stream().filter(i -> i.getExternalId() != null && i.getTitle() != null)
                .collect(Collectors.toMap(WorkItem::getExternalId, WorkItem::getTitle, (a, b) -> a));
    }

    /** Commits per calendar day of {@code [first, last]} among those matching {@code filter}. */
    public static List<CommitDay> commitsByDay(List<VcsCommit> commits, Predicate<VcsCommit> filter,
                                               LocalDate first, LocalDate last) {
        Map<LocalDate, Long> byDay = commits.stream().filter(filter).filter(c -> c.getAuthoredAt() != null)
                .collect(Collectors.groupingBy(c -> SprintWindow.toDay(c.getAuthoredAt()), Collectors.counting()));
        return days(first, last).stream()
                .map(day -> new CommitDay(day.toString(), label(day), byDay.getOrDefault(day, 0L).intValue()))
                .toList();
    }

    /**
     * Distinct (person, day) pairs with usage, by allocation situation: validated when a validated
     * allocation covers that person and day; pending when a record is claimed by an allocation not
     * validated yet; unallocated otherwise.
     *
     * @param validatedPersonDays keys from {@link #personDay}
     */
    public static AllocationSummary allocationSummary(List<AIUsage> records, Set<String> validatedPersonDays) {
        Map<String, Boolean> claimedByPersonDay = new HashMap<>();
        for (AIUsage usage : records) {
            if (usage.getUserId() == null || usage.getDay() == null) continue;
            claimedByPersonDay.merge(personDay(usage.getUserId(), usage.getDay()), usage.getAllocationId() != null,
                    Boolean::logicalOr);
        }
        int validated = 0;
        int pending = 0;
        int unallocated = 0;
        for (Map.Entry<String, Boolean> entry : claimedByPersonDay.entrySet()) {
            if (validatedPersonDays.contains(entry.getKey())) validated++;
            else if (entry.getValue()) pending++;
            else unallocated++;
        }
        return new AllocationSummary(validated, pending, unallocated);
    }

    public static String personDay(String userId, LocalDate day) {
        return userId + KEY_SEPARATOR + day;
    }

    /**
     * Delivered effort for display: {@code 0} when effort was planned but nothing estimated is done
     * yet (a real zero), {@code null} only when nothing carries effort at all.
     */
    public static Double deliveredOf(Double planned, Double delivered) {
        return delivered == null && planned != null ? Double.valueOf(0d) : delivered;
    }

    /** Sum of the non-null values, {@code null} when every value is null or the list is empty. */
    public static BigDecimal sumOrNull(Collection<BigDecimal> values) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        return present.isEmpty() ? null : present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Average of the non-null values, {@code null} without any. */
    public static Double averageOrNull(Collection<Double> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().stream()
                .boxed().findFirst().orElse(null);
    }

    public static int workingDays(LocalDate first, LocalDate last) {
        return (int) days(first, last).stream().filter(DashboardV2Views::isWorkingDay).count();
    }

    private static boolean isWorkingDay(LocalDate day) {
        return day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private static Assignee assignee(WorkItem item, Map<String, PersonInfo> people) {
        if (item.getAssigneeUserId() == null) return null;
        PersonInfo person = people.get(item.getAssigneeUserId());
        return new Assignee(item.getAssigneeUserId(), person != null ? person.name() : item.getAssigneeUserId());
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
