package andromeda.sei.ms.feature.organization.application.usecase;

import andromeda.sei.ms.feature.organization.application.dto.SyncSettingsOutput;
import andromeda.sei.ms.feature.organization.application.dto.UpdateSyncSettingsInput;
import andromeda.sei.ms.feature.organization.domain.Organization;
import andromeda.sei.ms.feature.organization.domain.OrganizationRepository;
import andromeda.sei.ms.feature.organization.domain.SyncSettings;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationBackfillDaysInvalidException;
import andromeda.sei.ms.feature.organization.domain.exception.OrganizationNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Atualiza a frequencia do sync agendado e a janela de backfill da organizacao. A janela so vale
 * para o primeiro sync de bindings novos (ou reparados); dados ja sincronizados nao mudam.
 */
@Component
@RequiredArgsConstructor
public class UpdateSyncSettingsUseCase {

    private final OrganizationRepository organizationRepository;

    public SyncSettingsOutput execute(String organizationId, UpdateSyncSettingsInput input) {
        Objects.requireNonNull(input.scheduledSync(), "scheduledSync");
        if (!SyncSettings.isValidBackfillDays(input.backfillDays())) {
            throw new OrganizationBackfillDaysInvalidException(input.backfillDays());
        }

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
        organization.setSyncSettings(new SyncSettings(input.scheduledSync(), input.backfillDays()));

        return SyncSettingsOutput.from(organizationRepository.save(organization));
    }
}
