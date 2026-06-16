package andromeda.sei.ms.feature.allocation.domain;

import java.math.BigDecimal;

public class AllocationSplit {

    private final String workItemId;
    private final String workItemTitle;
    private final BigDecimal percentage;

    public AllocationSplit(String workItemId, String workItemTitle, BigDecimal percentage) {
        this.workItemId = workItemId;
        this.workItemTitle = workItemTitle;
        this.percentage = percentage;
    }

    public String getWorkItemId() { return workItemId; }
    public String getWorkItemTitle() { return workItemTitle; }
    public BigDecimal getPercentage() { return percentage; }
}
