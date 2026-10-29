package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** A row to move is not part of the source allocation (HTTP 422). */
public class AiUsageNotInAllocationException extends BusinessRuleException {

    public AiUsageNotInAllocationException() {
        super("AI_USAGE_NOT_IN_ALLOCATION", "Há linhas que não pertencem a esta alocação");
    }
}
