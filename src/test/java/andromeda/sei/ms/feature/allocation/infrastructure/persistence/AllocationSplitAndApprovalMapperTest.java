package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import andromeda.sei.ms.feature.allocation.domain.AllocationSplit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** uso-ia-fora-de-entrega P1: kind/category/note on the split, status AWAITING_APPROVAL and the approval block. */
class AllocationSplitAndApprovalMapperTest {

    private final AiUsageAllocationDocumentMapper mapper = new AiUsageAllocationDocumentMapper();

    private AiUsageAllocation allocation(List<AllocationSplit> splits) {
        Instant now = Instant.now();
        return new AiUsageAllocation("a1", "org", "u1", LocalDate.of(2026, 10, 28), List.of("x"), splits,
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.MANUAL, true, 0L, now, now);
    }

    @Test
    void should_readAbsentKindAsItem() {
        AiUsageAllocationDocument doc = AiUsageAllocationDocument.builder().id("a1").active(true)
                .splits(List.of(new AllocationSplitDocument("wi-1", "T", new BigDecimal("1.0000")))).build();

        AllocationSplit split = mapper.toDomain(doc).getSplits().get(0);

        assertThat(split.getKind()).isEqualTo(AllocationSplit.Kind.ITEM);
        assertThat(split.isOutOfDelivery()).isFalse();
        assertThat(split.getCategory()).isNull();
        assertThat(split.getNote()).isNull();
    }

    @Test
    void should_writeKindCategoryAndNote_onlyForOutOfDelivery() {
        AiUsageAllocationDocument doc = mapper.toDocument(allocation(List.of(
                new AllocationSplit("wi-1", "T", new BigDecimal("0.7000")),
                AllocationSplit.outOfDelivery(new BigDecimal("0.3000"), AllocationSplit.Category.OTHER, "reunião"))));

        AllocationSplitDocument item = doc.getSplits().get(0);
        assertThat(item.getKind()).isNull();
        assertThat(item.getCategory()).isNull();
        assertThat(item.getNote()).isNull();
        AllocationSplitDocument out = doc.getSplits().get(1);
        assertThat(out.getKind()).isEqualTo(AllocationSplit.Kind.OUT_OF_DELIVERY);
        assertThat(out.getCategory()).isEqualTo(AllocationSplit.Category.OTHER);
        assertThat(out.getNote()).isEqualTo("reunião");
        assertThat(out.getWorkItemId()).isNull();
    }

    @Test
    void should_roundTripOutOfDeliverySplit() {
        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(allocation(List.of(
                new AllocationSplit("wi-1", "T", new BigDecimal("0.7000")),
                AllocationSplit.outOfDelivery(new BigDecimal("0.3000"), AllocationSplit.Category.STUDY, null)))));

        AllocationSplit item = back.getSplits().get(0);
        AllocationSplit out = back.getSplits().get(1);
        assertThat(item.getKind()).isEqualTo(AllocationSplit.Kind.ITEM);
        assertThat(item.getWorkItemId()).isEqualTo("wi-1");
        assertThat(out.getKind()).isEqualTo(AllocationSplit.Kind.OUT_OF_DELIVERY);
        assertThat(out.getCategory()).isEqualTo(AllocationSplit.Category.STUDY);
        assertThat(out.getNote()).isNull();
        assertThat(out.getPercentage()).isEqualByComparingTo("0.3000");
    }

    @Test
    void should_roundTripAwaitingApprovalWithApprovalBlock() {
        AiUsageAllocation a = allocation(List.of(AllocationSplit.outOfDelivery(
                new BigDecimal("1.0000"), AllocationSplit.Category.SUPPORT, null)));
        a.setStatus(AiUsageAllocation.Status.AWAITING_APPROVAL);
        a.setApproval(new AiUsageAllocation.Approval(
                List.of(new AiUsageAllocation.ApprovalReason(AiUsageAllocation.ApprovalReasonCode.REPO_OTHER_PROJECT,
                                "org/andromeda-ms", "p-1", "Andromeda", "wi-9", "AB#4812",
                                null, null, null, null, null, null),
                        new AiUsageAllocation.ApprovalReason(AiUsageAllocation.ApprovalReasonCode.OUT_OF_DELIVERY_OVER_CAP,
                                null, null, null, null, null,
                                15, new BigDecimal("120.00"), "2026-09",
                                new BigDecimal("16.40"), new BigDecimal("4.20"), 17)),
                List.of("p-1"), Instant.parse("2026-10-31T12:00:00Z"),
                AiUsageAllocation.ApprovalDecision.RETURNED, "lead-1", Instant.parse("2026-10-31T13:00:00Z"), "explique"));

        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(a));

        assertThat(back.getStatus()).isEqualTo(AiUsageAllocation.Status.AWAITING_APPROVAL);
        assertThat(back.closesTheDay()).isFalse();
        assertThat(back.getApproval()).isEqualTo(a.getApproval());
    }

    @Test
    void should_keepApprovalNull_whenNeverAsked() {
        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(allocation(List.of())));

        assertThat(back.getApproval()).isNull();
        assertThat(mapper.toDocument(allocation(List.of())).getApproval()).isNull();
    }

    @Test
    void should_roundTripReturnedReviewReasonWithNote() {
        AiUsageAllocation a = allocation(List.of());
        a.setReviewReasons(List.of(new AiUsageAllocation.ReviewReason(
                AiUsageAllocation.ReviewCode.RETURNED, null, null, null, "falta o motivo")));

        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(a));

        assertThat(back.getReviewReasons()).containsExactlyElementsOf(a.getReviewReasons());
        assertThat(new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.USAGE_SPIKE, null, null, 6L).note())
                .isNull();
    }
}
