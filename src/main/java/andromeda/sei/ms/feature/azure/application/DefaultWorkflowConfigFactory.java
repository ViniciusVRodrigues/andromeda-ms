package andromeda.sei.ms.feature.azure.application;

import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public class DefaultWorkflowConfigFactory {

    private DefaultWorkflowConfigFactory() {}

    private static final List<String> SCRUM_AGILE_TYPES =
            List.of("Product Backlog Item", "Bug", "Task", "Epic", "Feature");

    private static final List<String> AGILE_TYPES =
            List.of("User Story", "Bug", "Task", "Epic", "Feature");

    private static final List<String> CMMI_TYPES =
            List.of("Requirement", "Bug", "Task", "Epic", "Feature");

    public static WorkflowConfig forScrum() {
        return new WorkflowConfig(
                statusMappingsFor(SCRUM_AGILE_TYPES,
                        List.of(
                                statusPair("New", WorkItem.ResolvedStatus.TODO),
                                statusPair("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Closed", WorkItem.ResolvedStatus.DONE),
                                statusPair("Removed", WorkItem.ResolvedStatus.DONE)
                        )),
                List.of(
                        new TypeMapping("Product Backlog Item", WorkItem.ResolvedType.STORY),
                        new TypeMapping("Bug", WorkItem.ResolvedType.BUG),
                        new TypeMapping("Task", WorkItem.ResolvedType.TASK),
                        new TypeMapping("Epic", WorkItem.ResolvedType.EPIC),
                        new TypeMapping("Feature", WorkItem.ResolvedType.EPIC)
                ),
                List.of(),
                null,
                WorkflowConfig.ConfigSource.DEFAULT
        );
    }

    public static WorkflowConfig forAgile() {
        return new WorkflowConfig(
                statusMappingsFor(AGILE_TYPES,
                        List.of(
                                statusPair("New", WorkItem.ResolvedStatus.TODO),
                                statusPair("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Closed", WorkItem.ResolvedStatus.DONE),
                                statusPair("Removed", WorkItem.ResolvedStatus.DONE)
                        )),
                List.of(
                        new TypeMapping("User Story", WorkItem.ResolvedType.STORY),
                        new TypeMapping("Bug", WorkItem.ResolvedType.BUG),
                        new TypeMapping("Task", WorkItem.ResolvedType.TASK),
                        new TypeMapping("Epic", WorkItem.ResolvedType.EPIC),
                        new TypeMapping("Feature", WorkItem.ResolvedType.EPIC)
                ),
                List.of(),
                null,
                WorkflowConfig.ConfigSource.DEFAULT
        );
    }

    public static WorkflowConfig forCmmi() {
        return new WorkflowConfig(
                statusMappingsFor(CMMI_TYPES,
                        List.of(
                                statusPair("Proposed", WorkItem.ResolvedStatus.TODO),
                                statusPair("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                                statusPair("Closed", WorkItem.ResolvedStatus.DONE)
                        )),
                List.of(
                        new TypeMapping("Requirement", WorkItem.ResolvedType.STORY),
                        new TypeMapping("Bug", WorkItem.ResolvedType.BUG),
                        new TypeMapping("Task", WorkItem.ResolvedType.TASK),
                        new TypeMapping("Epic", WorkItem.ResolvedType.EPIC),
                        new TypeMapping("Feature", WorkItem.ResolvedType.EPIC)
                ),
                List.of(),
                null,
                WorkflowConfig.ConfigSource.DEFAULT
        );
    }

    public static WorkflowConfig fallback() {
        return forScrum();
    }

    public static WorkflowConfig forTemplateName(String templateName) {
        if (templateName == null) return fallback();
        return switch (templateName.toLowerCase()) {
            case "scrum" -> forScrum();
            case "agile" -> forAgile();
            case "cmmi" -> forCmmi();
            default -> fallback();
        };
    }

    /**
     * Duplicates the given flat (rawStatus, resolvedStatus) pairs across each of the template's
     * known work item types, producing one {@link StatusMapping} entry per (type, status)
     * combination as required now that status mapping is scoped per work item type.
     */
    private static List<StatusMapping> statusMappingsFor(List<String> types, List<StatusPair> pairs) {
        return types.stream()
                .flatMap(type -> pairs.stream()
                        .map(pair -> new StatusMapping(type, pair.rawStatus(), pair.resolvedStatus())))
                .toList();
    }

    private static StatusPair statusPair(String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {
        return new StatusPair(rawStatus, resolvedStatus);
    }

    private record StatusPair(String rawStatus, WorkItem.ResolvedStatus resolvedStatus) {}
}
