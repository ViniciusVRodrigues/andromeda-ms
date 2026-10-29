package andromeda.sei.ms.feature.agent.application.usage;

import andromeda.sei.ms.feature.allocation.application.usecase.GetSprintItemsUseCase;
import org.springframework.stereotype.Component;

import java.util.List;

/** The in-progress items of the person in the current sprints, as the agent shows them (design 9). */
@Component
public class ListAgentTasksUseCase {

    private final GetSprintItemsUseCase sprintItems;

    public ListAgentTasksUseCase(GetSprintItemsUseCase sprintItems) {
        this.sprintItems = sprintItems;
    }

    public record TaskItem(String workItemId, String ref, String externalId, String title, String type,
                           String sprintName) {
    }

    public List<TaskItem> execute(String organizationId, String userId) {
        return sprintItems.execute(organizationId, userId).stream()
                .map(i -> new TaskItem(i.workItemId(), refOf(i.source(), i.externalId()), i.externalId(),
                        i.title(), i.type(), i.sprintName()))
                .toList();
    }

    /** {@code AB#<externalId>} for Azure DevOps; the external id itself for Jira. */
    static String refOf(String source, String externalId) {
        if (externalId == null) return null;
        return "JIRA".equals(source) ? externalId : "AB#" + externalId;
    }
}
