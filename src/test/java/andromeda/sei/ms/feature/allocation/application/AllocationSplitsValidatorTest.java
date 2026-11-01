package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit.Category;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit.Kind;
import andromeda.sei.ms.feature.allocation.domain.exception.AllocationSplitsInvalidException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AllocationSplitsValidatorTest {

    private static BigDecimal pct(String v) { return new BigDecimal(v); }

    private static AllocationSplit item(String id, String p) { return new AllocationSplit(id, "T", pct(p)); }

    private static AllocationSplit out(String p, Category c, String note) {
        return AllocationSplit.outOfDelivery(pct(p), c, note);
    }

    private static void assertInvalid(List<AllocationSplit> splits, String detailPart) {
        assertThatThrownBy(() -> AllocationSplitsValidator.validate(AllocationSplitsValidator.normalize(splits)))
                .isInstanceOf(AllocationSplitsInvalidException.class)
                .satisfies(e -> {
                    assertThat(((AllocationSplitsInvalidException) e).getCode()).isEqualTo("ALLOCATION_SPLITS_INVALID");
                    assertThat(e.getMessage()).contains(detailPart);
                });
    }

    @Test
    void should_accept_itemsAndOneOutOfDeliverySummingOne() {
        assertThatCode(() -> AllocationSplitsValidator.validate(List.of(
                item("wi-1", "0.7000"), out("0.3000", Category.STUDY, null)))).doesNotThrowAnyException();
    }

    @Test
    void should_accept_aDayEntirelyOutOfDelivery() {
        assertThatCode(() -> AllocationSplitsValidator.validate(List.of(
                out("1.0000", Category.SUPPORT, "ajudei o time")))).doesNotThrowAnyException();
    }

    @Test
    void should_reject_sumDifferentFromOne() {
        assertInvalid(List.of(item("wi-1", "0.5000"), out("0.3000", Category.STUDY, null)), "sum to 1.0");
    }

    @Test
    void should_reject_itemWithoutWorkItemId() {
        assertInvalid(List.of(new AllocationSplit(Kind.ITEM, " ", null, pct("1.0000"), null, null)), "requires workItemId");
        assertInvalid(List.of(new AllocationSplit(null, null, null, pct("1.0000"), null, null)), "requires workItemId");
    }

    @Test
    void should_reject_itemWithNote() {
        assertInvalid(List.of(new AllocationSplit(Kind.ITEM, "wi-1", "T", pct("1.0000"), null, "x")), "does not accept");
    }

    @Test
    void should_reject_outOfDeliveryWithWorkItemId() {
        assertInvalid(List.of(new AllocationSplit(Kind.OUT_OF_DELIVERY, "wi-1", null, pct("1.0000"),
                Category.STUDY, null)), "does not accept workItemId");
    }

    @Test
    void should_reject_outOfDeliveryWithoutCategory() {
        assertInvalid(List.of(out("1.0000", null, null)), "requires category");
    }

    @Test
    void should_requireNote_whenCategoryIsOther() {
        assertInvalid(List.of(out("1.0000", Category.OTHER, null)), "note is required");
        assertInvalid(List.of(out("1.0000", Category.OTHER, "   ")), "note is required");
        assertThatCode(() -> AllocationSplitsValidator.validate(AllocationSplitsValidator.normalize(
                List.of(out("1.0000", Category.OTHER, " reunião externa ")))))
                .doesNotThrowAnyException();
    }

    @Test
    void should_limitNoteTo140CharactersAfterTrim() {
        assertThatCode(() -> AllocationSplitsValidator.validate(AllocationSplitsValidator.normalize(
                List.of(out("1.0000", Category.STUDY, " " + "a".repeat(140) + " ")))))
                .doesNotThrowAnyException();
        assertInvalid(List.of(out("1.0000", Category.STUDY, "a".repeat(141))), "at most 140");
        assertInvalid(List.of(out("1.0000", Category.OTHER, "a".repeat(141))), "at most 140");
    }

    @Test
    void should_rejectMoreThanOneOutOfDeliverySplit() {
        assertInvalid(List.of(out("0.5000", Category.STUDY, null), out("0.5000", Category.SUPPORT, null)),
                "Only one OUT_OF_DELIVERY");
    }

    @Test
    void should_trimNotes_andTurnBlankIntoNull() {
        List<AllocationSplit> normalized = AllocationSplitsValidator.normalize(List.of(
                out("0.5000", Category.STUDY, "  fiz curso "), item("wi-1", "0.5000")));

        assertThat(normalized.get(0).getNote()).isEqualTo("fiz curso");
        assertThat(normalized.get(0).getKind()).isEqualTo(Kind.OUT_OF_DELIVERY);
        assertThat(normalized.get(1).getKind()).isEqualTo(Kind.ITEM);
        assertThat(AllocationSplitsValidator.normalize(List.of(out("1.0000", Category.STUDY, "  "))).get(0).getNote())
                .isNull();
    }
}
