package andromeda.sei.ms.feature.agent.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

/** The allocation does not exist, belongs to another person or organization, or is inactive (HTTP 404). */
public class AiAllocationNotFoundException extends ResourceNotFoundException {

    public AiAllocationNotFoundException() {
        super("AI_ALLOCATION_NOT_FOUND", "Alocação não encontrada");
    }
}
