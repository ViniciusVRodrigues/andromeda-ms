package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.SyncSettingsOutput;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetSyncSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public SyncSettingsOutput execute(String organizationId) {
        return organizationRepository.findById(organizationId)
                .map(SyncSettingsOutput::from)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }
}
