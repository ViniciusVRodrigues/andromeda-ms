package andromeda.sei.ms.feature.allocation.domain;

import java.math.BigDecimal;

public class AllocationSplit {

    /** A split is either a work item or the "Fora de entrega" bucket (uso-ia-fora-de-entrega). */
    public enum Kind { ITEM, OUT_OF_DELIVERY }

    public enum Category { STUDY, SUPPORT, DOCS_MEETING, OTHER }

    private final Kind kind;
    private final String workItemId;
    private final String workItemTitle;
    private final BigDecimal percentage;
    private final Category category;
    private final String note;

    /** A work item split ({@link Kind#ITEM}). */
    public AllocationSplit(String workItemId, String workItemTitle, BigDecimal percentage) {
        this(Kind.ITEM, workItemId, workItemTitle, percentage, null, null);
    }

    /** {@code kind} null is read as {@link Kind#ITEM} (stored data and old clients). */
    public AllocationSplit(Kind kind, String workItemId, String workItemTitle, BigDecimal percentage,
                           Category category, String note) {
        this.kind = kind == null ? Kind.ITEM : kind;
        this.workItemId = workItemId;
        this.workItemTitle = workItemTitle;
        this.percentage = percentage;
        this.category = category;
        this.note = note;
    }

    public static AllocationSplit outOfDelivery(BigDecimal percentage, Category category, String note) {
        return new AllocationSplit(Kind.OUT_OF_DELIVERY, null, null, percentage, category, note);
    }

    public Kind getKind() { return kind; }
    public String getWorkItemId() { return workItemId; }
    public String getWorkItemTitle() { return workItemTitle; }
    public BigDecimal getPercentage() { return percentage; }
    public Category getCategory() { return category; }
    public String getNote() { return note; }

    public boolean isOutOfDelivery() { return kind == Kind.OUT_OF_DELIVERY; }
}
