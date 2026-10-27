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

    /**
     * O watermark do sync de work items e um campo proprio, separado de {@code lastSyncAt}:
     * aquele acumulava marcador de leitura e "quando sincronizou" da UI, e o sync de sprints
     * — que roda antes — o carimbava, fazendo o sync de work items nascer incremental e
     * importar so o que mudou no dia. Os dois precisam sobreviver ao round-trip sem se
     * misturar.
     */
    @Test
    void shouldRoundTripWorkItemsWatermarkSeparatelyFromLastSyncAt() {
        Instant lastSync = NOW;
        Instant watermark = NOW.minusSeconds(3600);

        AzureProjectBinding binding = new AzureProjectBinding(
                "bind-1", "org-1", "proj-1", "azure-1", "Azure Proj",
                true, lastSync, null, true, NOW, NOW, 1L);
        binding.setWorkItemsWatermark(watermark);

        AzureProjectBindingDocument doc = mapper.toDocument(binding);
        assertThat(doc.getLastSyncAt()).isEqualTo(lastSync);
        assertThat(doc.getWorkItemsWatermark()).isEqualTo(watermark);

        AzureProjectBinding back = mapper.toDomain(doc);
        assertThat(back.getLastSyncAt()).isEqualTo(lastSync);
        assertThat(back.getWorkItemsWatermark()).isEqualTo(watermark);
    }

    /**
     * Documento gravado antes do campo existir: a chave simplesmente falta, o Spring Data
     * devolve null e isso e exatamente o estado desejado — proximo sync roda FULL. E a razao
     * de nao existir migration para este campo.
     */
    @Test
    void toDomain_shouldLeaveWatermarkNullForDocumentWrittenBeforeTheFieldExisted() {
        AzureProjectBindingDocument legacyDoc = AzureProjectBindingDocument.builder()
                .id("bind-1")
                .organizationId("org-1")
                .projectId("proj-1")
                .azureProjectId("azure-1")
                .syncEnabled(true)
                .lastSyncAt(NOW)
                .active(true)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        AzureProjectBinding binding = mapper.toDomain(legacyDoc);

        assertThat(binding.getLastSyncAt()).isEqualTo(NOW);
        assertThat(binding.getWorkItemsWatermark()).isNull();
    }
    @Test
    void shouldReadNullReceivesAiAsFalseAndRoundTripTrue() {
        var legacy = new AzureProjectBindingDocument.TypeMappingDocument();
        legacy.setRawType("Bug");
        legacy.setResolvedType(WorkItem.ResolvedType.BUG);
        var withAi = new AzureProjectBindingDocument.TypeMappingDocument("Task", WorkItem.ResolvedType.TASK, true);
        AzureProjectBindingDocument doc = AzureProjectBindingDocument.builder()
                .id("bind-1").organizationId("org-1").projectId("proj-1").azureProjectId("azure-1")
                .azureProjectName("Azure Proj").syncEnabled(true)
                .workflowConfig(AzureProjectBindingDocument.WorkflowConfigDocument.builder()
                        .statusMappings(List.of()).typeMappings(List.of(legacy, withAi))
                        .fieldMappings(List.of()).hierarchyLevels(List.of())
                        .configSource(WorkflowConfig.ConfigSource.CUSTOM.name()).build())
                .active(true).createdAt(NOW).updatedAt(NOW).build();

        AzureProjectBinding binding = mapper.toDomain(doc);

        var types = binding.getWorkflowConfig().getTypeMappings();
        assertThat(types.get(0).isReceivesAi()).isFalse();
        assertThat(types.get(1).isReceivesAi()).isTrue();

        var back = mapper.toDocument(binding).getWorkflowConfig().getTypeMappings();
        assertThat(back.get(0).getReceivesAi()).isFalse();
        assertThat(back.get(1).getReceivesAi()).isTrue();
    }
}
