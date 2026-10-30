package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AgentUsageBaseline;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** Index {@code {organizationId, userId, day}} of {@code ai_usages} covers the match. */
@Repository
@RequiredArgsConstructor
public class AgentUsageBaselineImpl implements AgentUsageBaseline {

    private final MongoTemplate mongoTemplate;

    @Override
    public Map<LocalDate, Long> dailyTokenTotals(String organizationId, String userId, LocalDate from, LocalDate to) {
        Map<LocalDate, Long> totals = new HashMap<>();
        if (organizationId == null || userId == null || from == null || to == null || to.isBefore(from)) return totals;
        Criteria match = Criteria.where("organizationId").is(organizationId).and("userId").is(userId)
                .and("source").is("AGENT").and("active").is(true)
                .and("day").gte(from).lte(to);
        AggregationOperation group = ctx -> new Document("$group", new Document("_id", "$day")
                .append("total", new Document("$sum", new Document("$add", java.util.List.of(
                        new Document("$ifNull", java.util.List.of("$inputTokens", 0)),
                        new Document("$ifNull", java.util.List.of("$outputTokens", 0)))))));
        mongoTemplate.aggregate(Aggregation.newAggregation(Aggregation.match(match), group), "ai_usages", Document.class)
                .getMappedResults().forEach(d -> {
                    Object id = d.get("_id");
                    if (!(id instanceof java.util.Date date)) return;
                    LocalDate day = Instant.ofEpochMilli(date.getTime()).atZone(ZoneOffset.UTC).toLocalDate();
                    Number total = (Number) d.get("total");
                    totals.put(day, total == null ? 0L : total.longValue());
                });
        return totals;
    }
}
