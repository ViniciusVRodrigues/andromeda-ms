package andromeda.sei.ms.feature.azure.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AzureSyncPageLimitExceededException extends BusinessRuleException {

    public AzureSyncPageLimitExceededException(int maxPages) {
        super("AZURE_SYNC_PAGE_LIMIT_EXCEEDED",
                "Sync requires more than " + maxPages + " WIQL pages; aborting to avoid unbounded fetch");
    }
}
