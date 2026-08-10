package andromeda.sei.ms.feature.azure.domain;

import java.util.List;
import java.util.Optional;

public class WorkflowConfig {

    public enum ConfigSource { DEFAULT, CUSTOM }

    public static final String DEFAULT_EFFORT_FIELD_REFERENCE_NAME = "Microsoft.VSTS.Scheduling.StoryPoints";
    public static final String DEFAULT_EFFORT_FIELD_TYPE = "double";

    private List<StatusMapping> statusMappings;
    private List<TypeMapping> typeMappings;
    private List<FieldMapping> fieldMappings;
    private WiqlFilter syncFilter;
    private ConfigSource configSource;
    private List<HierarchyLevel> hierarchyLevels;

    public WorkflowConfig(List<StatusMapping> statusMappings, List<TypeMapping> typeMappings,
                           List<FieldMapping> fieldMappings, WiqlFilter syncFilter,
                           ConfigSource configSource) {
        this(statusMappings, typeMappings, fieldMappings, syncFilter, configSource, List.of());
    }

    public WorkflowConfig(List<StatusMapping> statusMappings, List<TypeMapping> typeMappings,
                           List<FieldMapping> fieldMappings, WiqlFilter syncFilter,
                           ConfigSource configSource, List<HierarchyLevel> hierarchyLevels) {
        this.statusMappings = statusMappings;
        this.typeMappings = typeMappings;
        this.fieldMappings = fieldMappings;
        this.syncFilter = syncFilter;
        this.configSource = configSource;
        this.hierarchyLevels = hierarchyLevels == null ? List.of() : hierarchyLevels;
    }

    public List<StatusMapping> getStatusMappings() { return statusMappings; }
    public void setStatusMappings(List<StatusMapping> statusMappings) { this.statusMappings = statusMappings; }

    public List<TypeMapping> getTypeMappings() { return typeMappings; }
    public void setTypeMappings(List<TypeMapping> typeMappings) { this.typeMappings = typeMappings; }

    public List<FieldMapping> getFieldMappings() { return fieldMappings; }
    public void setFieldMappings(List<FieldMapping> fieldMappings) { this.fieldMappings = fieldMappings; }

    public WiqlFilter getSyncFilter() { return syncFilter; }
    public void setSyncFilter(WiqlFilter syncFilter) { this.syncFilter = syncFilter; }

    public ConfigSource getConfigSource() { return configSource; }
    public void setConfigSource(ConfigSource configSource) { this.configSource = configSource; }

    public List<HierarchyLevel> getHierarchyLevels() { return hierarchyLevels; }
    public void setHierarchyLevels(List<HierarchyLevel> hierarchyLevels) { this.hierarchyLevels = hierarchyLevels == null ? List.of() : hierarchyLevels; }

    /**
     * Resolves the effective field mapping for the given internal field target, matching only
     * wildcard entries ({@code workItemType == null}) — preserves the exact behavior of this
     * method before per-work-item-type field mapping existed. Delegates to
     * {@link #resolveFieldMapping(FieldMapping.InternalFieldTarget, String)} with a null rawType.
     */
    public Optional<FieldMapping> resolveFieldMapping(FieldMapping.InternalFieldTarget target) {
        return resolveFieldMapping(target, null);
    }

    /**
     * Resolves the effective field mapping for the given internal field target and raw Azure work
     * item type. Resolution order:
     * <ol>
     *   <li>Exact match on (target, rawType), case/whitespace-insensitive.</li>
     *   <li>Wildcard match: an entry for target whose {@code workItemType} is {@code null}
     *       (legacy-compatibility sentinel meaning "applies to all work item types").</li>
     *   <li>Implicit default EFFORT-to-StoryPoints mapping when this config is DEFAULT
     *       (unconfigured) and no explicit/wildcard mapping exists. CUSTOM configs never receive
     *       an implicit default.</li>
     *   <li>Empty.</li>
     * </ol>
     */
    public Optional<FieldMapping> resolveFieldMapping(FieldMapping.InternalFieldTarget target, String rawType) {
        if (fieldMappings != null) {
            if (rawType != null) {
                Optional<FieldMapping> exact = fieldMappings.stream()
                        .filter(f -> f.getInternalField() == target)
                        .filter(f -> f.getWorkItemType() != null && normalize(f.getWorkItemType()).equals(normalize(rawType)))
                        .findFirst();
                if (exact.isPresent()) return exact;
            }

            Optional<FieldMapping> wildcard = fieldMappings.stream()
                    .filter(f -> f.getInternalField() == target)
                    .filter(f -> f.getWorkItemType() == null)
                    .findFirst();
            if (wildcard.isPresent()) return wildcard;
        }

        if (configSource == ConfigSource.DEFAULT && target == FieldMapping.InternalFieldTarget.EFFORT) {
            return Optional.of(new FieldMapping(
                    FieldMapping.InternalFieldTarget.EFFORT,
                    null,
                    DEFAULT_EFFORT_FIELD_REFERENCE_NAME,
                    DEFAULT_EFFORT_FIELD_TYPE));
        }
        return Optional.empty();
    }

    // Matches the normalization idiom used elsewhere for Azure raw type/status comparisons (see
    // SyncAzureWorkItemsUseCase.resolveStatus, WorkItemHierarchyClassifier.normalize).
    private String normalize(String rawType) {
        return rawType == null ? null : rawType.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
