package andromeda.sei.ms.feature.allocation.presentation.dto;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;

import java.math.BigDecimal;

/** One reason a day needs approval; the repository fields or the cap fields are filled, by {@code code}. */
public record ApprovalReasonResponse(String code, String repository, String projectId, String projectName,
                                     String workItemId, String displayId,
                                     Integer capPercent, BigDecimal baseUsd, String baseMonth,
                                     BigDecimal usedUsd, BigDecimal thisUsd, Integer afterPercent) {

    public static ApprovalReasonResponse from(AiUsageAllocation.ApprovalReason r) {
        return new ApprovalReasonResponse(r.code().name(), r.repository(), r.projectId(), r.projectName(),
                r.workItemId(), r.displayId(), r.capPercent(), r.baseUsd(), r.baseMonth(),
                r.usedUsd(), r.thisUsd(), r.afterPercent());
    }
}
