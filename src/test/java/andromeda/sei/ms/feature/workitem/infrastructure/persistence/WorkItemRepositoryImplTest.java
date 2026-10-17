package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.shared.testsupport.MongoIntegrationTest;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongoIntegrationTest
class WorkItemRepositoryImplTest {

    @Autowired
    private WorkItemMongoRepository mongoRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    private WorkItemRepositoryImpl repository;

    private static final String ORG_ID = "org-1";
    private static final String PROJECT_ID = "proj-1";
    private static final Instant NOW = Instant.now();

    @BeforeEach
    void setUp() {
        repository = new WorkItemRepositoryImpl(mongoRepository, new WorkItemDocumentMapper(), mongoTemplate);
        mongoRepository.deleteAll();
    }

    @Test
    void save_shouldUpsertByCompositeKey() {
        WorkItem item = workItem("ext-1");
        repository.save(item);
        repository.save(item);

        assertThat(mongoRepository.findAll()).hasSize(1);
    }

    @Test
    void save_shouldReturnWithId() {
        WorkItem saved = repository.save(workItem("ext-1"));
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findByOrganizationIdAndExternalIdAndSource_shouldReturnExisting() {
        repository.save(workItem("ext-1"));

        var result = repository.findByOrganizationIdAndExternalIdAndSource(
                ORG_ID, "ext-1", WorkItem.Source.AZURE_DEVOPS);

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("ext-1");
    }

    @Test
    void findAllByOrganizationIdAndProjectId_shouldReturnItemsForProject() {
        repository.save(workItem("ext-1"));
        repository.save(workItem("ext-2"));

        List<WorkItem> results = repository.findAllByOrganizationIdAndProjectId(ORG_ID, PROJECT_ID);

        assertThat(results).hasSize(2);
    }

    @Test
    void save_shouldPersistParentExternalId() {
        // Regression test: save() previously omitted parentExternalId from its Update, so it was
        // silently dropped on every upsert — the backlog tree never populated even when
        // SyncAzureWorkItemsUseCase correctly set the field in memory before calling save().
        WorkItem item = workItem("ext-1", "parent-ext-1");

        WorkItem saved = repository.save(item);

        assertThat(saved.getParentExternalId()).isEqualTo("parent-ext-1");
        var reloaded = repository.findByOrganizationIdAndExternalIdAndSource(
                ORG_ID, "ext-1", WorkItem.Source.AZURE_DEVOPS);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getParentExternalId()).isEqualTo("parent-ext-1");
    }

    @Test
    void save_shouldUpdateParentExternalIdOnResync() {
        WorkItem item = workItem("ext-1", null);
        repository.save(item);

        WorkItem reSynced = workItem("ext-1", "parent-ext-2");
        repository.save(reSynced);

        var reloaded = repository.findByOrganizationIdAndExternalIdAndSource(
                ORG_ID, "ext-1", WorkItem.Source.AZURE_DEVOPS);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getParentExternalId()).isEqualTo("parent-ext-2");
    }

    private WorkItem workItem(String externalId) {
        return workItem(externalId, null);
    }

    private WorkItem workItem(String externalId, String parentExternalId) {
        return new WorkItem(null, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "2", null, List.of(), null, null, null, null, parentExternalId,
                NOW, null, null, null, null, null, true, null, NOW, null, null, null, null, null, null, null, null);
    }
}
