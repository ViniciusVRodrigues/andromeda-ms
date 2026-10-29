package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.organization.application.port.WorkItemPatternSampleSource;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Branch sample of the pattern detection: {@code sourceBranch} of the active pull requests created since the
 * cut plus {@code branch} of the active agent rows ({@code source=AGENT}) of the same period. Each source is a
 * grouped, sorted, limited aggregation that returns only the branch and the last time it was seen, never the documents.
 */
@Component
@RequiredArgsConstructor
public class MongoWorkItemPatternSampleSource implements WorkItemPatternSampleSource {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<String> recentBranches(String organizationId, Instant since, int limit) {
        Map<String, Instant> seen = new HashMap<>();
        pullRequestBranches(organizationId, since, limit).forEach((b, t) -> seen.merge(b, t, this::later));
        agentBranches(organizationId, since, limit).forEach((b, t) -> seen.merge(b, t, this::later));
        return seen.entrySet().stream()
                .sorted(Map.Entry.<String, Instant>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<String, Instant> pullRequestBranches(String organizationId, Instant since, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizationId").is(organizationId)
                        .and("active").is(true)
                        .and("createdAt").gte(since)
                        .and("sourceBranch").ne(null)),
                Aggregation.group("sourceBranch").max("createdAt").as("seen"),
                Aggregation.sort(Sort.Direction.DESC, "seen"),
                Aggregation.limit(limit));
        return collect(mongoTemplate.aggregate(aggregation, VcsPullRequestDocument.COLLECTION, Document.class)
                .getMappedResults());
    }

    private Map<String, Instant> agentBranches(String organizationId, Instant since, int limit) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("organizationId").is(organizationId)
                        .and("source").is("AGENT")
                        .and("active").is(true)
                        .and("day").gte(since.atZone(ZoneOffset.UTC).toLocalDate())
                        .and("branch").ne(null)),
                Aggregation.group("branch").max("day").as("seen"),
                Aggregation.sort(Sort.Direction.DESC, "seen"),
                Aggregation.limit(limit));
        return collect(mongoTemplate.aggregate(aggregation, AiUsageDocument.class, Document.class).getMappedResults());
    }

    private static Map<String, Instant> collect(List<Document> rows) {
        Map<String, Instant> out = new HashMap<>();
        for (Document row : rows) {
            Object id = row.get("_id");
            if (!(id instanceof String branch) || branch.isBlank()) continue;
            Object seen = row.get("seen");
            Instant at = seen instanceof Date d ? d.toInstant() : Instant.EPOCH;
            out.merge(branch, at, (a, b) -> a.isAfter(b) ? a : b);
        }
        return out;
    }

    private Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
