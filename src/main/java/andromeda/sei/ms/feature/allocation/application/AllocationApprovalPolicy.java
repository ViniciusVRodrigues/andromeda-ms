package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReason;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation.ApprovalReasonCode;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * When a day's allocation needs the approval of someone else (uso-ia-fora-de-entrega, design 2.1). Pure:
 * the use cases gather the data ({@link AllocationApprovalService}) and every path that would validate an
 * allocation asks this one place.
 *
 * <ul>
 *   <li>{@code REPO_OTHER_PROJECT}: the rows come from a repository bound to project X and part of the
 *       split goes outside X, beyond what the rows without a project repository can cover.</li>
 *   <li>{@code OUT_OF_DELIVERY_OVER_CAP}: the "Fora de entrega" cost of the person in the month, with this
 *       allocation, is over {@code cap%} of the base.</li>
 * </ul>
 */
public final class AllocationApprovalPolicy {

    /** Tolerance of the share comparison: a foreign share within 0.01 of the free share is still free. */
    public static final BigDecimal SHARE_TOLERANCE = new BigDecimal("0.01");

    private AllocationApprovalPolicy() {}

    /** One usage row of the allocation; {@code repositoryProjectId} is null when the repository has no single project. */
    public record UsageRow(String repository, String repositoryProjectId, BigDecimal costUsd, long tokens) {}

    /** The project and display id of the work item of an ITEM split. */
    public record ItemRef(String projectId, String displayId) {}

    /** Cap data: used = "Fora de entrega" cost already in the month (validated or awaiting, replaced ones excluded). */
    public record CapContext(int capPercent, BigDecimal baseUsd, String baseMonth, BigDecimal usedUsd) {}

    public record Input(List<UsageRow> rows, List<AllocationSplit> splits, Map<String, ItemRef> items,
                        Map<String, String> projectNames, List<String> ownerProjectIds, CapContext cap) {}

    public record Decision(List<ApprovalReason> reasons, List<String> approverProjectIds) {
        public Decision {
            reasons = reasons == null ? List.of() : reasons;
            approverProjectIds = approverProjectIds == null ? List.of() : approverProjectIds;
        }

        public static Decision free() {
            return new Decision(List.of(), List.of());
        }

        public boolean needsApproval() {
            return !reasons.isEmpty();
        }

        /**
         * Puts the allocation where the decision says: {@code VALIDATED} when free, otherwise
         * {@code AWAITING_APPROVAL} with a fresh request (reasons, approvers, {@code requestedAt}).
         */
        public void applyTo(AiUsageAllocation allocation, Instant now) {
            if (!needsApproval()) {
                allocation.setStatus(AiUsageAllocation.Status.VALIDATED);
                return;
            }
            allocation.setStatus(AiUsageAllocation.Status.AWAITING_APPROVAL);
            allocation.setApproval(new AiUsageAllocation.Approval(reasons, approverProjectIds, now,
                    null, null, null, null));
        }
    }

    public static Decision evaluate(Input in) {
        List<ApprovalReason> reasons = new ArrayList<>();
        Set<String> approvers = new LinkedHashSet<>();
        repositoryReasons(in, reasons, approvers);
        capReasons(in, reasons, approvers);
        return new Decision(reasons, new ArrayList<>(approvers));
    }

    // ---- REPO_OTHER_PROJECT -------------------------------------------------------------------

