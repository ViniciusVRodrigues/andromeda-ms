package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.application.dto.WorkItemChildrenOutput;

import java.util.List;
import java.util.Map;

public record WorkItemChildrenResponse(
        List<WorkItemSummaryResponse> content,
        boolean truncated
) {
    public static WorkItemChildrenResponse from(WorkItemChildrenOutput output) {
        return from(output, Map.of());
    }

    /**
     * @param assigneeNamesByUserId nomes dos responsáveis já resolvidos, por id de usuário.
     *                              Sem eles a UI cai no e-mail do Azure — o que fazia os filhos
     *                              da árvore exibirem e-mail enquanto as raízes exibiam o nome.
     */
    public static WorkItemChildrenResponse from(WorkItemChildrenOutput output,
                                                 Map<String, String> assigneeNamesByUserId) {
        return new WorkItemChildrenResponse(
                output.children().stream()
                        .map(child -> WorkItemSummaryResponse.from(
                                child,
                                output.metricsFor(child.getId()),
                                child.getAssigneeUserId() == null
                                        ? null
                                        : assigneeNamesByUserId.get(child.getAssigneeUserId()),
                                output.infoFor(child.getId())))
                        .toList(),
                output.truncated());
    }
}
