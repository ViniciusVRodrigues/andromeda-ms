package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.application.AllocationApproverAccess;
import andromeda.sei.ms.feature.allocation.application.dto.ApprovalQueueItemOutput;
import andromeda.sei.ms.feature.allocation.application.dto.SplitOutput;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.user.domain.User;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.shared.domain.security.UserPermissionSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The days waiting for the caller's approval, oldest request first, never the caller's own. */
@Service
@RequiredArgsConstructor
public class GetApprovalQueueUseCase {

    private final AiUsageAllocationRepository allocationRepository;
    private final AIUsageRepository aiUsageRepository;
    private final UserRepository userRepository;
    private final AllocationApproverAccess approverAccess;

    public List<ApprovalQueueItemOutput> execute(UserPermissionSnapshot caller) {
        String organizationId = caller.organizationId();
        Set<String> callerProjects = approverAccess.approverProjects(caller);
        List<AiUsageAllocation> visible = allocationRepository.findAwaitingApprovalByOrganizationId(organizationId)
                .stream()
                .filter(a -> a.getStatus() == AiUsageAllocation.Status.AWAITING_APPROVAL && a.getApproval() != null)
                .filter(a -> a.getUserId() != null && !a.getUserId().equals(caller.userId()))
                .filter(a -> approverAccess.canDecide(caller, callerProjects, a))
                .sorted(Comparator.comparing(GetApprovalQueueUseCase::requestedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        if (visible.isEmpty()) return List.of();

        Set<String> usageIds = new LinkedHashSet<>();
        visible.forEach(a -> { if (a.getAiUsageIds() != null) usageIds.addAll(a.getAiUsageIds()); });
        Map<String, AIUsage> usageById = new HashMap<>();
        if (!usageIds.isEmpty()) {
            aiUsageRepository.findByOrganizationIdAndIdIn(organizationId, usageIds)
                    .forEach(u -> usageById.put(u.getId(), u));
        }
        Map<String, String> userNames = new HashMap<>();

        List<ApprovalQueueItemOutput> items = new ArrayList<>();
        for (AiUsageAllocation a : visible) {
            BigDecimal cost = BigDecimal.ZERO;
            long tokens = 0;
            Set<ApprovalQueueItemOutput.Repository> repositories = new LinkedHashSet<>();
            Set<String> ids = new LinkedHashSet<>(a.getAiUsageIds() == null ? List.<String>of() : a.getAiUsageIds());
            for (String usageId : ids) {
                AIUsage u = usageById.get(usageId);
                if (u == null) continue;
                if (u.getCostUsd() != null) cost = cost.add(u.getCostUsd());
                tokens += u.getInputTokens() + u.getOutputTokens();
                if (u.getRepository() != null && !u.getRepository().isBlank()) {
                    repositories.add(new ApprovalQueueItemOutput.Repository(u.getRepository(), blankToNull(u.getBranch())));
                }
            }
            String userName = userNames.computeIfAbsent(a.getUserId(), id -> userRepository
                    .findByIdAndOrganizationId(id, organizationId).map(User::getName).orElse(""));
            List<SplitOutput> splits = a.getSplits() == null ? List.of() : a.getSplits().stream()
                    .map(s -> new SplitOutput(s.getKind(), s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage(),
                            s.getCategory(), s.getNote()))
                    .toList();
            items.add(new ApprovalQueueItemOutput(a.getId(), a.getUserId(), userName.isEmpty() ? null : userName,
                    a.getDay(), cost, tokens, splits, a.getApproval().reasons(), a.getApproval().requestedAt(),
                    List.copyOf(repositories)));
        }
        return items;
    }

    private static Instant requestedAt(AiUsageAllocation a) {
        return a.getApproval() == null ? null : a.getApproval().requestedAt();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
