package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** azure-itens-excluidos 2.2: consultas de remocao e reativacao do WorkItemRepository contra Mongo real. */
@MongoIntegrationTest
class WorkItemRemovalQueriesIT {

    private static final String ORG = "org-1";
    private static final String PROJ = "proj-A";
    private static final Instant AT = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired private WorkItemMongoRepository mongoRepository;
    @Autowired private MongoTemplate mongoTemplate;

    private WorkItemRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new WorkItemRepositoryImpl(mongoRepository, new WorkItemDocumentMapper(), mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void findActiveExternalIds_shouldBeScopedByOrgProjectSourceAndActive() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", true);
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "2", false);
        seed(ORG, "proj-B", WorkItem.Source.AZURE_DEVOPS, "3", true);
        seed("org-2", PROJ, WorkItem.Source.AZURE_DEVOPS, "4", true);
        seed(ORG, PROJ, WorkItem.Source.JIRA, "5", true);

        Set<String> ids = repository.findActiveExternalIdsByOrganizationIdAndProjectIdAndSource(
                ORG, PROJ, WorkItem.Source.AZURE_DEVOPS);

        assertThat(ids).containsExactly("1");
    }

    @Test
    void findInactiveExternalIds_shouldIgnoreProjectButNotOrgSourceOrActive() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", false);
        seed(ORG, "proj-B", WorkItem.Source.AZURE_DEVOPS, "2", false);
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "3", true);
        seed("org-2", PROJ, WorkItem.Source.AZURE_DEVOPS, "4", false);
        seed(ORG, PROJ, WorkItem.Source.JIRA, "5", false);
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "6", false);

        Set<String> ids = repository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                ORG, WorkItem.Source.AZURE_DEVOPS, List.of("1", "2", "3", "4", "5", "99"));

        assertThat(ids).containsExactlyInAnyOrder("1", "2");
    }

    @Test
    void findInactiveExternalIds_shouldHandleMoreThanOneBatch() {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 1500; i++) {
            seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "x" + i, false);
            ids.add("x" + i);
        }

        assertThat(repository.findInactiveExternalIdsByOrganizationIdAndSourceAndExternalIdIn(
                ORG, WorkItem.Source.AZURE_DEVOPS, ids)).hasSize(1500);
    }

    @Test
    void deactivate_shouldTouchOnlyActiveItemsOfOrgProjectAndSource_andBumpVersion() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", true);
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "2", true);
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "3", false);
        seed(ORG, "proj-B", WorkItem.Source.AZURE_DEVOPS, "1b", true);
        seed("org-2", PROJ, WorkItem.Source.AZURE_DEVOPS, "1c", true);
        seed(ORG, PROJ, WorkItem.Source.JIRA, "1d", true);
        Long versionBefore = find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1").getVersion();

        long changed = repository.deactivateByExternalIds(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS,
                List.of("1", "3", "1b", "1c", "1d"), WorkItem.DeactivationReason.DELETED_IN_AZURE, AT);

        assertThat(changed).isEqualTo(1);
        WorkItemDocument one = find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1");
        assertThat(one.isActive()).isFalse();
        assertThat(one.getDeactivatedAt()).isEqualTo(AT);
        assertThat(one.getDeactivationReason()).isEqualTo(WorkItem.DeactivationReason.DELETED_IN_AZURE);
        assertThat(one.getUpdatedAt()).isEqualTo(AT);
        assertThat(one.getVersion()).isEqualTo(versionBefore + 1);
        assertThat(find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "2").isActive()).isTrue();
        assertThat(find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "3").getDeactivationReason()).isNull();
        assertThat(find(ORG, "proj-B", WorkItem.Source.AZURE_DEVOPS, "1b").isActive()).isTrue();
        assertThat(find("org-2", PROJ, WorkItem.Source.AZURE_DEVOPS, "1c").isActive()).isTrue();
        assertThat(find(ORG, PROJ, WorkItem.Source.JIRA, "1d").isActive()).isTrue();
    }

    @Test
    void deactivate_shouldBeIdempotent() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", true);
        Long versionBefore = find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1").getVersion();

        assertThat(repository.deactivateByExternalIds(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, List.of("1"),
                WorkItem.DeactivationReason.LEFT_BINDING, AT)).isEqualTo(1);
        assertThat(repository.deactivateByExternalIds(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, List.of("1"),
                WorkItem.DeactivationReason.UNKNOWN, AT.plusSeconds(60))).isZero();

        WorkItemDocument one = find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1");
        assertThat(one.getDeactivationReason()).isEqualTo(WorkItem.DeactivationReason.LEFT_BINDING);
        assertThat(one.getVersion()).isEqualTo(versionBefore + 1);
    }

    @Test
    void deactivate_withNoIds_shouldDoNothing() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", true);
        assertThat(repository.deactivateByExternalIds(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, List.of(),
                WorkItem.DeactivationReason.UNKNOWN, AT)).isZero();
    }

    @Test
    void save_shouldReactivateAndClearDeactivationFields() {
        seed(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1", true);
        repository.deactivateByExternalIds(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, List.of("1"),
                WorkItem.DeactivationReason.DELETED_IN_AZURE, AT);
        WorkItem item = repository.findByOrganizationIdAndExternalIdAndSource(ORG, "1", WorkItem.Source.AZURE_DEVOPS)
                .orElseThrow();
        assertThat(item.getDeactivationReason()).isEqualTo(WorkItem.DeactivationReason.DELETED_IN_AZURE);

        item.setActive(true);
        item.setDeactivatedAt(null);
        item.setDeactivationReason(null);
        repository.save(item);

        WorkItemDocument doc = find(ORG, PROJ, WorkItem.Source.AZURE_DEVOPS, "1");
        assertThat(doc.isActive()).isTrue();
        assertThat(doc.getDeactivatedAt()).isNull();
        assertThat(doc.getDeactivationReason()).isNull();
    }

    private WorkItemDocument find(String org, String project, WorkItem.Source source, String externalId) {
        return mongoRepository.findAll().stream()
                .filter(d -> org.equals(d.getOrganizationId()) && project.equals(d.getProjectId())
                        && source == d.getSource() && externalId.equals(d.getExternalId()))
                .findFirst().orElseThrow();
    }

    private void seed(String org, String project, WorkItem.Source source, String externalId, boolean active) {
        mongoRepository.save(WorkItemDocument.builder().organizationId(org).projectId(project).source(source)
                .externalId(externalId).title("t" + externalId).active(active).createdAt(AT).updatedAt(AT).build());
    }
}
