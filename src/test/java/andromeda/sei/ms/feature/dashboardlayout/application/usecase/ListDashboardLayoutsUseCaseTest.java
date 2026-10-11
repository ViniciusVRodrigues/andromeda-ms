package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListDashboardLayoutsUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    @Mock private DashboardLayoutRepository repository;

    @InjectMocks private ListDashboardLayoutsUseCase useCase;

    @Test
    void should_returnOnlyActiveLayouts_ofAllowedViews() {
        when(repository.findAllActive(ORG, USER)).thenReturn(List.of(
                layout(DashboardViewKey.INDIVIDUAL), layout(DashboardViewKey.EQUIPE), layout(DashboardViewKey.ORG)));

        List<DashboardLayoutOutput> result = useCase.execute(ORG, USER, Set.of("individual", "org"));

        assertThat(result).extracting(DashboardLayoutOutput::viewKey).containsExactly("individual", "org");
    }

    @Test
    void should_returnEmpty_withoutQuerying_when_noViewIsAllowed() {
        assertThat(useCase.execute(ORG, USER, Set.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static DashboardLayout layout(DashboardViewKey viewKey) {
        return new DashboardLayout("id-" + viewKey.value(), ORG, USER, viewKey, List.of(), List.of(), true, 1L,
                Instant.EPOCH, Instant.EPOCH);
    }
}
