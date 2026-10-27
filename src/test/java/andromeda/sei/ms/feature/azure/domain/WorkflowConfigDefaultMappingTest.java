package andromeda.sei.ms.feature.azure.domain;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** complexity-effort D5: the mapping an unconfigured binding resolves, by the level of each type. */
class WorkflowConfigDefaultMappingTest {

    private static final FieldMapping.InternalFieldTarget COMPLEXITY = FieldMapping.InternalFieldTarget.COMPLEXITY;
    private static final FieldMapping.InternalFieldTarget EFFORT = FieldMapping.InternalFieldTarget.EFFORT;

    private WorkflowConfig config(WorkflowConfig.ConfigSource source, List<FieldMapping> fields) {
        return new WorkflowConfig(List.of(),
                List.of(new TypeMapping("User Story", WorkItem.ResolvedType.STORY),
                        new TypeMapping("Bug", WorkItem.ResolvedType.BUG),
                        new TypeMapping("Task", WorkItem.ResolvedType.TASK),
                        new TypeMapping("Epic", WorkItem.ResolvedType.EPIC)),
                fields, null, source);
    }

    @Test
    void should_mapStoryPointsToComplexity_onStoryLevelTypes() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(COMPLEXITY, "User Story"))
                .hasValueSatisfying(m -> assertThat(m.getAzureFieldReferenceName())
                        .isEqualTo("Microsoft.VSTS.Scheduling.StoryPoints"));
        assertThat(config.resolveFieldMapping(COMPLEXITY, "bug")).isPresent();
    }

    @Test
    void should_mapCompletedWorkToEffort_onTaskLevelTypes() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(EFFORT, "Task"))
                .hasValueSatisfying(m -> assertThat(m.getAzureFieldReferenceName())
                        .isEqualTo("Microsoft.VSTS.Scheduling.CompletedWork"));
    }

    @Test
    void should_notMapEffortOnStories_norComplexityOnTasks() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(EFFORT, "User Story")).isEmpty();
        assertThat(config.resolveFieldMapping(COMPLEXITY, "Task")).isEmpty();
    }

    @Test
    void should_mapNothing_onEpics() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(EFFORT, "Epic")).isEmpty();
        assertThat(config.resolveFieldMapping(COMPLEXITY, "Epic")).isEmpty();
    }

    @Test
    void should_treatAnUnmappedType_asTask_likeTheSync() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(EFFORT, "Impediment")).isPresent();
        assertThat(config.resolveFieldMapping(COMPLEXITY, "Impediment")).isEmpty();
    }

    @Test
    void should_resolveTheTargetDefault_whenNoTypeIsGiven() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of());

        assertThat(config.resolveFieldMapping(COMPLEXITY).orElseThrow().getAzureFieldReferenceName())
                .isEqualTo("Microsoft.VSTS.Scheduling.StoryPoints");
        assertThat(config.resolveFieldMapping(EFFORT).orElseThrow().getAzureFieldReferenceName())
                .isEqualTo("Microsoft.VSTS.Scheduling.CompletedWork");
    }

    @Test
    void should_prefer_anExplicitMapping_overTheDefault() {
        var config = config(WorkflowConfig.ConfigSource.DEFAULT, List.of(
                new FieldMapping(COMPLEXITY, "User Story", "Custom.Size", "double")));

        assertThat(config.resolveFieldMapping(COMPLEXITY, "User Story").orElseThrow().getAzureFieldReferenceName())
                .isEqualTo("Custom.Size");
    }

    @Test
    void should_neverApplyTheDefault_onCustomConfigs() {
        var config = config(WorkflowConfig.ConfigSource.CUSTOM, List.of());

        assertThat(config.resolveFieldMapping(COMPLEXITY, "User Story")).isEmpty();
        assertThat(config.resolveFieldMapping(EFFORT, "Task")).isEmpty();
        assertThat(config.resolveFieldMapping(EFFORT)).isEmpty();
    }
}
