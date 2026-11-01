package andromeda.sei.ms.feature.allocation.application;

import andromeda.sei.ms.feature.allocation.application.AllocationApprovalPolicy.Decision;

import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/** A mock {@link AllocationApprovalService} for tests that are not about the approval policy. */
public final class AllocationApprovalTestSupport {

    private AllocationApprovalTestSupport() {}

    /** Answers {@link Decision#free()} to every evaluation. */
    public static AllocationApprovalService freeService() {
        AllocationApprovalService service = mock(AllocationApprovalService.class);
        stubFree(service);
        return service;
    }

    public static void stubFree(AllocationApprovalService service) {
        lenient().when(service.evaluate(any(), any(), any(), any(), any(), any(Collection.class)))
                .thenReturn(Decision.free());
    }
}
