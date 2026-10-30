package andromeda.sei.ms.feature.aiusage.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.domain.AiefOverlapRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class AiefOverlapRepositoryImpl implements AiefOverlapRepository {

    private static final String AGENT = "AGENT";
    private static final int CHUNK = 5000;

    private final MongoTemplate mongoTemplate;

    @Override
    public List<Candidate> findCandidates(String organizationId, Collection<String> editors, LocalDate fromDay) {
        if (editors == null || editors.isEmpty()) return List.of();
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("editor").in(editors)
                .and("source").is(null);
        if (fromDay != null) criteria = criteria.and("day").gte(fromDay);
        criteria = new Criteria().andOperator(criteria,
                new Criteria().orOperator(Criteria.where("active").is(true), Criteria.where("supersededBy").is(AGENT)));
        Query query = Query.query(criteria);
        query.fields().include("userId", "day", "editor", "active", "supersededBy");
        return mongoTemplate.find(query, AiUsageDocument.class).stream()
                .map(d -> new Candidate(d.getId(), d.getUserId(), d.getDay(), d.getEditor(), d.isActive(),
                        d.getSupersededBy()))
                .toList();
    }

    @Override
    public Set<AgentCoverage> findAgentCoverage(String organizationId, Collection<Candidate> candidates,
                                                Collection<String> tools) {
        if (candidates.isEmpty() || tools.isEmpty()) return Set.of();
        Set<String> users = candidates.stream().map(Candidate::userId).collect(Collectors.toSet());
        Set<LocalDate> days = candidates.stream().map(Candidate::day).collect(Collectors.toSet());
        Criteria criteria = Criteria.where("organizationId").is(organizationId)
                .and("userId").in(users).and("day").in(days)
                .and("source").is(AGENT).and("active").is(true).and("tool").in(tools);
        var agg = Aggregation.newAggregation(Aggregation.match(criteria),
                Aggregation.group("userId", "day", "tool"));
        Set<AgentCoverage> out = new HashSet<>();
        for (Document row : mongoTemplate.aggregate(agg, "ai_usages", Document.class).getMappedResults()) {
            Document id = row.get("_id", Document.class);
            if (id == null) continue;
            Object day = id.get("day");
            // Spring Data stores LocalDate as a Date at the start of the day in the system zone.
            LocalDate d = day instanceof Date date
                    ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                    : day instanceof LocalDate ld ? ld : null;
            if (d == null) continue;
            out.add(new AgentCoverage(id.getString("userId"), d, id.getString("tool")));
        }
        return out;
    }

    @Override
    public long deactivate(String organizationId, Collection<String> ids) {
        Update update = new Update().set("active", false).set("supersededBy", AGENT).set("updatedAt", Instant.now());
        long changed = 0;
        for (List<String> chunk : chunks(ids)) {
            Query query = Query.query(Criteria.where("_id").in(chunk).and("organizationId").is(organizationId)
                    .and("source").is(null).and("active").is(true));
            changed += mongoTemplate.updateMulti(query, update, AiUsageDocument.class).getModifiedCount();
        }
        return changed;
    }

    @Override
    public long reactivate(String organizationId, Collection<String> ids) {
        Update update = new Update().set("active", true).unset("supersededBy").set("updatedAt", Instant.now());
        long changed = 0;
        for (List<String> chunk : chunks(ids)) {
            Query query = Query.query(Criteria.where("_id").in(chunk).and("organizationId").is(organizationId)
                    .and("source").is(null).and("active").is(false).and("supersededBy").is(AGENT));
            changed += mongoTemplate.updateMulti(query, update, AiUsageDocument.class).getModifiedCount();
        }
        return changed;
    }

    /** The first run walks the whole history: the ids go in slices so no {@code $in} nears the 16 MB limit. */
    private static List<List<String>> chunks(Collection<String> ids) {
        List<String> all = List.copyOf(ids);
        List<List<String>> out = new java.util.ArrayList<>();
        for (int i = 0; i < all.size(); i += CHUNK) out.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
        return out;
    }
}
