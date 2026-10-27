package andromeda.sei.ms.feature.allocation.application.usecase;

import andromeda.sei.ms.feature.azure.application.dto.ActivityDay;
import andromeda.sei.ms.feature.workitem.domain.WorkItemActivityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReattributeAzureActivityUseCaseTest {

    @Mock private WorkItemActivityRepository activityRepository;
    @Mock private RefreshAllocationSuggestionsUseCase refreshSuggestions;
    @InjectMocks private ReattributeAzureActivityUseCase useCase;

    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 2);

    @Test
    void should_refreshEachAffectedDay_ofTheUser() {
        when(activityRepository.assignOwnerToUnowned("org", "az-1", "a@x.com", "u1")).thenReturn(List.of(D1, D2));

        useCase.execute("org", "u1", "az-1", "a@x.com");

        verify(refreshSuggestions).execute("org", Set.of(new ActivityDay("u1", D1), new ActivityDay("u1", D2)));
    }

    @Test
    void should_notRefresh_whenNothingWasReattributed() {
        when(activityRepository.assignOwnerToUnowned("org", "az-1", "a@x.com", "u1")).thenReturn(List.of());

        useCase.execute("org", "u1", "az-1", "a@x.com");

        verify(refreshSuggestions, never()).execute(any(), any());
    }

    @Test
    void should_swallowFailures() {
        when(activityRepository.assignOwnerToUnowned("org", "az-1", null, "u1"))
                .thenThrow(new RuntimeException("boom"));

        useCase.execute("org", "u1", "az-1", null);

        verifyNoInteractions(refreshSuggestions);
    }
}
