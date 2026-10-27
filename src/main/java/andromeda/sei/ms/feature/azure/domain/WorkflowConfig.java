package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;
import java.util.Optional;

public class WorkflowConfig {

    public enum ConfigSource { DEFAULT, CUSTOM }

    /** Default COMPLEXITY field of an unconfigured binding: the story points of Story-level types. */
    public static final String DEFAULT_COMPLEXITY_FIELD_REFERENCE_NAME = "Microsoft.VSTS.Scheduling.StoryPoints";
    /** Default EFFORT field of an unconfigured binding: the hours logged on Task-level types. */
    public static final String DEFAULT_EFFORT_FIELD_REFERENCE_NAME = "Microsoft.VSTS.Scheduling.CompletedWork";
    public static final String DEFAULT_FIELD_TYPE = "double";

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
     *   <li>Implicit default when this config is DEFAULT (unconfigured) and no explicit/wildcard
     *       mapping exists (complexity-effort D5): COMPLEXITY to StoryPoints on Story-level types
     *       (STORY, FEATURE, BUG) and EFFORT to CompletedWork on Task-level types (TASK, SUB_TASK).
     *       The level comes from the type mapping, with an unmapped type taken as TASK like the sync
     *       does; without a {@code rawType} the target's default applies. CUSTOM configs never receive
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

        if (configSource == ConfigSource.DEFAULT) return implicitDefault(target, rawType);
        return Optional.empty();
    }

    private Optional<FieldMapping> implicitDefault(FieldMapping.InternalFieldTarget target, String rawType) {
        WorkItem.ResolvedType level = rawType == null ? null : resolvedTypeOf(rawType);
        boolean storyLevel = level == WorkItem.ResolvedType.STORY || level == WorkItem.ResolvedType.FEATURE
                || level == WorkItem.ResolvedType.BUG;
        boolean taskLevel = level == WorkItem.ResolvedType.TASK || level == WorkItem.ResolvedType.SUB_TASK;
        if (target == FieldMapping.InternalFieldTarget.COMPLEXITY && (rawType == null || storyLevel)) {
            return Optional.of(new FieldMapping(target, null, DEFAULT_COMPLEXITY_FIELD_REFERENCE_NAME, DEFAULT_FIELD_TYPE));
        }
        if (target == FieldMapping.InternalFieldTarget.EFFORT && (rawType == null || taskLevel)) {
            return Optional.of(new FieldMapping(target, null, DEFAULT_EFFORT_FIELD_REFERENCE_NAME, DEFAULT_FIELD_TYPE));
        }
        return Optional.empty();
    }

    /** Same rule as the sync's {@code resolveType}: the type mapping, or TASK when the type is not mapped. */
    private WorkItem.ResolvedType resolvedTypeOf(String rawType) {
        if (typeMappings == null) return WorkItem.ResolvedType.TASK;
        return typeMappings.stream()
                .filter(m -> rawType.equalsIgnoreCase(m.getRawType()))
                .map(TypeMapping::getResolvedType)
                .findFirst()
                .orElse(WorkItem.ResolvedType.TASK);
    }

    // Matches the normalization idiom used elsewhere for Azure raw type/status comparisons (see
    // SyncAzureWorkItemsUseCase.resolveStatus, WorkItemHierarchyClassifier.normalize).
    private String normalize(String rawType) {
        return rawType == null ? null : rawType.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
