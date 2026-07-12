package andromeda.sei.ms.feature.azure.application;

import andromeda.sei.ms.feature.azure.domain.StatusMapping;
import andromeda.sei.ms.feature.azure.domain.TypeMapping;
import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;

import java.util.List;

public class DefaultWorkflowConfigFactory {

    private DefaultWorkflowConfigFactory() {}

    public static WorkflowConfig forScrum() {
        return new WorkflowConfig(
                List.of(
                        new StatusMapping("New", WorkItem.ResolvedStatus.TODO),
                        new StatusMapping("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Closed", WorkItem.ResolvedStatus.DONE),
                        new StatusMapping("Removed", WorkItem.ResolvedStatus.DONE)
                ),
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
                List.of(
                        new StatusMapping("New", WorkItem.ResolvedStatus.TODO),
                        new StatusMapping("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Closed", WorkItem.ResolvedStatus.DONE),
                        new StatusMapping("Removed", WorkItem.ResolvedStatus.DONE)
                ),
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
                List.of(
                        new StatusMapping("Proposed", WorkItem.ResolvedStatus.TODO),
                        new StatusMapping("Active", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Resolved", WorkItem.ResolvedStatus.IN_PROGRESS),
                        new StatusMapping("Closed", WorkItem.ResolvedStatus.DONE)
                ),
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
}
