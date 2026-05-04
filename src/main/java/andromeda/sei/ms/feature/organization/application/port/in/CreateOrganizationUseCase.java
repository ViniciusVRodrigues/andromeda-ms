package andromeda.sei.ms.feature.organization.application.port.in;

import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationRequest;
import andromeda.sei.ms.feature.organization.adapter.in.web.dto.CreateOrganizationResponse;

public interface CreateOrganizationUseCase {
    CreateOrganizationResponse execute(CreateOrganizationRequest request);
}
