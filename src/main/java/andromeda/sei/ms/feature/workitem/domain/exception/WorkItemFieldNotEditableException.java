package andromeda.sei.ms.feature.workitem.domain.exception;

import andromeda.sei.ms.feature.workitem.domain.WorkItem;
import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/**
 * Raised when complexity/effort is set manually on a work item type that doesn't carry those
 * fields. EPIC and FEATURE are planning containers whose figures are derived from their
 * descendants, so a manual value there would contradict its own children.
 */
public class WorkItemFieldNotEditableException extends BusinessRuleException {

    public WorkItemFieldNotEditableException(String workItemId, WorkItem.ResolvedType resolvedType) {
        super("WORK_ITEM_FIELD_NOT_EDITABLE",
                "Complexidade e esforço não são editáveis em itens do tipo "
                        + (resolvedType == null ? "desconhecido" : resolvedType.name())
                        + " (work item " + workItemId + ") — esses valores são derivados dos itens filhos.");
    }
}
