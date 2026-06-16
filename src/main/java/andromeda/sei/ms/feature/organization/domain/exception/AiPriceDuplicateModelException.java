package andromeda.sei.ms.feature.organization.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AiPriceDuplicateModelException extends BusinessRuleException {

    public AiPriceDuplicateModelException(String model) {
        super("AI_PRICE_DUPLICATE_MODEL", "Duplicate AI price entry for model: " + model);
    }
}
