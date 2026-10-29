package andromeda.sei.ms.feature.organization.infrastructure.persistence;

import andromeda.sei.ms.feature.organization.application.port.WorkItemReferenceLookup;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Lean work item lookups of the pattern detection: only {@code externalId} and {@code title} are read. */
@Component
@RequiredArgsConstructor
public class MongoWorkItemReferenceLookup implements WorkItemReferenceLookup {

    /** Same shape as {@code BranchPatternMatcher.JIRA_KEY_REGEX}, anchored for the whole external id. */
    static final String JIRA_STYLE_EXTERNAL_ID = "^[A-Z][A-Z0-9]{1,9}-\\d+$";

    private final MongoTemplate mongoTemplate;

    @Override
    public Map<String, String> activeTitlesByExternalId(String organizationId, Collection<String> externalIds) {
        Map<String, String> titles = new HashMap<>();
        if (externalIds == null || externalIds.isEmpty()) return titles;
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("active").is(true).and("externalId").in(externalIds));
        query.fields().include("externalId", "title");
        for (WorkItemDocument item : mongoTemplate.find(query, WorkItemDocument.class)) {
            titles.putIfAbsent(item.getExternalId(), item.getTitle() == null ? "" : item.getTitle());
        }
        return titles;
    }

    @Override
    public boolean hasJiraStyleExternalIds(String organizationId) {
        Query query = Query.query(Criteria.where("organizationId").is(organizationId)
                .and("active").is(true).and("externalId").regex(JIRA_STYLE_EXTERNAL_ID));
        return mongoTemplate.exists(query, WorkItemDocument.class);
    }
}