    private static void repositoryReasons(Input in, List<ApprovalReason> reasons, Set<String> approvers) {
        List<UsageRow> rows = in.rows() == null ? List.of() : in.rows();
        if (rows.isEmpty()) return;
        BigDecimal totalCost = rows.stream().map(r -> nz(r.costUsd())).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalTokens = rows.stream().mapToLong(UsageRow::tokens).sum();

        Map<String, BigDecimal> projectWeight = new LinkedHashMap<>();
        Map<String, String> projectRepository = new LinkedHashMap<>();
        BigDecimal freeWeight = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (UsageRow r : rows) {
            BigDecimal w = totalCost.signum() > 0 ? nz(r.costUsd())
                    : totalTokens > 0 ? BigDecimal.valueOf(r.tokens()) : BigDecimal.ONE;
            total = total.add(w);
            if (r.repositoryProjectId() == null) {
                freeWeight = freeWeight.add(w);
            } else {
                projectWeight.merge(r.repositoryProjectId(), w, BigDecimal::add);
                projectRepository.putIfAbsent(r.repositoryProjectId(), r.repository());
            }
        }
        if (projectWeight.isEmpty() || total.signum() == 0) return;

        BigDecimal freeShare = freeWeight.divide(total, 6, RoundingMode.HALF_UP);
        List<AllocationSplit> foreign = new ArrayList<>();
        BigDecimal foreignShare = BigDecimal.ZERO;
        for (AllocationSplit s : in.splits()) {
            if (!isForeign(s, in, projectWeight.keySet())) continue;
            foreign.add(s);
            foreignShare = foreignShare.add(nz(s.getPercentage()));
        }
        if (foreignShare.compareTo(freeShare.add(SHARE_TOLERANCE)) <= 0) return;

        String mainProject = projectWeight.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
        String projectName = in.projectNames() == null ? null : in.projectNames().get(mainProject);
        for (AllocationSplit s : foreign) {
            ItemRef item = s.isOutOfDelivery() || s.getWorkItemId() == null || in.items() == null
                    ? null : in.items().get(s.getWorkItemId());
            reasons.add(new ApprovalReason(ApprovalReasonCode.REPO_OTHER_PROJECT,
                    projectRepository.get(mainProject), mainProject, projectName,
                    s.isOutOfDelivery() ? null : s.getWorkItemId(), item == null ? null : item.displayId(),
                    null, null, null, null, null, null));
        }
        approvers.addAll(projectWeight.keySet());
    }

    private static boolean isForeign(AllocationSplit s, Input in, Set<String> repoProjects) {
        if (s.isOutOfDelivery()) return true;
        ItemRef item = in.items() == null || s.getWorkItemId() == null ? null : in.items().get(s.getWorkItemId());
        return item == null || item.projectId() == null || !repoProjects.contains(item.projectId());
    }

    // ---- OUT_OF_DELIVERY_OVER_CAP -------------------------------------------------------------

    private static void capReasons(Input in, List<ApprovalReason> reasons, Set<String> approvers) {
        AllocationSplit out = in.splits().stream().filter(AllocationSplit::isOutOfDelivery).findFirst().orElse(null);
        if (out == null || in.cap() == null) return;
        CapContext cap = in.cap();

        BigDecimal rowsCost = in.rows() == null ? BigDecimal.ZERO : in.rows().stream()
                .map(r -> nz(r.costUsd())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal thisUsd = rowsCost.multiply(nz(out.getPercentage()));
        BigDecimal used = nz(cap.usedUsd());
        BigDecimal after = used.add(thisUsd);
        BigDecimal base = nz(cap.baseUsd());
        BigDecimal limit = base.multiply(BigDecimal.valueOf(cap.capPercent())).divide(BigDecimal.valueOf(100));

        boolean over = cap.capPercent() == 0 || after.compareTo(limit) > 0;
        if (!over) return;
        Integer afterPercent = base.signum() > 0
                ? after.multiply(BigDecimal.valueOf(100)).divide(base, 0, RoundingMode.CEILING).intValue() : null;
        reasons.add(new ApprovalReason(ApprovalReasonCode.OUT_OF_DELIVERY_OVER_CAP,
                null, null, null, null, null,
                cap.capPercent(), money(base), cap.baseMonth(), money(used), money(thisUsd), afterPercent));
        if (in.ownerProjectIds() != null) approvers.addAll(in.ownerProjectIds());
    }

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
