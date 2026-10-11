package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutViewUnknownException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteDashboardLayoutUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";

    @Mock private DashboardLayoutRepository repository;

    @InjectMocks private DeleteDashboardLayoutUseCase useCase;

    @Test
    void should_softDeleteActiveLayout() {
        DashboardLayout stored = new DashboardLayout("layout-1", ORG, USER, DashboardViewKey.EQUIPE, List.of(),
                List.of(), true, 2L, Instant.EPOCH, Instant.EPOCH);
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored));

        useCase.execute(ORG, USER, "equipe");

        ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("layout-1");
        assertThat(saved.getValue().isActive()).isFalse();
    }

    @Test
    void should_beIdempotent_when_noActiveLayout() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());

        assertThatCode(() -> useCase.execute(ORG, USER, "equipe")).doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void should_rejectUnknownViewKey() {
        assertThatThrownBy(() -> useCase.execute(ORG, USER, "qualquer"))
                .isInstanceOf(DashboardLayoutViewUnknownException.class);
        verifyNoInteractions(repository);
    }
}
