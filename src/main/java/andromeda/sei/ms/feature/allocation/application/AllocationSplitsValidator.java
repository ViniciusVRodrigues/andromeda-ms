package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Shape rules of a day's splits (uso-ia-fora-de-entrega, design 1): the sum is 1.0000; an ITEM split
 * carries a work item and no category/note; the single OUT_OF_DELIVERY split carries a category, no
 * work item, and a note (1..140 after trim) when the category is OTHER (optional up to 140 otherwise).
 * Whether the items exist and are eligible stays with the use cases.
 */
public final class AllocationSplitsValidator {

    public static final int NOTE_MAX = 140;
    private static final BigDecimal ONE_SCALED = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);

    private AllocationSplitsValidator() {}

    /** Trims the notes (blank becomes null); everything else is kept. */
    public static List<AllocationSplit> normalize(List<AllocationSplit> splits) {
        if (splits == null) return null;
        return splits.stream().map(s -> {
            String note = s.getNote() == null || s.getNote().isBlank() ? null : s.getNote().trim();
            return new AllocationSplit(s.getKind(), s.getWorkItemId(), s.getWorkItemTitle(), s.getPercentage(),
                    s.getCategory(), note);
        }).toList();
    }

    public static void validate(List<AllocationSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            throw new AllocationSplitsInvalidException("At least one split is required");
        }
        BigDecimal sum = splits.stream()
                .map(AllocationSplit::getPercentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        if (sum.compareTo(ONE_SCALED) != 0) {
            throw new AllocationSplitsInvalidException();
        }
        int outOfDelivery = 0;
        for (AllocationSplit s : splits) {
            if (s.isOutOfDelivery()) {
                outOfDelivery++;
                validateOutOfDelivery(s);
            } else {
                validateItem(s);
            }
        }
        if (outOfDelivery > 1) {
            throw new AllocationSplitsInvalidException("Only one OUT_OF_DELIVERY split is allowed per allocation");
        }
    }

    private static void validateItem(AllocationSplit s) {
        if (s.getWorkItemId() == null || s.getWorkItemId().isBlank()) {
            throw new AllocationSplitsInvalidException("An ITEM split requires workItemId");
        }
        if (s.getCategory() != null || s.getNote() != null) {
            throw new AllocationSplitsInvalidException("An ITEM split does not accept category or note");
        }
    }

    private static void validateOutOfDelivery(AllocationSplit s) {
        if (s.getWorkItemId() != null && !s.getWorkItemId().isBlank()) {
            throw new AllocationSplitsInvalidException("An OUT_OF_DELIVERY split does not accept workItemId");
        }
        if (s.getCategory() == null) {
            throw new AllocationSplitsInvalidException("An OUT_OF_DELIVERY split requires category");
        }
        String note = s.getNote() == null ? "" : s.getNote().trim();
        if (s.getCategory() == AllocationSplit.Category.OTHER && note.isEmpty()) {
            throw new AllocationSplitsInvalidException("note is required when category is OTHER");
        }
        if (note.length() > NOTE_MAX) {
            throw new AllocationSplitsInvalidException("note must have at most " + NOTE_MAX + " characters");
        }
    }
}
