package andromeda.sei.ms.feature.dashboard.infrastructure.persistence;

import andromeda.sei.ms.feature.aiusage.infrastructure.persistence.AiUsageDocument;
import andromeda.sei.ms.feature.allocation.infrastructure.persistence.AiUsageAllocationDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsCommitDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemDocument;
import andromeda.sei.ms.feature.workitem.infrastructure.persistence.WorkItemTransitionDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

/**
 * Creates the indexes the v2 dashboards query by (dashboards-customizaveis, QA finding B1). This
 * application does not apply the {@code @CompoundIndex} declarations ({@code MongoConfig} extends
 * {@code AbstractMongoClientConfiguration}, whose mapping context has auto index creation off), so
 * without this every dashboard read scanned its collection.
 *
 * <ul>
 *   <li>Only <b>non-unique</b> indexes: they cannot fail on duplicated data already stored.</li>
 *   <li>Same keys as the declarations; where a declaration names its index the same name is used,
 *       and where it does not no name is given, so MongoDB derives the same default name — creating
 *       an index that already exists is a no-op, never a conflict.</li>
 *   <li>Each index is created on its own; a failure is logged and the next one still runs. These
 *       indexes are for speed only, so a failure must not stop the startup.</li>
 * </ul>
 * The general fix (creating every declared index) is a separate delivery.
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class DashboardQueryIndexInitializer implements ApplicationRunner {

    static final String ALLOCATION_ORG_USER_DAY = "idx_allocation_org_user_day";
    static final String ALLOCATION_ORG_SPLIT_WORK_ITEM_DAY = "idx_allocation_org_split_workitem_day";
    static final String PULL_REQUEST_ORG_REPO_MERGED = "idx_pr_org_repo_merged";

    private static final String ORGANIZATION_ID = "organizationId";
    private static final String REPOSITORY_ID = "repositoryId";
    private static final String USER_ID = "userId";
    private static final String DAY = "day";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensure(WorkItemTransitionDocument.class, asc(ORGANIZATION_ID, "workItemId"));
        ensure(WorkItemDocument.class, asc(ORGANIZATION_ID, "projectId"));
        ensure(VcsPullRequestDocument.class, asc(ORGANIZATION_ID, REPOSITORY_ID).on("createdAt", Sort.Direction.DESC));
        ensure(VcsPullRequestDocument.class,
                asc(ORGANIZATION_ID, REPOSITORY_ID, "status").on("createdAt", Sort.Direction.DESC));
        ensure(VcsPullRequestDocument.class, asc(ORGANIZATION_ID, REPOSITORY_ID, "mergedAt")
                .named(PULL_REQUEST_ORG_REPO_MERGED));
        ensure(VcsCommitDocument.class, asc(ORGANIZATION_ID, REPOSITORY_ID, "authoredAt"));
        ensure(AiUsageDocument.class, asc(ORGANIZATION_ID, USER_ID, DAY));
        ensure(AiUsageAllocationDocument.class, asc(ORGANIZATION_ID, USER_ID, DAY).named(ALLOCATION_ORG_USER_DAY));
        ensure(AiUsageAllocationDocument.class, asc(ORGANIZATION_ID, "splits.workItemId", DAY)
                .named(ALLOCATION_ORG_SPLIT_WORK_ITEM_DAY));
    }

    private void ensure(Class<?> document, Index index) {
        try {
            mongoTemplate.indexOps(document).createIndex(index);
            log.debug("DashboardQueryIndexInitializer: ensured {} on {}", index.getIndexKeys().toJson(),
                    document.getSimpleName());
        } catch (RuntimeException e) {
            log.warn("DashboardQueryIndexInitializer: could not create {} on {} (startup continues): {}",
                    index.getIndexKeys().toJson(), document.getSimpleName(), e.getMessage());
        }
    }

    private static Index asc(String... fields) {
        Index index = new Index();
        for (String field : fields) index.on(field, Sort.Direction.ASC);
        return index;
    }
}
