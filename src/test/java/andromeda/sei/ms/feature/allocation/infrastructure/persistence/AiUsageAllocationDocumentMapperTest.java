package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageAllocationDocumentMapperTest {

    private final AiUsageAllocationDocumentMapper mapper = new AiUsageAllocationDocumentMapper();

    private AiUsageAllocation allocation(AiUsageAllocation.SuggestionSource source) {
        Instant now = Instant.now();
        return new AiUsageAllocation("a1", "org", "u1", LocalDate.of(2026, 9, 30), List.of("x"), List.of(),
                AiUsageAllocation.Status.PENDING_VALIDATION, AiUsageAllocation.AssignedBy.AUTO, true, 0L, now, now,
                source);
    }

    @Test
    void should_roundTripSuggestionSource() {
        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(
                allocation(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY)));

        assertThat(back.getSuggestionSource()).isEqualTo(AiUsageAllocation.SuggestionSource.AZURE_ACTIVITY);
    }

    @Test
    void should_roundTripReviewReasonsAcknowledgementAndOverriddenTaskRef() {
        AiUsageAllocation a = allocation(null);
        a.setReviewReasons(List.of(
                new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.ITEM_CLOSED, "wi-1", "AB#1", null),
                new AiUsageAllocation.ReviewReason(AiUsageAllocation.ReviewCode.USAGE_SPIKE, null, null, 6L)));
        a.setReviewAcknowledgedAt(Instant.parse("2026-10-30T10:00:00Z"));
        a.setAgent(new AiUsageAllocation.AgentInfo(AiUsageAllocation.ResolvedBy.BRANCH, List.of("r"), List.of("b"), "AB#9"));

        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(a));

        assertThat(back.getReviewReasons()).containsExactlyElementsOf(a.getReviewReasons());
        assertThat(back.getReviewAcknowledgedAt()).isEqualTo(Instant.parse("2026-10-30T10:00:00Z"));
        assertThat(back.getAgent().overriddenTaskRef()).isEqualTo("AB#9");
    }

    @Test
    void should_readADocumentWithoutReviewReasonsAsEmpty() {
        AiUsageAllocationDocument doc = mapper.toDocument(allocation(null));
        doc.setReviewReasons(null);

        AiUsageAllocation back = mapper.toDomain(doc);

        assertThat(back.getReviewReasons()).isEmpty();
        assertThat(back.hasReviewReasons()).isFalse();
    }

    @Test
    void should_readAbsentSourceAsInProgress() {
        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(allocation(null)));

        assertThat(back.getSuggestionSource()).isNull();
        assertThat(back.suggestionSourceOrDefault()).isEqualTo(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
    }
}
