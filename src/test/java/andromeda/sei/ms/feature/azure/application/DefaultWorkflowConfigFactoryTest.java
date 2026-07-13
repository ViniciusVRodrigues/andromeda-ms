package andromeda.sei.ms.feature.azure.application;

import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultWorkflowConfigFactoryTest {

    @Test
    void forScrum_shouldContainScrumWorkItemTypes() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forScrum();

        assertThat(config.getConfigSource()).isEqualTo(WorkflowConfig.ConfigSource.DEFAULT);
        assertThat(config.getTypeMappings())
                .anyMatch(t -> t.getRawType().equals("Product Backlog Item")
                        && t.getResolvedType() == WorkItem.ResolvedType.STORY);
        assertThat(config.getStatusMappings())
                .anyMatch(s -> "Bug".equals(s.getWorkItemType()) && s.getRawStatus().equals("Active")
                        && s.getResolvedStatus() == WorkItem.ResolvedStatus.IN_PROGRESS);
    }

    @Test
    void forScrum_shouldProduceOneStatusMappingPerTypeStatusCombination() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forScrum();

        List<String> types = List.of("Product Backlog Item", "Bug", "Task", "Epic", "Feature");
        List<String> statuses = List.of("New", "Active", "Resolved", "Closed", "Removed");

        assertThat(config.getStatusMappings()).hasSize(types.size() * statuses.size());
        for (String type : types) {
            for (String status : statuses) {
                assertThat(config.getStatusMappings())
                        .as("expected a StatusMapping for (%s, %s)", type, status)
                        .anyMatch(s -> type.equals(s.getWorkItemType()) && status.equals(s.getRawStatus()));
            }
        }
    }

    @Test
    void forAgile_shouldContainUserStoryType() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forAgile();

        assertThat(config.getTypeMappings())
                .anyMatch(t -> t.getRawType().equals("User Story")
                        && t.getResolvedType() == WorkItem.ResolvedType.STORY);
    }

    @Test
    void forAgile_shouldProduceOneStatusMappingPerTypeStatusCombination() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forAgile();

        List<String> types = List.of("User Story", "Bug", "Task", "Epic", "Feature");
        List<String> statuses = List.of("New", "Active", "Resolved", "Closed", "Removed");

        assertThat(config.getStatusMappings()).hasSize(types.size() * statuses.size());
        for (String type : types) {
            for (String status : statuses) {
                assertThat(config.getStatusMappings())
                        .anyMatch(s -> type.equals(s.getWorkItemType()) && status.equals(s.getRawStatus()));
            }
        }
    }

    @Test
    void forCmmi_shouldContainProposedStatus() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forCmmi();

        assertThat(config.getStatusMappings())
                .anyMatch(s -> "Requirement".equals(s.getWorkItemType()) && s.getRawStatus().equals("Proposed")
                        && s.getResolvedStatus() == WorkItem.ResolvedStatus.TODO);
    }

    @Test
    void forCmmi_shouldProduceOneStatusMappingPerTypeStatusCombination() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forCmmi();

        List<String> types = List.of("Requirement", "Bug", "Task", "Epic", "Feature");
        List<String> statuses = List.of("Proposed", "Active", "Resolved", "Closed");

        assertThat(config.getStatusMappings()).hasSize(types.size() * statuses.size());
        for (String type : types) {
            for (String status : statuses) {
                assertThat(config.getStatusMappings())
                        .anyMatch(s -> type.equals(s.getWorkItemType()) && status.equals(s.getRawStatus()));
            }
        }
    }

    @Test
    void forTemplateName_shouldReturnScrumForScrumTemplate() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forTemplateName("Scrum");

        assertThat(config.getTypeMappings())
                .anyMatch(t -> t.getRawType().equals("Product Backlog Item"));
    }

    @Test
    void forTemplateName_shouldFallbackForUnknownTemplate() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forTemplateName("UnknownProcess");

        assertThat(config).isNotNull();
        assertThat(config.getConfigSource()).isEqualTo(WorkflowConfig.ConfigSource.DEFAULT);
    }

    @Test
    void forTemplateName_shouldFallbackForNullTemplate() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forTemplateName(null);

        assertThat(config).isNotNull();
    }
}
