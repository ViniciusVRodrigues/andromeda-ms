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
     * Resolves the effective field mapping for the given internal field target, applying the
     * implicit default EFFORT-to-StoryPoints mapping when this config is DEFAULT (unconfigured)
     * and no explicit mapping exists. CUSTOM configs never receive an implicit default.
     */
    public Optional<FieldMapping> resolveFieldMapping(FieldMapping.InternalFieldTarget target) {
        Optional<FieldMapping> explicit = fieldMappings == null ? Optional.empty() :
                fieldMappings.stream().filter(f -> f.getInternalField() == target).findFirst();
        if (explicit.isPresent()) return explicit;

        if (configSource == ConfigSource.DEFAULT && target == FieldMapping.InternalFieldTarget.EFFORT) {
            return Optional.of(new FieldMapping(
                    FieldMapping.InternalFieldTarget.EFFORT,
                    DEFAULT_EFFORT_FIELD_REFERENCE_NAME,
                    DEFAULT_EFFORT_FIELD_TYPE));
        }
        return Optional.empty();
    }
}
