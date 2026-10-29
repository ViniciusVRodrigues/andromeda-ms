package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AiUsageModelOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code @DataMongoTest} does not run under Boot 4 in this repo, so the aggregation is checked
 * against a mocked {@link MongoTemplate}: the pipeline filters by organization, active and the
 * inclusive day range, and groups by model.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageRepositoryImplSummarizeOutputTest {

    @Mock private AiUsageMongoRepository mongoRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private AggregationResults<Map> results;

    private AiUsageRepositoryImpl repo() {
        return new AiUsageRepositoryImpl(mongoRepository, new AiUsageDocumentMapper(), mongoTemplate);
    }

    private String pipeline(LocalDate from, LocalDate to) {
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(AiUsageDocument.class), eq(Map.class))).thenReturn(results);
        when(results.getMappedResults()).thenReturn(List.<Map>of(
                Map.of("_id", "gpt-4o", "outputTokens", 1500L, "records", 3),
                Map.of("_id", "auto", "outputTokens", 10, "records", 1L)));

        List<AiUsageModelOutput> out = repo().summarizeOutputByModel("org-1", from, to);

        assertThat(out).containsExactly(new AiUsageModelOutput("gpt-4o", 1500L, 3), new AiUsageModelOutput("auto", 10L, 1));
        ArgumentCaptor<Aggregation> agg = ArgumentCaptor.forClass(Aggregation.class);
        verify(mongoTemplate).aggregate(agg.capture(), eq(AiUsageDocument.class), eq(Map.class));
        return agg.getValue().toString();
    }

    @Test
    void should_scopeByOrganizationActiveAndInclusiveDays_andGroupByModel() {
        String json = pipeline(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertThat(json).contains("organizationId").contains("org-1").contains("active").contains("$gte").contains("$lte")
                .contains("$group").contains("$sum").contains("outputTokens")
                .contains("$cond").contains("AGENT").contains("agentCacheReadTokens").contains("agentCacheWriteTokens")
                .contains("agentInputTokens");
    }

    @Test
    void should_leaveTheDayOpen_whenNoPeriodIsGiven() {
        String json = pipeline(null, null);

        assertThat(json).contains("org-1").doesNotContain("$gte").doesNotContain("$lte");
    }

    @Test
    void updateCost_withNull_setsCostToNull() {
        repo().updateCost("org-1", "u", "ds", LocalDate.of(2026, 10, 1), "m", "ws", "mc", null);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(any(Query.class), update.capture(), eq(AiUsageDocument.class));
        org.bson.Document set = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set.containsKey("costUsd")).isTrue();
        assertThat(set.get("costUsd")).isNull();
    }
}
