package andromeda.sei.ms.feature.azure.application;

import andromeda.sei.ms.feature.azure.domain.WorkflowConfig;
import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

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
                .anyMatch(s -> s.getRawStatus().equals("Active")
                        && s.getResolvedStatus() == WorkItem.ResolvedStatus.IN_PROGRESS);
    }

    @Test
    void forAgile_shouldContainUserStoryType() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forAgile();

        assertThat(config.getTypeMappings())
                .anyMatch(t -> t.getRawType().equals("User Story")
                        && t.getResolvedType() == WorkItem.ResolvedType.STORY);
    }

    @Test
    void forCmmi_shouldContainProposedStatus() {
        WorkflowConfig config = DefaultWorkflowConfigFactory.forCmmi();

        assertThat(config.getStatusMappings())
                .anyMatch(s -> s.getRawStatus().equals("Proposed")
                        && s.getResolvedStatus() == WorkItem.ResolvedStatus.TODO);
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
