package andromeda.sei.ms.feature.workitem.presentation.dto;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.feature.workitem.domain.WorkItemHierarchyMetrics;

/**
 * @param hasChildren          whether this item has children that are valid under the project's
 *                             declared hierarchy — lets the UI hide the expand toggle on leaves
 *                             instead of discovering emptiness only after a click.
 * @param rolledUpComplexity   complexity summed over all descendants, {@code null} when no
 *                             descendant carries a value. Complementary to {@code complexity};
 *                             the item's own value always wins in display.
 * @param rolledUpEffort       effort summed over all descendants, same semantics.
 * @param descendantCount      how many descendants contributed to the rollup, so the UI can label
 *                             it honestly ("soma de N itens") rather than implying an own value.
 * @param sprintName           nome da sprint, desnormalizado no sync — evita um join por página
 *                             só para rotular o item. {@code null} enquanto o item não pertencer a
 *                             uma sprint (ou até o backfill/sync alcançá-lo).
 * @param assigneeUserId       id do usuário Andrômeda responsável, quando o assignee do Azure foi
 *                             correlacionado a um membro da organização; {@code null} caso
 *                             contrário (assignee externo ou ainda não vinculado).
 * @param assigneeName         nome de exibição do responsável, resolvido na leitura a partir de
 *                             {@code assigneeUserId}. Não é desnormalizado de propósito: usuários
 *                             de uma organização são poucos e cabem num único lookup, e o nome
 *                             muda com mais frequência do que o de uma sprint.
 * @param assigneeEmail        e-mail do assignee vindo do Azure. Serve de fallback de exibição
 *                             quando o assignee não corresponde a nenhum membro da organização.
 */
public record WorkItemSummaryResponse(
        String id,
        String externalId,
        String title,
        String rawType,
        WorkItem.ResolvedType resolvedType,
        String rawStatus,
        WorkItem.ResolvedStatus resolvedStatus,
        String sprintId,
        String sprintName,
        String assigneeUserId,
        String assigneeName,
        String assigneeEmail,
        String parentExternalId,
        Double complexity,
        String complexitySource,
        Double effort,
        String effortSource,
        boolean hasChildren,
        Double rolledUpComplexity,
        Double rolledUpEffort,
        int descendantCount
) {
    public static WorkItemSummaryResponse from(WorkItem item) {
        return from(item, WorkItemHierarchyMetrics.EMPTY, null);
    }

    public static WorkItemSummaryResponse from(WorkItem item, WorkItemHierarchyMetrics metrics) {
        return from(item, metrics, null);
    }

    /**
     * @param assigneeName nome já resolvido do responsável, ou {@code null} quando não há
     *                     responsável correlacionado (ou quando o chamador não resolve nomes).
     */
    public static WorkItemSummaryResponse from(WorkItem item, WorkItemHierarchyMetrics metrics,
                                                String assigneeName) {
        WorkItemHierarchyMetrics effective = metrics == null ? WorkItemHierarchyMetrics.EMPTY : metrics;
        return new WorkItemSummaryResponse(
                item.getId(),
                item.getExternalId(),
                item.getTitle(),
                item.getRawType(),
                item.getResolvedType(),
                item.getRawStatus(),
                item.getResolvedStatus(),
                item.getSprintId(),
                item.getSprintName(),
                item.getAssigneeUserId(),
                assigneeName,
                item.getAssigneeEmail(),
                item.getParentExternalId(),
                item.getComplexity(),
                item.getComplexitySource() != null ? item.getComplexitySource().name() : null,
                item.getEffort(),
                item.getEffortSource() != null ? item.getEffortSource().name() : null,
                effective.hasChildren(),
                effective.rolledUpComplexity(),
                effective.rolledUpEffort(),
                effective.descendantCount());
    }
}
