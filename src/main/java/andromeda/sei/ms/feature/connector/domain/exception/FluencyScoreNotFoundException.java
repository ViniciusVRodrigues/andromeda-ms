package andromeda.sei.ms.feature.connector.domain.exception;

import andromeda.sei.ms.shared.domain.exception.ResourceNotFoundException;

public class FluencyScoreNotFoundException extends ResourceNotFoundException {

    public FluencyScoreNotFoundException(String userId) {
        super("FLUENCY_SCORE_NOT_FOUND", "Fluency score not found for user: " + userId);
    }
}
