package andromeda.sei.ms.feature.workitem.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("@DataMongoTest removed in Spring Boot 4 â€” needs rewrite as @SpringBootTest slice")
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

    private WorkItem workItem(String externalId) {
        return new WorkItem(null, ORG_ID, PROJECT_ID, WorkItem.Source.AZURE_DEVOPS,
                externalId, NOW, "Title", "Bug", WorkItem.ResolvedType.BUG,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "2", null, List.of(), null, null, null, null, null,
                NOW, null, null, null, null, null, true, null, NOW, null, null, null, null, null, null, null, null);
    }
}
