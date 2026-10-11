package andromeda.sei.ms.feature.dashboardlayout.application.usecase;

import andromeda.sei.ms.feature.dashboardlayout.application.DashboardLayoutContentMapper;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.DashboardLayoutOutput;
import andromeda.sei.ms.feature.dashboardlayout.application.dto.SaveDashboardLayoutInput;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayout;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardLayoutRepository;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardSection;
import andromeda.sei.ms.feature.dashboardlayout.domain.DashboardViewKey;
import andromeda.sei.ms.feature.dashboardlayout.domain.exception.DashboardLayoutVersionConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Saves the caller's layout of one view (design.md D6):
 * <ul>
 *     <li>{@code version == null}: create; an active layout already there is a 409 (the client reloads);</li>
 *     <li>{@code version} equal to the stored one: replace the content, the store bumps the version;</li>
 *     <li>any other {@code version} (or none stored): 409 {@code DASHBOARD_LAYOUT_VERSION_CONFLICT}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SaveDashboardLayoutUseCase {

    private final DashboardLayoutRepository repository;

    public DashboardLayoutOutput execute(SaveDashboardLayoutInput input) {
        DashboardViewKey viewKey = DashboardViewKey.parse(input.viewKey());
        List<DashboardSection> sections = DashboardLayoutContentMapper.toSections(input.sections());
        DashboardLayout layout = input.version() == null
                ? create(input, viewKey, sections)
                : update(input, viewKey, sections);
        return DashboardLayoutContentMapper.toOutput(repository.save(layout));
    }

    private DashboardLayout create(SaveDashboardLayoutInput input, DashboardViewKey viewKey,
                                   List<DashboardSection> sections) {
        DashboardLayout layout = DashboardLayout.create(
                input.organizationId(), input.userId(), viewKey, sections, input.hidden());
        if (findActive(input, viewKey).isPresent()) {
            throw new DashboardLayoutVersionConflictException(viewKey.value());
        }
        return layout;
    }

    private DashboardLayout update(SaveDashboardLayoutInput input, DashboardViewKey viewKey,
                                   List<DashboardSection> sections) {
        DashboardLayout stored = findActive(input, viewKey)
                .filter(existing -> input.version().equals(existing.getVersion()))
                .orElseThrow(() -> new DashboardLayoutVersionConflictException(viewKey.value()));
        stored.replaceContent(sections, input.hidden());
        return stored;
    }

    private Optional<DashboardLayout> findActive(SaveDashboardLayoutInput input, DashboardViewKey viewKey) {
        return repository.findActive(input.organizationId(), input.userId(), viewKey);
    }
}
