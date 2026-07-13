package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.azure.domain.AzureProjectBinding;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies (task 7.1) that a persisted {@code StatusMappingDocument} lacking {@code workItemType}
 * -- the shape of any document written before per-work-item-type status mapping was introduced --
 * deserializes with the field null rather than failing, preserving the legacy-compatibility
 * fallback relied on by {@code SyncAzureWorkItemsUseCase.resolveStatus}.
 */
class AzureProjectBindingDocumentMapperTest {

    private final AzureProjectBindingDocumentMapper mapper = new AzureProjectBindingDocumentMapper();
    private static final Instant NOW = Instant.now();

    @Test
    void toDomain_shouldDeserializeLegacyStatusMappingWithNullWorkItemType() {
        // Simulates a pre-existing Mongo document written before the workItemType field existed:
        // built here via the no-arg constructor + setter, leaving workItemType unset (null),
        // exactly how Spring Data Mongo would populate it from a document missing that key.
        AzureProjectBindingDocument.StatusMappingDocument legacyStatusMapping =
                new AzureProjectBindingDocument.StatusMappingDocument();
        legacyStatusMapping.setRawStatus("Active");
        legacyStatusMapping.setResolvedStatus(WorkItem.ResolvedStatus.IN_PROGRESS);

        AzureProjectBindingDocument.WorkflowConfigDocument workflowConfigDoc =
                AzureProjectBindingDocument.WorkflowConfigDocument.builder()
                        .statusMappings(List.of(legacyStatusMapping))
                        .typeMappings(List.of())
                        .fieldMappings(List.of())
                        .configSource(WorkflowConfig.ConfigSource.CUSTOM.name())
                        .hierarchyLevels(List.of())
                        .build();

        AzureProjectBindingDocument doc = AzureProjectBindingDocument.builder()
                .id("bind-1")
                .organizationId("org-1")
                .projectId("proj-1")
                .azureProjectId("azure-1")
                .azureProjectName("Azure Proj")
                .syncEnabled(true)
                .workflowConfig(workflowConfigDoc)
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        AzureProjectBinding binding = mapper.toDomain(doc);

        assertThat(binding.getWorkflowConfig().getStatusMappings()).hasSize(1);
        assertThat(binding.getWorkflowConfig().getStatusMappings().get(0).getWorkItemType()).isNull();
        assertThat(binding.getWorkflowConfig().getStatusMappings().get(0).getRawStatus()).isEqualTo("Active");
    }
}
