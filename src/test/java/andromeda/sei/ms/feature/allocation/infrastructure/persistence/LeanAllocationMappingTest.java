package andromeda.sei.ms.feature.allocation.infrastructure.persistence;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocation;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dashboards-customizaveis (B1 performance) — the raw (unmapped) read of a project's allocations
 * builds the fields the cost breakdown reads: id, user, day, usage ids and splits with their
 * percentage, status VALIDATED.
 */
class LeanAllocationMappingTest {

    @Test
    void should_mapTheProjectedFields() {
        ObjectId id = new ObjectId();
        Document raw = new Document("_id", id).append("userId", "user-1").append("aiUsageIds", List.of("u1", "u2"))
                .append("splits", List.of(new Document("workItemId", "wi-1").append("percentage", new Decimal128(new BigDecimal("0.40"))),
                        new Document("workItemId", "wi-2").append("percentage", new Decimal128(new BigDecimal("0.60")))));

        AiUsageAllocation allocation = AiUsageAllocationRepositoryImpl.leanAllocation(raw, "org-1", LocalDate.parse("2026-10-02"));

        assertThat(allocation.getId()).isEqualTo(id.toHexString());
        assertThat(allocation.getOrganizationId()).isEqualTo("org-1");
        assertThat(allocation.getUserId()).isEqualTo("user-1");
        assertThat(allocation.getDay()).isEqualTo(LocalDate.parse("2026-10-02"));
        assertThat(allocation.getAiUsageIds()).containsExactly("u1", "u2");
        assertThat(allocation.getSplits()).extracting(s -> s.getWorkItemId()).containsExactly("wi-1", "wi-2");
        assertThat(allocation.getSplits().get(0).getPercentage()).isEqualByComparingTo("0.40");
        assertThat(allocation.getStatus()).isEqualTo(AiUsageAllocation.Status.VALIDATED);
        assertThat(allocation.isActive()).isTrue();
    }

    @Test
    void should_tolerateMissingArrays() {
        AiUsageAllocation allocation = AiUsageAllocationRepositoryImpl.leanAllocation(
                new Document("_id", new ObjectId()).append("userId", "user-1"), "org-1", null);

        assertThat(allocation.getAiUsageIds()).isEmpty();
        assertThat(allocation.getSplits()).isEmpty();
    }
}
