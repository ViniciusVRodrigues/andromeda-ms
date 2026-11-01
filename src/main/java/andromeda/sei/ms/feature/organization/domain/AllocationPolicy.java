package andromeda.sei.ms.feature.organization.domain;

/**
 * Per-organization knobs of the AI usage allocation (uso-ia-fora-de-entrega). The cap is the share of a
 * person's AI cost of the previous closed month that may go to "Fora de entrega" before a lead has to
 * approve. 0 sends every out-of-delivery entry to approval; 100 does not switch the rule off.
 */
public record AllocationPolicy(int outOfDeliveryCapPercent) {

    public static final int DEFAULT_CAP_PERCENT = 15;

    public AllocationPolicy {
        if (outOfDeliveryCapPercent < 0 || outOfDeliveryCapPercent > 100) {
            throw new IllegalArgumentException("outOfDeliveryCapPercent must be between 0 and 100");
        }
    }

    public static AllocationPolicy defaults() {
        return new AllocationPolicy(DEFAULT_CAP_PERCENT);
    }
}
