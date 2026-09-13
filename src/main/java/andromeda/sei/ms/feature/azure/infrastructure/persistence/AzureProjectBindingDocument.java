package andromeda.sei.ms.feature.azure.infrastructure.persistence;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "azure_project_bindings")
@CompoundIndexes({
        @CompoundIndex(def = "{'organizationId': 1, 'projectId': 1, 'active': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AzureProjectBindingDocument {

    @Id
    private String id;

    private String organizationId;
    private String projectId;
    private String azureProjectId;
    private String azureProjectName;
    private boolean syncEnabled;
    private Instant lastSyncAt;
    /** Marcador de leitura exclusivo do sync de work items; ver AzureProjectBinding. */
    private Instant workItemsWatermark;
    private WorkflowConfigDocument workflowConfig;
    private boolean active = true;
    private Instant createdAt;
    private Instant updatedAt;

    @Version
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowConfigDocument {
        private List<StatusMappingDocument> statusMappings;
        private List<TypeMappingDocument> typeMappings;
        private List<FieldMappingDocument> fieldMappings;
        private WiqlFilterDocument syncFilter;
        private String configSource;
        private List<HierarchyLevelDocument> hierarchyLevels;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusMappingDocument {
        /**
         * Raw Azure work item type this status mapping applies to. {@code null} is the
         * legacy-compatibility sentinel meaning "applies to all types" (documents persisted
         * before status mapping became per-work-item-type).
         */
        private String workItemType;
        private String rawStatus;
        private WorkItem.ResolvedStatus resolvedStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TypeMappingDocument {
        private String rawType;
        private WorkItem.ResolvedType resolvedType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldMappingDocument {
        private String internalField;
        /**
         * Raw Azure work item type this field mapping applies to. {@code null} is the
         * legacy-compatibility sentinel meaning "applies to all work item types" (documents
         * persisted before field mapping became per-work-item-type).
         */
        private String workItemType;
        private String azureFieldReferenceName;
        private String azureFieldType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WiqlFilterDocument {
        private List<String> workItemTypes;
        private String areaPath;
        private String iterationPath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HierarchyLevelDocument {
        private String rawType;
        private Integer rank;
    }
}
