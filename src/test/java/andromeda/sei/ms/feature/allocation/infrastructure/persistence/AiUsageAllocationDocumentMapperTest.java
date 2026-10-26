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
    void should_readAbsentSourceAsInProgress() {
        AiUsageAllocation back = mapper.toDomain(mapper.toDocument(allocation(null)));

        assertThat(back.getSuggestionSource()).isNull();
        assertThat(back.suggestionSourceOrDefault()).isEqualTo(AiUsageAllocation.SuggestionSource.IN_PROGRESS);
    }
}
