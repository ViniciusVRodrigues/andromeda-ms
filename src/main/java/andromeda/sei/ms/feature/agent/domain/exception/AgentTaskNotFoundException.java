package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

/** The task reference (or destination item) does not resolve to an accessible, eligible work item (HTTP 422). */
public class AgentTaskNotFoundException extends BusinessRuleException {

    public AgentTaskNotFoundException() {
        super("AGENT_TASK_NOT_FOUND", "Tarefa não encontrada, sem acesso ou sem alocação de IA");
    }
}
