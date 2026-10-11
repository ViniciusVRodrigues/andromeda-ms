package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardSectionData;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardWidgetData;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.SaveDashboardLayoutInput;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardWidget;
import andromeda.sei.ms.feature.dashboardlayout.domain.WidgetSize;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutInvalidException;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutVersionConflictException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveDashboardLayoutUseCaseTest {

    private static final String ORG = "org-1";
    private static final String USER = "user-1";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock private DashboardLayoutRepository repository;

    @InjectMocks private SaveDashboardLayoutUseCase useCase;

    @Test
    void should_createLayout_when_versionIsNull_andNoActiveLayoutExists() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 0L));

        DashboardLayoutOutput output = useCase.execute(input("equipe", null));

        ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
        assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER);
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getSections().getFirst().widgets().getFirst().size()).isEqualTo(WidgetSize.G);
        assertThat(output.viewKey()).isEqualTo("equipe");
        assertThat(output.version()).isZero();
        assertThat(output.sections().getFirst().widgets().getFirst()).isEqualTo(new DashboardWidgetData("equipe.burndown", "G"));
        assertThat(output.hidden()).containsExactly("equipe.commits-dia");
        assertThat(output.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void should_conflict_when_versionIsNull_butActiveLayoutExists() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(3L)));

        assertThatThrownBy(() -> useCase.execute(input("equipe", null)))
                .isInstanceOf(DashboardLayoutVersionConflictException.class)
                .extracting("code").isEqualTo("DASHBOARD_LAYOUT_VERSION_CONFLICT");
        verify(repository, never()).save(any());
    }

    @Test
    void should_updateExistingLayout_when_versionMatches() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(3L)));
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 4L));

        DashboardLayoutOutput output = useCase.execute(input("equipe", 3L));

        ArgumentCaptor<DashboardLayout> saved = ArgumentCaptor.forClass(DashboardLayout.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo("layout-1");
        assertThat(saved.getValue().getVersion()).isEqualTo(3L);
        assertThat(saved.getValue().getHidden()).containsExactly("equipe.commits-dia");
        assertThat(output.version()).isEqualTo(4L);
    }

    @Test
    void should_conflict_when_versionDiffersFromStored() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(4L)));

        assertThatThrownBy(() -> useCase.execute(input("equipe", 3L)))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_conflict_when_versionGiven_butNoActiveLayout() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(input("equipe", 3L)))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_rejectUnknownViewKey_beforeTouchingRepository() {
        assertThatThrownBy(() -> useCase.execute(input("qualquer", null)))
                .isInstanceOf(DashboardLayoutViewUnknownException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void should_rejectUnknownSize_beforeTouchingRepository() {
        SaveDashboardLayoutInput input = new SaveDashboardLayoutInput(ORG, USER, "equipe",
                List.of(new DashboardSectionData("fluxo", false, List.of(new DashboardWidgetData("equipe.a", "XL")))),
                List.of(), null);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(DashboardLayoutInvalidException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void should_rejectInvalidContent_onUpdate_withoutSaving() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.of(stored(3L)));
        SaveDashboardLayoutInput input = new SaveDashboardLayoutInput(ORG, USER, "equipe",
                List.of(), List.of("equipe.a", "equipe.a"), 3L);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(DashboardLayoutInvalidException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_rejectInvalidContent_onCreate_beforeLookup() {
        SaveDashboardLayoutInput input = new SaveDashboardLayoutInput(ORG, USER, "equipe", null, List.of(), null);

        assertThatThrownBy(() -> useCase.execute(input)).isInstanceOf(DashboardLayoutInvalidException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void should_treatMissingCollapsed_asExpanded() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> persisted(inv.getArgument(0), 0L));
        SaveDashboardLayoutInput input = new SaveDashboardLayoutInput(ORG, USER, "equipe",
                List.of(new DashboardSectionData("fluxo", null, List.of())), List.of(), null);

        DashboardLayoutOutput output = useCase.execute(input);

        assertThat(output.sections().getFirst().collapsed()).isFalse();
    }

    @Test
    void should_propagateConflict_fromRepositoryRace() {
        when(repository.findActive(ORG, USER, DashboardViewKey.EQUIPE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenThrow(new DashboardLayoutVersionConflictException("equipe"));

        assertThatThrownBy(() -> useCase.execute(input("equipe", null)))
                .isInstanceOf(DashboardLayoutVersionConflictException.class);
    }

    private static SaveDashboardLayoutInput input(String viewKey, Long version) {
        return new SaveDashboardLayoutInput(ORG, USER, viewKey,
                List.of(new DashboardSectionData("fluxo", false, List.of(new DashboardWidgetData("equipe.burndown", "G")))),
                List.of("equipe.commits-dia"), version);
    }

    private static DashboardLayout stored(long version) {
        return new DashboardLayout("layout-1", ORG, USER, DashboardViewKey.EQUIPE,
                List.of(new DashboardSection("fluxo", false, List.of(new DashboardWidget("equipe.velocity", WidgetSize.M)))),
                List.of(), true, version, NOW, NOW);
    }

    private static DashboardLayout persisted(DashboardLayout layout, long version) {
        return new DashboardLayout(layout.getId() == null ? "layout-1" : layout.getId(), layout.getOrganizationId(),
                layout.getUserId(), layout.getViewKey(), layout.getSections(), layout.getHidden(), layout.isActive(),
                version, NOW, NOW);
    }
}
