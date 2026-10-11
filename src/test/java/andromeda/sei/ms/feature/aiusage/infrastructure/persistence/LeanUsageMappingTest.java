package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AIUsage;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dashboards-customizaveis (B1 performance) — the raw (unmapped) read of usage records by id keeps
 * what a cost breakdown reads: id, user, day, model, tokens, interactions, cost and allocation.
 */
class LeanUsageMappingTest {

    @Test
    void should_mapTheProjectedFields() {
        ObjectId id = new ObjectId();
        Document raw = new Document("_id", id).append("userId", "user-1").append("model", "claude")
                .append("inputTokens", 1000L).append("outputTokens", 200).append("interactions", 3)
                .append("costUsd", new Decimal128(new BigDecimal("1.25"))).append("allocationId", "al-1");

        AIUsage usage = AiUsageRepositoryImpl.leanUsage(raw, "org-1", LocalDate.parse("2026-10-02"));

        assertThat(usage.getId()).isEqualTo(id.toHexString());
        assertThat(usage.getOrganizationId()).isEqualTo("org-1");
        assertThat(usage.getUserId()).isEqualTo("user-1");
        assertThat(usage.getDay()).isEqualTo(LocalDate.parse("2026-10-02"));
        assertThat(usage.getModel()).isEqualTo("claude");
        assertThat(usage.getInputTokens()).isEqualTo(1000L);
        assertThat(usage.getOutputTokens()).isEqualTo(200L);
        assertThat(usage.getInteractions()).isEqualTo(3);
        assertThat(usage.getCostUsd()).isEqualByComparingTo("1.25");
        assertThat(usage.getAllocationId()).isEqualTo("al-1");
    }

    @Test
    void should_keepAMissingCostNull() {
        AIUsage usage = AiUsageRepositoryImpl.leanUsage(new Document("_id", new ObjectId()), "org-1", null);

        assertThat(usage.getCostUsd()).isNull();
        assertThat(usage.getInputTokens()).isZero();
    }
}
