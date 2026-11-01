package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationSplitDocument {

    /** Written only when OUT_OF_DELIVERY; absent is read as ITEM. */
    private AllocationSplit.Kind kind;
    private AllocationSplit.Category category;
    private String note;

    public AllocationSplitDocument(String workItemId, String workItemTitle, BigDecimal percentage) {
        this.workItemId = workItemId;
        this.workItemTitle = workItemTitle;
        this.percentage = percentage;
    }
    private String workItemId;
    private String workItemTitle;
    private BigDecimal percentage;
}
