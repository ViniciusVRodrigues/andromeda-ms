package andromeda.sei.ms.feature.allocation.domain.exception;

import andromeda.sei.ms.shared.domain.exception.BusinessRuleException;

public class AllocationWorkItemTypeNotEligibleException extends BusinessRuleException {

    public AllocationWorkItemTypeNotEligibleException(String rawType) {
        super("ALLOCATION_WORK_ITEM_TYPE_NOT_ELIGIBLE",
                "Work items of type '" + rawType + "' do not receive AI allocation in this project");
    }
}
