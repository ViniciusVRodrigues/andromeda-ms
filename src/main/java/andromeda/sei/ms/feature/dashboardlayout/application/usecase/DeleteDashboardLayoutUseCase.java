package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "Restaurar padrão" (design.md D5/D6): soft-deletes the caller's active layout of the view.
 * Idempotent — no active layout is not an error.
 */
@Component
@RequiredArgsConstructor
public class DeleteDashboardLayoutUseCase {

    private final DashboardLayoutRepository repository;

    public void execute(String organizationId, String userId, String viewKey) {
        DashboardViewKey key = DashboardViewKey.parse(viewKey);
        repository.findActive(organizationId, userId, key).ifPresent(this::deactivate);
    }

    private void deactivate(DashboardLayout layout) {
        layout.deactivate();
        repository.save(layout);
    }
}
