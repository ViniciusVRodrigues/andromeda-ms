package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItemActivity;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class WorkItemActivityRepositoryImplTest {

    @Autowired private WorkItemActivityMongoRepository mongoRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private WorkItemActivityRepositoryImpl repository;

    private static final LocalDate DAY = LocalDate.of(2026, 9, 29);

    @BeforeEach
    void setUp() {
        repository = new WorkItemActivityRepositoryImpl(mongoRepository, new WorkItemActivityDocumentMapper(),
                mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void upsert_isIdempotentByOrgWorkItemAndRevision() {
        repository.upsert(activity("org-1", "wi-1", 1, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-1", 1, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-1", 2, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-2", 1, "u-1", DAY));

        assertThat(mongoRepository.findAll()).hasSize(3);
    }

    @Test
    void upsert_storesAllFields() {
        WorkItemActivity saved = repository.upsert(activity("org-1", "wi-1", 4, "u-1", DAY));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRevision()).isEqualTo(4);
        assertThat(saved.getActorUserId()).isEqualTo("u-1");
        assertThat(saved.getActorUniqueName()).isEqualTo("nati@empresa.com");
        assertThat(saved.getDay()).isEqualTo(DAY);
        assertThat(saved.getChangedFields()).containsExactly("System.State", "System.History");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void maxRevisionByWorkItemId_returnsHighestOrEmpty() {
        assertThat(repository.maxRevisionByWorkItemId("org-1", "wi-1")).isEmpty();
        repository.upsert(activity("org-1", "wi-1", 3, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-1", 7, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-1", 5, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-2", 9, "u-1", DAY));

        assertThat(repository.maxRevisionByWorkItemId("org-1", "wi-1")).hasValue(7);
        assertThat(repository.maxRevisionByWorkItemId("org-2", "wi-1")).isEmpty();
    }

    @Test
    void findByOrganizationIdAndActorUserIdAndDay_filtersByOrgUserAndDay() {
        repository.upsert(activity("org-1", "wi-1", 1, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-2", 1, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-3", 1, "u-2", DAY));
        repository.upsert(activity("org-1", "wi-4", 1, "u-1", DAY.plusDays(1)));
        repository.upsert(activity("org-2", "wi-5", 1, "u-1", DAY));
        repository.upsert(activity("org-1", "wi-6", 1, null, DAY));

        List<WorkItemActivity> found = repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-1", DAY);

        assertThat(found).extracting(WorkItemActivity::getWorkItemId).containsExactlyInAnyOrder("wi-1", "wi-2");
    }

    @Test
    void assignOwnerToUnowned_matchesByAzureIdOrEmailIgnoringCase_andReturnsDistinctDays() {
        repository.upsert(activityOf("org-1", "wi-1", "az-9", "outro@x.com", null, DAY));
        repository.upsert(activityOf("org-1", "wi-2", "az-0", "ANA@Acme.com", null, DAY.plusDays(1)));
        repository.upsert(activityOf("org-1", "wi-3", "az-9", "x@x.com", null, DAY));
        repository.upsert(activityOf("org-1", "wi-4", "az-1", "ana@acme.com", "u-other", DAY.plusDays(2)));
        repository.upsert(activityOf("org-2", "wi-5", "az-9", "ana@acme.com", null, DAY.plusDays(3)));
        repository.upsert(activityOf("org-1", "wi-6", "az-5", "ana@acme.com.br", null, DAY.plusDays(4)));

        List<LocalDate> days = repository.assignOwnerToUnowned("org-1", "az-9", "ana@acme.com", "u-1");

        assertThat(days).containsExactlyInAnyOrder(DAY, DAY.plusDays(1));
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-1", DAY)).hasSize(2);
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-1", DAY.plusDays(1))).hasSize(1);
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-other", DAY.plusDays(2))).hasSize(1);
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-2", "u-1", DAY.plusDays(3))).isEmpty();
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-1", DAY.plusDays(4))).isEmpty();
    }

    @Test
    void assignOwnerToUnowned_ignoresInactive_andBlankKeys() {
        WorkItemActivity inactive = activityOf("org-1", "wi-1", "az-9", "ana@acme.com", null, DAY);
        repository.upsert(inactive);
        mongoTemplate.updateMulti(new org.springframework.data.mongodb.core.query.Query(),
                new org.springframework.data.mongodb.core.query.Update().set("active", false),
                WorkItemActivityDocument.class);
        repository.upsert(activityOf("org-1", "wi-2", null, null, null, DAY));
        repository.upsert(activityOf("org-1", "wi-3", "", "", null, DAY));

        assertThat(repository.assignOwnerToUnowned("org-1", "az-9", "ana@acme.com", "u-1")).isEmpty();
        assertThat(repository.assignOwnerToUnowned("org-1", null, "", "u-1")).isEmpty();
        assertThat(repository.assignOwnerToUnowned("org-1", " ", null, "u-1")).isEmpty();
        assertThat(repository.findByOrganizationIdAndActorUserIdAndDay("org-1", "u-1", DAY)).isEmpty();
    }

    private WorkItemActivity activityOf(String org, String workItemId, String actorExternalId, String uniqueName,
                                        String userId, LocalDate day) {
        return new WorkItemActivity(null, org, workItemId, "100", 1, actorExternalId, uniqueName, userId,
                day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC), day,
                List.of("System.State"), true, null, null, null);
    }

    private WorkItemActivity activity(String org, String workItemId, int revision, String userId, LocalDate day) {
        return new WorkItemActivity(null, org, workItemId, "100", revision, "az-1", "nati@empresa.com", userId,
                day.atStartOfDay().toInstant(java.time.ZoneOffset.UTC), day,
                List.of("System.State", "System.History"), true, null, null, null);
    }
}
