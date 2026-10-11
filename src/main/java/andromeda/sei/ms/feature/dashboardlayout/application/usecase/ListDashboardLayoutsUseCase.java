package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.application.DashboardLayoutContentMapper;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * The caller's active layouts, restricted to the views the caller may see. The allowed set is
 * resolved by the presentation layer from the caller's permissions (design.md D3) and passed in
 * as a filter, like {@code projectsWith} for project listings.
 */
@Component
@RequiredArgsConstructor
public class ListDashboardLayoutsUseCase {

    private final DashboardLayoutRepository repository;

    public List<DashboardLayoutOutput> execute(String organizationId, String userId, Set<String> allowedViewKeys) {
        if (allowedViewKeys.isEmpty()) return List.of();
        return repository.findAllActive(organizationId, userId).stream()
                .filter(layout -> allowedViewKeys.contains(layout.getViewKey().value()))
                .map(DashboardLayoutContentMapper::toOutput)
                .toList();
    }
}
