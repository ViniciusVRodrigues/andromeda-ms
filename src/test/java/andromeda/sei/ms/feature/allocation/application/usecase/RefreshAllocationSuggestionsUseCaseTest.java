package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.allocation.domain.AiUsageAllocationRepository;
import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshAllocationSuggestionsUseCaseTest {

    static final String ORG = "org-1";
    static final LocalDate D1 = LocalDate.of(2026, 9, 30);
    static final LocalDate D2 = LocalDate.of(2026, 9, 29);

    @Mock AutoAssignAllocationUseCase autoAssign;
    @Mock AiUsageAllocationRepository allocationRepository;
    @InjectMocks RefreshAllocationSuggestionsUseCase useCase;

    @Test
    void should_forceAutoAssign_when_dayIsNotValidated() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, "u1", D1)).thenReturn(false);

        useCase.execute(ORG, Set.of(new ActivityDay("u1", D1)));

        verify(autoAssign).execute(ORG, "u1", D1, true);
    }

    @Test
    void should_doNothing_when_dayIsValidated() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(ORG, "u1", D1)).thenReturn(true);

        useCase.execute(ORG, Set.of(new ActivityDay("u1", D1)));

        verify(autoAssign, never()).execute(anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void should_keepGoing_when_oneDayFails() {
        when(allocationRepository.existsValidatedByOrganizationIdAndUserIdAndDay(any(), any(), any())).thenReturn(false);
        doThrow(new IllegalStateException("boom")).when(autoAssign).execute(ORG, "u1", D1, true);

        useCase.execute(ORG, Set.of(new ActivityDay("u1", D1), new ActivityDay("u1", D2)));

        verify(autoAssign).execute(ORG, "u1", D2, true);
    }

    @Test
    void should_ignoreEmptySet() {
        useCase.execute(ORG, Set.of());

        verify(autoAssign, never()).execute(anyString(), anyString(), any(), anyBoolean());
    }
}
