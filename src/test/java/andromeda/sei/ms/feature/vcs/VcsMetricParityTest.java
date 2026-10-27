package andromeda.sei.ms.feature.vcs;

import andromeda.sei.ms.feature.aiusage.domain.AIUsageRepository;
import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.dashboard.application.dto.DeveloperDashboardOutput;
import andromeda.sei.ms.feature.dashboard.application.usecase.GetDeveloperDashboardUseCase;
import andromeda.sei.ms.feature.metrics.application.DeliveryMetricsService;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMethod;
import andromeda.sei.ms.feature.metrics.domain.DeliveryMetrics;
import andromeda.sei.ms.feature.metrics.domain.MergedChange;
import andromeda.sei.ms.feature.metrics.domain.PerformanceClass;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.user.domain.UserRepository;
import andromeda.sei.ms.feature.vcs.application.VcsReadService;
import andromeda.sei.ms.feature.vcs.application.dto.CommitActivityPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.DeliveryMetricPointOutput;
import andromeda.sei.ms.feature.vcs.application.dto.ProjectRepositoryOutput;
import andromeda.sei.ms.feature.vcs.application.dto.RepositoryPullRequestOutput;
import andromeda.sei.ms.feature.vcs.application.usecase.CalculatePullRequestMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryCommitActivityUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.GetRepositoryDeliveryMetricsUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListProjectRepositoriesUseCase;
import andromeda.sei.ms.feature.vcs.application.usecase.ListRepositoryPullRequestsUseCase;
import andromeda.sei.ms.feature.vcs.domain.ReviewMetrics;
import andromeda.sei.ms.feature.vcs.domain.VcsPullRequest;
import andromeda.sei.ms.feature.vcs.domain.VcsProvider;
import andromeda.sei.ms.feature.vcs.infrastructure.migration.VcsCoreMigrationMappings;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsCommitDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsDocumentMapper;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsPullRequestDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepoBindingDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsRepositoryDocument;
import andromeda.sei.ms.feature.vcs.infrastructure.persistence.VcsReviewEventDocument;
import andromeda.sei.ms.feature.workitem.application.usecase.EffortBearingLevelResolver;
import andromeda.sei.ms.feature.workitem.domain.SprintRepository;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemRepository;
import andromeda.sei.ms.shared.domain.metrics.DurationStat;
import andromeda.sei.ms.shared.infrastructure.security.TenantContextHolder;
import andromeda.sei.ms.feature.workitem.domain.WorkItemFixture;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * vcs-core task 8.5 — metric parity after the migration (blocking for the merge).
 *
 * <p>A seeded dataset is written in the <em>pre-refactor</em> shape ({@code github_*} documents as the
 * old code stored them, including the per-PR metrics the old sync computed), converted by the very
 * mappings of {@code V__vcs_core} and read back through the real Spring Data converter into the
 * neutral model. Then:
 * <ol>
 *   <li>the per-PR metrics recomputed from the migrated review events equal the stored pre-refactor
 *       values (DISMISSED, [bot] suffix without flag, flagged bot, self-review and PENDING cases);</li>
 *   <li>the repository endpoints, the team, strategic and developer dashboards return the values the
 *       pre-refactor code produced for this dataset, hand-computed below with the old rules.</li>
 * </ol>
 * The delivery calculator itself is unchanged by this change; parity there means the merged-change
 * list it receives is the same, which is asserted field by field.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VcsMetricParityTest {

    private static final String ORG = "org-p";
    private static final String PROJECT = "proj-p";
    private static final Instant T = Instant.parse("2026-09-01T00:00:00Z");
    private static final LocalDate START = LocalDate.parse("2026-08-31");
    private static final LocalDate END = LocalDate.parse("2026-09-13");
    private static final ObjectId R1 = new ObjectId();
    private static final ObjectId R2 = new ObjectId();
    private static final ObjectId R_OTHER_ORG = new ObjectId();

    /** Per-PR metrics the pre-refactor sync stored: cycle, time to first review, time to first approval. */
    private final Map<String, List<Double>> stored = new LinkedHashMap<>();

    @Mock private WorkItemRepository workItemRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private AIUsageRepository aiUsageRepository;
    @Mock private AiUsageAllocationRepository allocationRepository;
    @Mock private UserRepository userRepository;

    private final InMemoryVcsStore store = new InMemoryVcsStore();
    private final Map<String, String> prIdByName = new LinkedHashMap<>();
    private VcsReadService read;
    private DeliveryMetricsService deliveryMetricsService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(ORG);
        seedPreRefactorDataAndMigrate();
        read = new VcsReadService(store.bindingRepository, store.repositoryRepository, store.pullRequestRepository,
                store.reviewEventRepository);
        deliveryMetricsService = new DeliveryMetricsService(read, workItemRepository, organizationRepository);

        WorkItem bug = WorkItemFixture.item("wi-501", WorkItem.ResolvedType.BUG, WorkItem.ResolvedStatus.DONE);
        bug.setExternalId("501");
        bug.setTitle("Checkout crash");
        when(workItemRepository.findAllByOrganizationIdAndExternalIdInAndSource(eq(ORG), any(), eq(WorkItem.Source.AZURE_DEVOPS)))
                .thenAnswer(inv -> ((Collection<?>) inv.getArgument(1)).contains("501") ? List.of(bug) : List.of());
        when(organizationRepository.findById(anyString())).thenReturn(Optional.empty());
        when(sprintRepository.findCurrentSprintByOrganizationIdAndProjectId(any(), any())).thenReturn(Optional.empty());
        when(userRepository.findAllByOrganizationId(anyString())).thenReturn(List.of());
        when(aiUsageRepository.findTotalCostByUserIdsAndDateRange(any(), any(), any(), any())).thenReturn(Map.of());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // ------------------------------------------------------------------
    // 1. Per pull request metrics recomputed from migrated review events
    // ------------------------------------------------------------------

    @Test
    void should_recomputeFromMigratedEvents_theMetricsThePreRefactorSyncStored() {
        Map<String, List<Double>> migrated = metricsByName();
        clearMetrics();

        CalculatePullRequestMetricsUseCase calculate =
                new CalculatePullRequestMetricsUseCase(store.pullRequestRepository, store.reviewEventRepository);
        calculate.execute(ORG, R1.toHexString());
        calculate.execute(ORG, R2.toHexString());

        assertThat(migrated).containsExactlyEntriesOf(stored);
        assertThat(metricsByName()).containsExactlyEntriesOf(stored);
    }

    // ------------------------------------------------------------------
    // 2. Repository endpoints
    // ------------------------------------------------------------------

    @Test
    void should_listProjectRepositories_withPreRefactorHealthFigures() {
        List<ProjectRepositoryOutput> repos = new ListProjectRepositoriesUseCase(read, deliveryMetricsService)
                .execute(PROJECT, START, END);

        assertThat(repos).extracting(ProjectRepositoryOutput::fullName).containsExactly("acme/api", "acme/web");
        ProjectRepositoryOutput api = repos.get(0);
        assertThat(api.repoId()).isEqualTo(R1.toHexString());
        assertThat(api.repositoryId()).isEqualTo(R1.toHexString());
        assertThat(api.provider()).isEqualTo(VcsProvider.GITHUB);
        assertThat(api.defaultBranch()).isEqualTo("main");
        assertThat(api.openPrCount()).isEqualTo(2);
        assertThat(api.stalePrCount()).isEqualTo(1);
        assertThat(api.prCycleTime()).isEqualTo(new DurationStat(10.0, 24.0, 3));
        assertThat(api.avgCycleTimeHours()).isEqualTo(37.0 / 3);
        assertThat(api.avgTimeToFirstReviewHours()).isEqualTo(3.0);
        assertThat(api.review()).isEqualTo(new ReviewMetrics(new DurationStat(2.0, 5.0, 3),
                new DurationStat(4.0, 8.0, 2), 75.0, 4, 24));
        DeliveryMetrics delivery = api.delivery();
        assertThat(delivery.method()).isEqualTo(DeliveryMethod.VCS_PROXY);
        assertThat(delivery.changeFailRatePercent()).isEqualTo(100.0);
        assertThat(delivery.changeFailRateClass()).isEqualTo(PerformanceClass.LOW);
        assertThat(delivery.deploymentsPerWeek()).isEqualTo(1.0);
        assertThat(delivery.changeLeadTime()).isEqualTo(new DurationStat(12.0, 24.0, 2));
        assertThat(api.latestCfr()).isEqualTo(100.0);
        assertThat(api.latestCfrEstimated()).isTrue();

        ProjectRepositoryOutput web = repos.get(1);
        assertThat(web.openPrCount()).isZero();
        assertThat(web.prCycleTime()).isEqualTo(new DurationStat(12.0, 12.0, 1));
        assertThat(web.review().reviewedWithinSlaPercent()).isEqualTo(100.0);
        assertThat(web.delivery().changeFailRatePercent()).isEqualTo(0.0);
        assertThat(web.delivery().deploymentsPerWeek()).isEqualTo(0.5);
    }

    @Test
    void should_listPullRequests_withLoginAndLinkResolvedAtReadTime() {
        List<RepositoryPullRequestOutput> page = new ListRepositoryPullRequestsUseCase(read, store.pullRequestRepository,
                workItemRepository).execute(R1.toHexString(), null, null, 0, 20).content();

        assertThat(page).extracting(RepositoryPullRequestOutput::number).containsExactly(6, 5, 4, 3, 2, 1);
        RepositoryPullRequestOutput p1 = page.get(5);
        assertThat(p1.authorLogin()).isEqualTo("alice");
        assertThat(p1.authorUserId()).isEqualTo("u-alice");
        assertThat(p1.linkedWorkItemExternalId()).isEqualTo("501");
        assertThat(p1.linkedWorkItemId()).isEqualTo("wi-501");
        assertThat(p1.timeToFirstApprovalHours()).isEqualTo(4.0);
        assertThat(page.get(2).draft()).isTrue();
        assertThat(new ListRepositoryPullRequestsUseCase(read, store.pullRequestRepository, workItemRepository)
                .execute(R1.toHexString(), null, "bob", 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    void should_serveDeliverySeriesAndCommitActivity_asBefore() {
        List<DeliveryMetricPointOutput> series = new GetRepositoryDeliveryMetricsUseCase(read, deliveryMetricsService)
                .execute(R1.toHexString(), T, T.plus(Duration.ofDays(14)));
        assertThat(series).hasSize(2);
        assertThat(series.get(0).mergedPrs()).isEqualTo(2);
        assertThat(series.get(0).remediationPrs()).isEqualTo(2);
        assertThat(series.get(0).changeFailRatePercent()).isEqualTo(100.0);
        assertThat(series.get(0).deploymentsPerWeek()).isEqualTo(2.0);
        assertThat(series.get(1).mergedPrs()).isZero();
        assertThat(series.get(1).changeFailRatePercent()).isNull();

        List<CommitActivityPointOutput> activity = new GetRepositoryCommitActivityUseCase(read, store.commitRepository)
                .execute(R1.toHexString(), T, T.plus(Duration.ofDays(4)));
        assertThat(activity).extracting(CommitActivityPointOutput::commitCount).containsExactly(2L, 0L, 1L, 0L);
    }

    @Test
    void should_feedTheDeliveryCalculator_theSameMergedChanges() {
        List<MergedChange> changes = deliveryMetricsService.mergedChangesOfProject(ORG, PROJECT,
                START.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), T.plus(Duration.ofDays(13)));

        assertThat(changes).extracting(MergedChange::title).containsExactlyInAnyOrder("P1", "P2", "P6", "Q1");
        MergedChange p1 = changes.stream().filter(c -> "P1".equals(c.title())).findFirst().orElseThrow();
        assertThat(p1.linkedToBug()).isTrue();
        assertThat(p1.productionBranch()).isEqualTo("main");
        assertThat(p1.leadTimeStart()).isEqualTo(T.minus(Duration.ofHours(2)));
        MergedChange p6 = changes.stream().filter(c -> "P6".equals(c.title())).findFirst().orElseThrow();
        assertThat(p6.isIntoProductionBranch()).isFalse();
    }

    @Test
    void should_notSeeOtherOrganizationsRepository() {
        assertThat(read.repository(ORG, R_OTHER_ORG.toHexString())).isEmpty();
        assertThat(read.repositoriesOfProject("org-x", PROJECT)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 3. Dashboards
    // ------------------------------------------------------------------

    @Test
    void should_produceDeveloperPullRequestContext_asBefore() {
        GetDeveloperDashboardUseCase developer = new GetDeveloperDashboardUseCase(sprintRepository, workItemRepository,
                aiUsageRepository, allocationRepository, organizationRepository, read, new EffortBearingLevelResolver());

        DeveloperDashboardOutput output = developer.execute(ORG, "u-alice", PROJECT, null, START, END, null);

        assertThat(output.openPrs()).isZero();
        assertThat(output.mergedPrs()).isEqualTo(3);
        var context = output.sprintContext();
        assertThat(context.reviewedPrs()).isEqualTo(3);
        assertThat(context.avgTimeToFirstReviewHours()).isEqualTo(8.0 / 3);
        assertThat(context.review().timeToFirstReview()).isEqualTo(new DurationStat(2.0, 5.0, 3));
        assertThat(context.review().timeToFirstApproval()).isEqualTo(new DurationStat(4.0, 8.0, 3));
        assertThat(context.prCycleTime()).isEqualTo(new DurationStat(12.0, 24.0, 3));
        assertThat(context.pullRequests()).allSatisfy(pr -> {
            assertThat(pr.authorLogin()).isEqualTo("alice");
            assertThat(pr.authorGithubLogin()).isEqualTo("alice");
            assertThat(pr.provider()).isEqualTo("GITHUB");
        });
    }

    // ------------------------------------------------------------------
    // Pre-refactor dataset, migrated through V__vcs_core
    // ------------------------------------------------------------------

    private void seedPreRefactorDataAndMigrate() {
        MappingMongoConverter converter = converter();
        VcsDocumentMapper mapper = new VcsDocumentMapper();

        List<Document> githubRepos = List.of(
                githubRepo(R1, ORG, "1001", "acme/api"),
                githubRepo(R2, ORG, "1002", "acme/web"),
                githubRepo(R_OTHER_ORG, "org-x", "1001", "acme/api"));
        githubRepos.forEach(doc -> store.repositoryRepository.save(mapper.toDomain(
                converter.read(VcsRepositoryDocument.class, VcsCoreMigrationMappings.repository(doc)))));

        migrateBinding(converter, mapper, "acme/api", R1.toHexString(), true);
        migrateBinding(converter, mapper, "acme/web", R2.toHexString(), true);
        migrateBinding(converter, mapper, "acme/api", R1.toHexString(), false);

        String r1 = R1.toHexString();
        pr(converter, mapper, "P1", 1, r1, "alice", "u-alice", h(0), h(10), h(10), "main", "feature/a", false, "501", h(-2),
                10.0, 2.0, 4.0,
                review("bob", "COMMENTED", h(2), null), review("carol", "APPROVED", h(4), null));
        pr(converter, mapper, "P2", 2, r1, "alice", "u-alice", h(1), h(25), h(25), "main", "hotfix/x", false, null, null,
                24.0, 5.0, 8.0,
                review("dependabot[bot]", "COMMENTED", h(1.5), null), review("alice", "COMMENTED", h(2), null),
                review("dave", "DISMISSED", h(6), null), review("erin", "APPROVED", h(9), null));
        pr(converter, mapper, "P3", 3, r1, "bob", null, h(2), null, null, "main", "feature/c", false, null, null,
                null, null, null);
        pr(converter, mapper, "P4", 4, r1, "bob", null, h(3), null, null, "main", "feature/d", true, null, null,
                null, null, null);
        pr(converter, mapper, "P5", 5, r1, "carol", null, h(4), null, h(6), "main", "feature/e", false, null, null,
                2.0, null, null,
                review("bob", "PENDING", null, null));
        pr(converter, mapper, "P6", 6, r1, "erin", null, h(5), h(8), h(8), "develop", "feature/f", false, null, null,
                3.0, 2.0, null,
                review("ci-helper", "APPROVED", h(5.5), true), review("bob", "CHANGES_REQUESTED", h(7), false));
        pr(converter, mapper, "Q1", 1, R2.toHexString(), "alice", "u-alice", h(6), h(18), h(18), "main", "feature/q",
                false, null, null, 12.0, 1.0, 1.0,
                review("frank", "APPROVED", h(7), null));

        for (Instant authoredAt : List.of(h(1), h(5), h(58), T.plus(Duration.ofDays(10)))) {
            Document commit = new Document("_id", new ObjectId()).append("organizationId", ORG).append("repoId", r1)
                    .append("sha", new ObjectId().toHexString()).append("authorGithubLogin", "alice")
                    .append("authoredAt", Date.from(authoredAt)).append("active", true);
            store.commitRepository.save(mapper.toDomain(
                    converter.read(VcsCommitDocument.class, VcsCoreMigrationMappings.commit(commit))));
        }
    }

    private void migrateBinding(MappingMongoConverter converter, VcsDocumentMapper mapper, String fullName,
                                String repositoryId, boolean active) {
        Document githubBinding = new Document("_id", new ObjectId()).append("organizationId", ORG)
                .append("projectId", active ? PROJECT : "proj-old").append("repoId", fullName)
                .append("syncEnabled", active).append("lastSyncAt", Date.from(T)).append("active", active);
        store.bindingRepository.save(mapper.toDomain(converter.read(VcsRepoBindingDocument.class,
                VcsCoreMigrationMappings.binding(githubBinding, repositoryId, false))));
    }

    @SuppressWarnings("java:S107")
    private void pr(MappingMongoConverter converter, VcsDocumentMapper mapper, String title, int number, String repoId,
                    String author, String authorUserId, Instant createdAt, Instant mergedAt, Instant closedAt,
                    String baseBranch, String sourceBranch, boolean draft, String linkedWorkItem, Instant earliestCommitAt,
                    Double cycle, Double ttfr, Double ttfa, Document... reviews) {
        ObjectId id = new ObjectId();
        prIdByName.put(title, id.toHexString());
        stored.put(title, Arrays.asList(cycle, ttfr, ttfa));
        Document githubPr = new Document("_id", id).append("organizationId", ORG).append("repoId", repoId)
                .append("externalId", "ext-" + title).append("number", number).append("title", title)
                .append("status", mergedAt != null ? "MERGED" : closedAt != null ? "CLOSED" : "OPEN")
                .append("authorGithubLogin", author).append("createdAt", Date.from(createdAt))
                .append("draft", draft).append("baseBranch", baseBranch).append("sourceBranch", sourceBranch)
                .append("active", true);
        putIfPresent(githubPr, "authorUserId", authorUserId);
        putIfPresent(githubPr, "mergedAt", mergedAt == null ? null : Date.from(mergedAt));
        putIfPresent(githubPr, "closedAt", closedAt == null ? null : Date.from(closedAt));
        putIfPresent(githubPr, "linkedWorkItemExternalId", linkedWorkItem);
        putIfPresent(githubPr, "earliestCommitAt", earliestCommitAt == null ? null : Date.from(earliestCommitAt));
        putIfPresent(githubPr, "cycleTimeHours", cycle);
        putIfPresent(githubPr, "timeToFirstReviewHours", ttfr);
        putIfPresent(githubPr, "timeToFirstApprovalHours", ttfa);
        store.pullRequestRepository.save(mapper.toDomain(
                converter.read(VcsPullRequestDocument.class, VcsCoreMigrationMappings.pullRequest(githubPr))));

        for (Document review : reviews) {
            review.append("organizationId", ORG).append("pullRequestId", id.toHexString());
            VcsCoreMigrationMappings.reviewEvent(review, repoId).ifPresent(event -> store.reviewEventRepository.save(
                    mapper.toDomain(converter.read(VcsReviewEventDocument.class, event))));
        }
    }

    private static Document review(String login, String result, Instant submittedAt, Boolean flaggedBot) {
        Document review = new Document("_id", new ObjectId()).append("externalId", new ObjectId().toHexString())
                .append("reviewerGithubLogin", login).append("result", result).append("active", true);
        putIfPresent(review, "submittedAt", submittedAt == null ? null : Date.from(submittedAt));
        putIfPresent(review, "reviewerIsBot", flaggedBot);
        return review;
    }

    private static Document githubRepo(ObjectId id, String org, String externalId, String fullName) {
        return new Document("_id", id).append("organizationId", org).append("externalId", externalId)
                .append("owner", "acme").append("name", fullName.substring(5)).append("fullName", fullName)
                .append("defaultBranch", "main").append("active", true).append("createdAt", Date.from(T));
    }

    private Map<String, List<Double>> metricsByName() {
        Map<String, List<Double>> metrics = new LinkedHashMap<>();
        prIdByName.forEach((name, id) -> {
            VcsPullRequest pr = store.pullRequestRepository.findByIdAndOrganizationId(id, ORG).orElseThrow();
            metrics.put(name, Arrays.asList(pr.getCycleTimeHours(), pr.getTimeToFirstReviewHours(),
                    pr.getTimeToFirstApprovalHours()));
        });
        return metrics;
    }

    private void clearMetrics() {
        store.pullRequests.forEach(pr -> {
            pr.setCycleTimeHours(null);
            pr.setTimeToFirstReviewHours(null);
            pr.setTimeToFirstApprovalHours(null);
        });
    }

    private static Instant h(double hours) {
        return T.plus(Duration.ofMinutes(Math.round(hours * 60)));
    }

    private static void putIfPresent(Document document, String field, Object value) {
        if (value != null) document.append(field, value);
    }

    private static MappingMongoConverter converter() {
        MongoCustomConversions conversions = new MongoCustomConversions(List.of());
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }
}
