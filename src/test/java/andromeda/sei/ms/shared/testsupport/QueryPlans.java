package andromeda.sei.ms.shared.testsupport;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Winning plan of a query, read from {@code explain}: the stages and the indexes it uses. For the
 * tests that guard a partial index (the planner only picks it when the query repeats the filter).
 */
public final class QueryPlans {

    private QueryPlans() {
    }

    /** What the planner chose for the query: every stage name and every index name of the winning plan. */
    public record Plan(List<String> stages, List<String> indexes) {

        public boolean scansTheCollection() {
            return stages.contains("COLLSCAN");
        }
    }

    public static Plan winning(MongoTemplate mongoTemplate, Class<?> entity, Query query) {
        QueryMapper mapper = new QueryMapper(mongoTemplate.getConverter());
        Document filter = mapper.getMappedObject(query.getQueryObject(),
                mongoTemplate.getConverter().getMappingContext().getPersistentEntity(entity));
        Document find = new Document("find", mongoTemplate.getCollectionName(entity)).append("filter", filter);
        if (!query.getSortObject().isEmpty()) find.append("sort", query.getSortObject());
        Document explain = mongoTemplate.executeCommand(new Document("explain", find)
                .append("verbosity", "queryPlanner"));
        Document winning = explain.get("queryPlanner", Document.class).get("winningPlan", Document.class);
        List<String> stages = new ArrayList<>();
        List<String> indexes = new ArrayList<>();
        collect(winning, stages, indexes);
        return new Plan(stages, indexes);
    }

    private static void collect(Object node, List<String> stages, List<String> indexes) {
        if (node instanceof Document d) {
            if (d.get("stage") instanceof String stage) stages.add(stage);
            if (d.get("indexName") instanceof String index) indexes.add(index);
            d.values().forEach(v -> collect(v, stages, indexes));
        } else if (node instanceof List<?> list) {
            list.forEach(v -> collect(v, stages, indexes));
        }
    }
}
