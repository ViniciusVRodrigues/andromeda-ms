package andromeda.sei.ms.feature.dashboard.application.v2;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectPeriodItemsTest {

    @Test
    void should_treatOnlyEpicsAndFeaturesAsContainers_andToleratesAMissingType() {
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.EPIC))).isTrue();
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.FEATURE))).isTrue();
        assertThat(ProjectPeriodItems.isContainer(item(WorkItem.ResolvedType.STORY))).isFalse();
        assertThat(ProjectPeriodItems.isContainer(item(null))).isFalse();
    }

    private static WorkItem item(WorkItem.ResolvedType type) {
        return new WorkItem("wi", "org", "proj", WorkItem.Source.AZURE_DEVOPS,
                "1", Instant.now(), "Title",
                "Raw", type,
                "Active", WorkItem.ResolvedStatus.IN_PROGRESS,
                "Medium", 3.0, List.of(),
                "sprint", "ext-user", "user", null, null,
                Instant.now(), null, null,
                null, null, null, true, 1L, Instant.now(), null, null, null, null, 3.0, null, null, null);
    }
}
